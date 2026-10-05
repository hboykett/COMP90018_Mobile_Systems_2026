package com.comp90018.flashcards.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a flashcard in the database.
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val cardId: String,
    val deckId: String,
    val front: String,
    val back: String,
    val frontImageUri: String? = null,
    val backImageUri: String? = null,
    val position: Int = 0,
)
