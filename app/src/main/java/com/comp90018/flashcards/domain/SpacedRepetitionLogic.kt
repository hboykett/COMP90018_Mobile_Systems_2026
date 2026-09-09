package com.comp90018.flashcards.domain

import com.comp90018.flashcards.domain.model.Rating
import java.time.LocalDate

/**
 * SM-2 Spaced Repetition Algorithm Implementation.
 */
object SpacedRepetitionLogic {
    private const val MIN_EFACTOR = 1.3f
    private const val INITIAL_EFACTOR = 2.5f

    data class State(
        val easinessFactor: Float,
        val repetitionCount: Int,
        val intervalDays: Int,
        val nextReviewDate: LocalDate
    )

    fun calculateNextState(
        currentEF: Float,
        currentRepetition: Int,
        currentInterval: Int,
        rating: Rating,
        currentDate: LocalDate = LocalDate.now()
    ): State {
        // Map Rating to Quality (0-5)
        val q = when (rating) {
            Rating.AGAIN -> 0
            Rating.HARD -> 3
            Rating.GOOD -> 4
            Rating.EASY -> 5
        }

        // Calculate next E-Factor
        var nextEF = currentEF + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
        if (nextEF < MIN_EFACTOR) nextEF = MIN_EFACTOR

        val nextRepetition: Int
        val nextInterval: Int

        if (q < 3) {
            // If quality is low, reset repetitions
            nextRepetition = 0
            nextInterval = 1
        } else {
            nextRepetition = currentRepetition + 1
            nextInterval = when (nextRepetition) {
                1 -> 1
                2 -> 6
                else -> Math.round(currentInterval * nextEF)
            }
        }

        return State(
            easinessFactor = nextEF,
            repetitionCount = nextRepetition,
            intervalDays = nextInterval,
            nextReviewDate = currentDate.plusDays(nextInterval.toLong())
        )
    }
}
