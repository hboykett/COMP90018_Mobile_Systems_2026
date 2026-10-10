package com.comp90018.flashcards.domain.fsrs

import com.comp90018.flashcards.domain.model.Rating
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.random.Random

/**
 * FSRS-6 (Free Spaced Repetition Scheduler).
 *
 * Each review updates the card's stability and difficulty, then picks the next due time.
 * While learning or relearning, the card moves through short fixed steps. Once in review,
 * the interval is the number of days until recall probability falls to
 * [FsrsParameters.desiredRetention].
 *
 * Matches the reference implementation (py-fsrs 6), with one difference: fuzz is drawn
 * uniformly from the fuzz range, so a fuzzed interval never goes past the top of that range.
 */
class FsrsScheduler(
    private val parameters: FsrsParameters = FsrsParameters(),
    private val random: Random = Random.Default,
) {
    private val model = FsrsMemoryModel(parameters)

    /** Probability that the user can recall [card] at [now]. 0 for a card that was never reviewed. */
    fun retrievability(
        card: FsrsCard,
        now: Instant,
    ): Double {
        val stability = card.stability
        val lastReview = card.lastReview
        if (stability == null || lastReview == null) return 0.0
        val elapsedDays = wholeDaysBetween(lastReview, now).coerceAtLeast(0)
        return model.forgettingCurve(elapsedDays.toDouble(), stability)
    }

    /** Returns [card] after the user reviewed it with [rating] at [now]. */
    fun review(
        card: FsrsCard,
        rating: Rating,
        now: Instant,
    ): FsrsCard {
        val reviewed = updateMemory(card, rating, now).copy(lastReview = now, reps = card.reps + 1)
        return when (card.state) {
            CardState.NEW, CardState.LEARNING ->
                advanceSteps(reviewed, rating, now, parameters.learningSteps, CardState.LEARNING)
            CardState.REVIEW -> afterReview(reviewed, rating, now)
            CardState.RELEARNING ->
                advanceSteps(reviewed, rating, now, parameters.relearningSteps, CardState.RELEARNING)
        }
    }

    private fun updateMemory(
        card: FsrsCard,
        rating: Rating,
        now: Instant,
    ): FsrsCard {
        val stability = card.stability
        val difficulty = card.difficulty
        if (stability == null || difficulty == null) {
            return card.copy(
                stability = model.initialStability(rating),
                difficulty = model.initialDifficulty(rating),
            )
        }
        val elapsedDays = card.lastReview?.let { wholeDaysBetween(it, now) }
        val nextStability =
            if (elapsedDays != null && elapsedDays < 1) {
                model.shortTermStability(stability, rating)
            } else {
                model.longTermStability(difficulty, stability, retrievability(card, now), rating)
            }
        // The stability formulas use the difficulty from before this review.
        return card.copy(stability = nextStability, difficulty = model.nextDifficulty(difficulty, rating))
    }

    private fun advanceSteps(
        card: FsrsCard,
        rating: Rating,
        now: Instant,
        steps: List<Duration>,
        stepState: CardState,
    ): FsrsCard {
        val step = card.step ?: 0
        // A card can be past the last step if the step list was shortened since it was scheduled.
        if (steps.isEmpty() || (step >= steps.size && rating != Rating.AGAIN)) {
            return graduate(card, now)
        }
        return when (rating) {
            Rating.AGAIN -> card.copy(state = stepState, step = 0, due = now + steps[0])
            Rating.HARD -> card.copy(state = stepState, step = step, due = now + hardStepDelay(steps, step))
            Rating.GOOD ->
                if (step + 1 >= steps.size) {
                    graduate(card, now)
                } else {
                    card.copy(state = stepState, step = step + 1, due = now + steps[step + 1])
                }
            Rating.EASY -> graduate(card, now)
        }
    }

    private fun hardStepDelay(
        steps: List<Duration>,
        step: Int,
    ): Duration =
        when {
            step == 0 && steps.size == 1 -> steps[0].multipliedBy(3).dividedBy(2)
            step == 0 -> (steps[0] + steps[1]).dividedBy(2)
            else -> steps[step]
        }

    private fun afterReview(
        card: FsrsCard,
        rating: Rating,
        now: Instant,
    ): FsrsCard {
        if (rating != Rating.AGAIN) return graduate(card, now)
        val lapsed = card.copy(lapses = card.lapses + 1)
        val relearningSteps = parameters.relearningSteps
        return if (relearningSteps.isEmpty()) {
            graduate(lapsed, now)
        } else {
            lapsed.copy(state = CardState.RELEARNING, step = 0, due = now + relearningSteps[0])
        }
    }

    private fun graduate(
        card: FsrsCard,
        now: Instant,
    ): FsrsCard {
        val stability = checkNotNull(card.stability) { "stability is set before scheduling" }
        val interval = model.nextIntervalDays(stability)
        val intervalDays = if (parameters.enableFuzzing) fuzz(interval) else interval
        return card.copy(
            state = CardState.REVIEW,
            step = null,
            due = now + Duration.ofDays(intervalDays.toLong()),
        )
    }

    /** Spreads review intervals of 3+ days by a few days so cards added together don't stay clumped. */
    private fun fuzz(intervalDays: Int): Int {
        if (intervalDays < MIN_FUZZ_INTERVAL) return intervalDays
        val delta =
            1.0 +
                FUZZ_RANGES.sumOf { range ->
                    range.factor * max(min(intervalDays.toDouble(), range.end) - range.start, 0.0)
                }
        val maxDays = min(round(intervalDays + delta).toInt(), parameters.maximumIntervalDays)
        val minDays = min(max(2, round(intervalDays - delta).toInt()), maxDays)
        return random.nextInt(minDays, maxDays + 1)
    }

    private fun wholeDaysBetween(
        from: Instant,
        to: Instant,
    ): Long = Duration.between(from, to).toDays()

    private data class FuzzRange(
        val start: Double,
        val end: Double,
        val factor: Double,
    )

    private companion object {
        const val MIN_FUZZ_INTERVAL = 2.5

        val FUZZ_RANGES =
            listOf(
                FuzzRange(start = 2.5, end = 7.0, factor = 0.15),
                FuzzRange(start = 7.0, end = 20.0, factor = 0.1),
                FuzzRange(start = 20.0, end = Double.POSITIVE_INFINITY, factor = 0.05),
            )
    }
}
