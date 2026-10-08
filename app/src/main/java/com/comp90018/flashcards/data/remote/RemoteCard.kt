package com.comp90018.flashcards.data.remote

/**
 * Cloud card document at `decks/{deckId}/cards/{cardId}`.
 */
data class RemoteCard(
    val cardId: String,
    val deckId: String,
    val front: String,
    val back: String,
    val position: Int,
    val frontImageUri: String? = null,
    val backImageUri: String? = null,
)
