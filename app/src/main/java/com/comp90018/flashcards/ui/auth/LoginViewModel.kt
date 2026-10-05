package com.comp90018.flashcards.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comp90018.flashcards.data.auth.AuthRepository
import com.comp90018.flashcards.data.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val signingIn = MutableStateFlow(false)
        val isSigningIn: StateFlow<Boolean> = signingIn.asStateFlow()

        private val error = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = error.asStateFlow()

        private val info = MutableStateFlow<String?>(null)
        val infoMessage: StateFlow<String?> = info.asStateFlow()

        fun signIn(context: Context) {
            launchAuth { authRepository.signInWithGoogle(context) }
        }

        fun register(
            email: String,
            password: String,
            confirmPassword: String,
        ) {
            val validationError = validate(email, password, confirmPassword)
            if (validationError != null) {
                error.value = validationError
                return
            }
            launchAuth { authRepository.registerWithEmail(email, password) }
        }

        fun signInWithEmail(
            email: String,
            password: String,
        ) {
            val validationError = validate(email, password, confirmPassword = null)
            if (validationError != null) {
                error.value = validationError
                return
            }
            launchAuth { authRepository.signInWithEmail(email, password) }
        }

        fun sendPasswordReset(email: String) {
            if (!email.isValidEmail()) {
                error.value = "Enter the email for the account you want to reset."
                return
            }
            launchAuth(
                successMessage = "Password reset email sent.",
            ) { authRepository.sendPasswordReset(email) }
        }

        private fun launchAuth(
            successMessage: String? = null,
            block: suspend () -> Result<Unit>,
        ) {
            if (signingIn.value) {
                return
            }
            viewModelScope.launch {
                signingIn.value = true
                error.value = null
                info.value = null
                block()
                    .onSuccess { info.value = successMessage }
                    .onFailure { failure -> error.value = failure.message ?: "Sign-in failed" }
                signingIn.value = false
            }
        }
    }

private fun validate(
    email: String,
    password: String,
    confirmPassword: String?,
): String? =
    when {
        !email.isValidEmail() -> "Enter a valid email address."
        password.length < MIN_PASSWORD_LENGTH -> "Password must be at least 6 characters."
        confirmPassword != null && password != confirmPassword -> "Passwords do not match."
        else -> null
    }

private fun String.isValidEmail(): Boolean = contains("@") && substringAfter("@").contains(".")

private const val MIN_PASSWORD_LENGTH = 6

@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
    ) : ViewModel() {
        val authState: StateFlow<AuthState> = authRepository.authState
    }
