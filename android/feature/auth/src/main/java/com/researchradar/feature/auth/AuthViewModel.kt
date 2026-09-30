package com.researchradar.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.researchradar.core.data.session.AuthException
import com.researchradar.core.data.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SignIn, SignUp }

/** Password rules — the same ones the server enforces. */
data class PasswordRules(val longEnough: Boolean, val hasUppercase: Boolean, val hasNumber: Boolean) {
    val allMet: Boolean get() = longEnough && hasUppercase && hasNumber

    companion object {
        fun of(password: String) = PasswordRules(
            longEnough = password.length >= 8,
            hasUppercase = password.any { it.isUpperCase() },
            hasNumber = password.any { it.isDigit() },
        )
    }
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null,
    val isSubmitting: Boolean = false,
    /** Incremented on each failed submit so fields can shake. */
    val shakeKey: Int = 0,
) {
    val passwordRules: PasswordRules get() = PasswordRules.of(password)
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val session: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setMode(mode: AuthMode) = _uiState.update {
        it.copy(mode = mode, nameError = null, emailError = null, passwordError = null, formError = null)
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v, nameError = null, formError = null) }
    fun onEmailChange(v: String) = _uiState.update { it.copy(email = v.trim(), emailError = null, formError = null) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v, passwordError = null, formError = null) }

    fun submit() {
        val s = _uiState.value
        if (s.isSubmitting) return

        val nameError = if (s.mode == AuthMode.SignUp && s.name.isBlank()) "Enter your name." else null
        val emailError = when {
            s.email.isBlank() -> "Enter your email address."
            !EMAIL.matches(s.email) -> "That doesn't look like an email address."
            else -> null
        }
        val passwordError = when {
            s.password.isEmpty() -> "Enter your password."
            s.mode == AuthMode.SignUp && !s.passwordRules.allMet -> "Password doesn't meet the rules below."
            else -> null
        }
        if (nameError != null || emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError, shakeKey = it.shakeKey + 1)
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                if (s.mode == AuthMode.SignUp) {
                    session.signUp(s.name, s.email, s.password)
                } else {
                    session.signIn(s.email, s.password)
                }
                // Success: the app switches to the main screens when the session changes.
                _uiState.update { it.copy(isSubmitting = false, password = "") }
            } catch (e: AuthException) {
                _uiState.update { it.copy(isSubmitting = false, formError = e.message, shakeKey = it.shakeKey + 1) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, formError = "Something went wrong. Please try again.", shakeKey = it.shakeKey + 1)
                }
            }
        }
    }
}
