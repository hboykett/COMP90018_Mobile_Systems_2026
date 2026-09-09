package com.comp90018.flashcards.data.local.entity

import java.time.LocalDate

/**
 * Entity storing the spaced repetition progress for a specific card.
 */
data class SpacedRepetitionState(
    val cardId: String,
    val easinessFactor: Float = 2.5f,
    val repetitionCount: Int = 0,
    val intervalDays: Int = 0,
    val nextReviewDate: LocalDate = LocalDate.now()
)
