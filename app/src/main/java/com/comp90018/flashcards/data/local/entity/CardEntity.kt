package com.comp90018.flashcards.data.local.entity

/**
 * Entity representing a flashcard in the database.
 */
data class CardEntity(
    val cardId: String,
    val deckId: String,
    val front: String,
    val back: String,
    val frontImageUri: String? = null,
    val backImageUri: String? = null,
    val position: Int = 0
)
