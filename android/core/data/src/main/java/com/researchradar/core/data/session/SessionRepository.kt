package com.researchradar.core.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.model.AuthResponse
import com.researchradar.core.model.DeleteAccountRequest
import com.researchradar.core.model.SignInRequest
import com.researchradar.core.model.SignUpRequest
import com.researchradar.core.network.AuthTokenStore
import com.researchradar.core.network.RadarApi
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in account. */
data class Session(val token: String, val userId: String, val name: String, val email: String)

/** Loading = not read from disk yet (show nothing); SignedOut / SignedIn otherwise. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}

class AuthException(message: String) : Exception(message)

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: Lazy<RadarApi>,
    private val maps: Lazy<MapRepository>,
    private val json: Json,
) : AuthTokenStore {

    private object Keys {
        val token = stringPreferencesKey("token")
        val userId = stringPreferencesKey("user_id")
        val name = stringPreferencesKey("name")
        val email = stringPreferencesKey("email")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile private var cachedToken: String? = null

    init {
        scope.launch {
            val p = context.sessionStore.data.first()
            val token = p[Keys.token]
            _state.value = if (token.isNullOrBlank()) {
                SessionState.SignedOut
            } else {
                cachedToken = token
                SessionState.SignedIn(
                    Session(token, p[Keys.userId].orEmpty(), p[Keys.name].orEmpty(), p[Keys.email].orEmpty()),
                )
            }
        }
    }

    override fun currentToken(): String? = cachedToken

    override fun onUnauthorized() {
        scope.launch { signOut() }
    }

    suspend fun signUp(name: String, email: String, password: String): Session =
        store(call { api.get().signUp(SignUpRequest(name.trim(), email.trim(), password)) })

    suspend fun signIn(email: String, password: String): Session =
        store(call { api.get().signIn(SignInRequest(email.trim(), password)) })

    /**
     * Permanently deletes the account on the server (library, usage counts and maps
     * built from uploaded PDFs), then signs out and clears this device.
     */
    suspend fun deleteAccount(password: String) {
        val response = call { api.get().deleteAccount(DeleteAccountRequest(password)) }
        if (!response.isSuccessful) {
            val detail = runCatching {
                json.parseToJsonElement(response.errorBody()?.string().orEmpty()).jsonObject["detail"]?.jsonPrimitive?.content
            }.getOrNull()
            throw AuthException(detail ?: if (response.code() == 403) "Incorrect password." else "The server returned an error (${response.code()}).")
        }
        signOut()
    }

    /** Ends the session and removes this account's data from the device. */
    suspend fun signOut() {
        cachedToken = null
        context.sessionStore.edit { it.clear() }
        runCatching {
            maps.get().clearAllCache()
            maps.get().clearRecentTopics()
        }
        _state.value = SessionState.SignedOut
    }

    private suspend fun store(response: AuthResponse): Session {
        val session = Session(response.token, response.user.id, response.user.name, response.user.email)
        context.sessionStore.edit {
            it[Keys.token] = session.token
            it[Keys.userId] = session.userId
            it[Keys.name] = session.name
            it[Keys.email] = session.email
        }
        cachedToken = session.token
        _state.value = SessionState.SignedIn(session)
        // Bring down the library saved on the website or another device.
        scope.launch { runCatching { maps.get().syncLibrary() } }
        return session
    }

    /** Runs an auth call, turning HTTP and network failures into a readable message. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val detail = runCatching {
            val body = e.response()?.errorBody()?.string().orEmpty()
            json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull()
        throw AuthException(
            detail ?: when (e.code()) {
                401 -> "Incorrect email or password."
                409 -> "An account with this email already exists."
                else -> "The server returned an error (${e.code()}). Please try again."
            },
        )
    } catch (e: IOException) {
        throw AuthException("Can't reach the Ereuna server. Check your connection and try again.")
    }
}
