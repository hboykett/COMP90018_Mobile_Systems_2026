package com.comp90018.flashcards.domain

import com.comp90018.flashcards.domain.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpacedRepetitionLogicTest {

    private val initialEF = 2.5f
    private val today = LocalDate.of(2026, 9, 9)

    @Test
    fun `initial review with EASY rating calculates correct state`() {
        val state = SpacedRepetitionLogic.calculateNextState(
            currentEF = initialEF,
            currentRepetition = 0,
            currentInterval = 0,
            rating = Rating.EASY,
            currentDate = today
        )

        assertEquals(2.6f, state.easinessFactor, 0.001f)
        assertEquals(1, state.repetitionCount)
        assertEquals(1, state.intervalDays)
        assertEquals(today.plusDays(1), state.nextReviewDate)
    }

    @Test
    fun `initial review with AGAIN rating resets state`() {
        val state = SpacedRepetitionLogic.calculateNextState(
            currentEF = initialEF,
            currentRepetition = 0,
            currentInterval = 0,
            rating = Rating.AGAIN,
            currentDate = today
        )

        assertEquals(1.7f, state.easinessFactor, 0.001f)
        assertEquals(0, state.repetitionCount)
        assertEquals(1, state.intervalDays)
        assertEquals(today.plusDays(1), state.nextReviewDate)
    }

    @Test
    fun `easiness factor increases for EASY rating`() {
        val state = SpacedRepetitionLogic.calculateNextState(
            currentEF = 2.0f,
            currentRepetition = 1,
            currentInterval = 1,
            rating = Rating.EASY,
            currentDate = today
        )

        assertTrue(state.easinessFactor > 2.0f)
    }

    @Test
    fun `easiness factor decreases for AGAIN rating`() {
        val state = SpacedRepetitionLogic.calculateNextState(
            currentEF = 2.0f,
            currentRepetition = 1,
            currentInterval = 1,
            rating = Rating.AGAIN,
            currentDate = today
        )

        assertTrue(state.easinessFactor < 2.0f)
    }

    @Test
    fun `easiness factor decreases for HARD rating`() {
        val state = SpacedRepetitionLogic.calculateNextState(
            currentEF = 2.0f,
            currentRepetition = 1,
            currentInterval = 1,
            rating = Rating.HARD,
            currentDate = today
        )

        assertTrue(state.easinessFactor < 2.0f)
    }

    @Test
    fun `interval increases correctly for repetition 2`() {
        val state = SpacedRepetitionLogic.calculateNextState(
            currentEF = initialEF,
            currentRepetition = 1,
            currentInterval = 1,
            rating = Rating.GOOD,
            currentDate = today
        )

        assertEquals(2, state.repetitionCount)
        assertEquals(6, state.intervalDays)
    }

    @Test
    fun `interval increases correctly for repetition 3 using SM-2 formula`() {
        val ef = 2.0f
        val interval = 6
        val state = SpacedRepetitionLogic.calculateNextState(
            currentEF = ef,
            currentRepetition = 2,
            currentInterval = interval,
            rating = Rating.GOOD,
            currentDate = today
        )

        // nextRepetition = 3
        // nextEF for GOOD (q=4): 2.0 + (0.1 - (5-4) * (0.08 + (5-4) * 0.02)) = 2.0 + (0.1 - 1 * (0.08 + 0.02)) = 2.0 + 0 = 2.0
        // nextInterval = Math.round(6 * 2.0) = 12
        assertEquals(3, state.repetitionCount)
        assertEquals(12, state.intervalDays)
    }

    @Test
    fun `nextReviewDate is calculated correctly based on interval`() {
        val interval = 10
        val state = SpacedRepetitionLogic.calculateNextState(
            currentEF = initialEF,
            currentRepetition = 5,
            currentInterval = interval,
            rating = Rating.GOOD,
            currentDate = today
        )

        assertEquals(today.plusDays(state.intervalDays.toLong()), state.nextReviewDate)
    }

    @Test
    fun `easiness factor does not fall below minimum`() {
        val state = SpacedRepetitionLogic.calculateNextState(
            currentEF = 1.3f,
            currentRepetition = 1,
            currentInterval = 1,
            rating = Rating.AGAIN,
            currentDate = today
        )

        assertEquals(1.3f, state.easinessFactor, 0.001f)
    }
}
