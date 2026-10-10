package com.comp90018.flashcards.domain.fsrs

import com.comp90018.flashcards.domain.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class FsrsSchedulerTest {
    private val t0 = Instant.parse("2026-01-01T09:00:00Z")
    private val scheduler = FsrsScheduler(FsrsParameters(enableFuzzing = false))

    @Test
    fun `matches the reference implementation`() {
        FsrsReferenceVectors.SEQUENCES.forEach { (name, steps) ->
            var card = FsrsCard.new(t0)
            steps.forEachIndexed { index, expected ->
                card = scheduler.review(card, expected.rating, t0.plusSeconds(expected.reviewedAtSeconds))
                val where = "$name, review ${index + 1}"
                assertEquals(where, expected.state, card.state)
                assertEquals(where, expected.step, card.step)
                assertEquals(where, expected.stability, card.stability!!, TOLERANCE)
                assertEquals(where, expected.difficulty, card.difficulty!!, TOLERANCE)
                assertEquals(where, t0.plusSeconds(expected.dueSeconds), card.due)
            }
        }
    }

    @Test
    fun `retrievability falls along the forgetting curve`() {
        val card = reviewAtDue(FsrsCard.new(t0), Rating.GOOD, Rating.GOOD)
        val lastReview = card.lastReview!!

        // Reference values from py-fsrs for a card with stability 2.3065.
        val expected = mapOf(0L to 1.0, 1L to 0.9468474993825461, 3L to 0.8809479557659419, 100L to 0.5589067446271925)
        expected.forEach { (days, retrievability) ->
            val now = lastReview + Duration.ofDays(days).plusHours(1)
            assertEquals("after $days days", retrievability, scheduler.retrievability(card, now), TOLERANCE)
        }
    }

    @Test
    fun `retrievability is 90 percent when elapsed days equal stability`() {
        val card = FsrsCard(state = CardState.REVIEW, stability = 10.0, difficulty = 5.0, due = t0, lastReview = t0)

        assertEquals(0.9, scheduler.retrievability(card, t0 + Duration.ofDays(10)), TOLERANCE)
    }

    @Test
    fun `new card has no retrievability`() {
        assertEquals(0.0, scheduler.retrievability(FsrsCard.new(t0), t0), 0.0)
    }

    @Test
    fun `counts reviews and lapses`() {
        val card = reviewAtDue(FsrsCard.new(t0), Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.AGAIN, Rating.GOOD)

        assertEquals(5, card.reps)
        assertEquals(1, card.lapses)
    }

    @Test
    fun `forgetting while learning is not a lapse`() {
        val card = reviewAtDue(FsrsCard.new(t0), Rating.AGAIN, Rating.AGAIN)

        assertEquals(CardState.LEARNING, card.state)
        assertEquals(0, card.lapses)
    }

    @Test
    fun `card graduates on first review when there are no learning steps`() {
        val noSteps = FsrsScheduler(FsrsParameters(learningSteps = emptyList(), enableFuzzing = false))

        val card = noSteps.review(FsrsCard.new(t0), Rating.AGAIN, t0)

        assertEquals(CardState.REVIEW, card.state)
        assertNull(card.step)
        assertEquals(t0 + Duration.ofDays(1), card.due)
    }

    @Test
    fun `lapse stays in review when there are no relearning steps`() {
        val noSteps = FsrsScheduler(FsrsParameters(relearningSteps = emptyList(), enableFuzzing = false))
        val inReview = noSteps.review(FsrsCard.new(t0), Rating.EASY, t0)

        val lapsed = noSteps.review(inReview, Rating.AGAIN, inReview.due)

        assertEquals(CardState.REVIEW, lapsed.state)
        assertEquals(1, lapsed.lapses)
        assertTrue(lapsed.due.isAfter(inReview.due))
    }

    @Test
    fun `card past the last learning step graduates`() {
        // Possible if a card was scheduled with more learning steps than the scheduler now has.
        val card = FsrsCard(state = CardState.LEARNING, step = 5, stability = 2.0, difficulty = 5.0, due = t0)

        assertEquals(CardState.REVIEW, scheduler.review(card, Rating.HARD, t0).state)
    }

    @Test
    fun `interval is capped at the maximum interval`() {
        val capped = FsrsScheduler(FsrsParameters(maximumIntervalDays = 5, enableFuzzing = false))

        val card = capped.review(FsrsCard.new(t0), Rating.EASY, t0)

        assertEquals(t0 + Duration.ofDays(5), card.due)
    }

    @Test
    fun `fuzz keeps intervals near the unfuzzed value`() {
        // Unfuzzed, the next interval is 46 days, which gives a fuzz range of 42 to 50 days.
        val card = reviewAtDue(FsrsCard.new(t0), Rating.GOOD, Rating.GOOD, Rating.GOOD)
        val now = card.due

        val intervals =
            (0 until 200)
                .map { seed ->
                    val fuzzed = FsrsScheduler(FsrsParameters(), Random(seed)).review(card, Rating.GOOD, now)
                    Duration.between(now, fuzzed.due).toDays()
                }.toSet()

        assertEquals(46L, Duration.between(now, scheduler.review(card, Rating.GOOD, now).due).toDays())
        assertEquals((42L..50L).toSet(), intervals)
    }

    @Test
    fun `fuzz does not change short intervals`() {
        val learning = scheduler.review(FsrsCard.new(t0), Rating.GOOD, t0)

        // Graduating from the last learning step gives a 2-day interval, below the fuzz threshold.
        (0 until 20).forEach { seed ->
            val graduated = FsrsScheduler(FsrsParameters(), Random(seed)).review(learning, Rating.GOOD, learning.due)
            assertEquals(learning.due + Duration.ofDays(2), graduated.due)
        }
    }

    @Test
    fun `rejects the wrong number of weights`() {
        assertThrows(IllegalArgumentException::class.java) { FsrsParameters(weights = List(19) { 1.0 }) }
    }

    @Test
    fun `rejects desired retention outside 0 to 1`() {
        assertThrows(IllegalArgumentException::class.java) { FsrsParameters(desiredRetention = 1.0) }
    }

    private fun reviewAtDue(
        card: FsrsCard,
        vararg ratings: Rating,
    ): FsrsCard = ratings.fold(card) { current, rating -> scheduler.review(current, rating, current.due) }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
