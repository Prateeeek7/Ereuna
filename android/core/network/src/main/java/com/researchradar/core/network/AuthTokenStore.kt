package com.researchradar.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Where the network layer gets the current login token (implemented in core:data). */
interface AuthTokenStore {
    /** Current token, or null when signed out. Must be fast: called for every request. */
    fun currentToken(): String?

    /** The server rejected the token (expired or revoked): end the session. */
    fun onUnauthorized()
}

/** Adds `Authorization: Bearer <token>` and signs the user out if the server returns 401. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokens: AuthTokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.currentToken()
        val request = if (token != null && chain.request().header("Authorization") == null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        val isAuthCall = request.url.encodedPath.contains("/v1/auth/sign")
        if (response.code == 401 && token != null && !isAuthCall) {
            tokens.onUnauthorized()
        }
        return response
    }
}
