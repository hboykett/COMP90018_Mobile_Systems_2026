package com.comp90018.flashcards.data.auth

data class SignedInUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
)

sealed interface AuthState {
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(
        val user: SignedInUser,
    ) : AuthState
}
