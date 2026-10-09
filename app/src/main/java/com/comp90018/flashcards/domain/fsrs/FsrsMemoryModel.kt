package com.comp90018.flashcards.domain.fsrs

import com.comp90018.flashcards.domain.model.Rating
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * The FSRS-6 memory formulas: how stability and difficulty change after a review,
 * and how recall probability decays over time.
 *
 * `w` is the weight list from [FsrsParameters], indexed as in the FSRS-6 paper and py-fsrs.
 */
internal class FsrsMemoryModel(
    private val parameters: FsrsParameters,
) {
    private val w = parameters.weights
    private val decay = -w[20]

    // Chosen so that recall probability is exactly 90% when elapsed days equal stability.
    private val factor = 0.9.pow(1 / decay) - 1

    /** Recall probability [elapsedDays] after the last review of a card with [stability]. */
    fun forgettingCurve(
        elapsedDays: Double,
        stability: Double,
    ): Double = (1 + factor * elapsedDays / stability).pow(decay)

    /** Whole days until recall probability falls to the desired retention. */
    fun nextIntervalDays(stability: Double): Int {
        val days = stability / factor * (parameters.desiredRetention.pow(1 / decay) - 1)
        // Kotlin's round() rounds halves to even, like Python's.
        return round(days).coerceIn(1.0, parameters.maximumIntervalDays.toDouble()).toInt()
    }

    fun initialStability(rating: Rating): Double = max(w[rating.grade - 1], MIN_STABILITY)

    fun initialDifficulty(rating: Rating): Double =
        rawInitialDifficulty(rating).coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    fun nextDifficulty(
        difficulty: Double,
        rating: Rating,
    ): Double {
        val delta = -w[6] * (rating.grade - 3)
        // Linear damping: changes shrink as difficulty approaches 10.
        val damped = difficulty + (10 - difficulty) * delta / 9
        // Mean reversion towards the difficulty of a card first rated Easy.
        val reverted = w[7] * rawInitialDifficulty(Rating.EASY) + (1 - w[7]) * damped
        return reverted.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    /** Stability after a review less than a day after the previous one. */
    fun shortTermStability(
        stability: Double,
        rating: Rating,
    ): Double {
        var increase = exp(w[17] * (rating.grade - 3 + w[18])) * stability.pow(-w[19])
        if (rating != Rating.AGAIN) increase = max(increase, 1.0)
        return max(stability * increase, MIN_STABILITY)
    }

    /** Stability after a review a day or more after the previous one. */
    fun longTermStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
    ): Double {
        val next =
            if (rating == Rating.AGAIN) {
                forgetStability(difficulty, stability, retrievability)
            } else {
                recallStability(difficulty, stability, retrievability, rating)
            }
        return max(next, MIN_STABILITY)
    }

    private fun rawInitialDifficulty(rating: Rating): Double = w[4] - exp(w[5] * (rating.grade - 1)) + 1

    private fun forgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
    ): Double {
        val longTerm =
            w[11] * difficulty.pow(-w[12]) * ((stability + 1).pow(w[13]) - 1) * exp((1 - retrievability) * w[14])
        val shortTerm = stability / exp(w[17] * w[18])
        return min(longTerm, shortTerm)
    }

    private fun recallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
    ): Double {
        val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
        val growth =
            exp(w[8]) * (11 - difficulty) * stability.pow(-w[9]) * (exp((1 - retrievability) * w[10]) - 1)
        return stability * (1 + growth * hardPenalty * easyBonus)
    }

    private companion object {
        const val MIN_STABILITY = 0.001
        const val MIN_DIFFICULTY = 1.0
        const val MAX_DIFFICULTY = 10.0
    }
}
