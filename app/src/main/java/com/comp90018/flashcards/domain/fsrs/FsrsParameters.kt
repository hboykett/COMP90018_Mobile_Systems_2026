package com.comp90018.flashcards.domain.fsrs

import java.time.Duration

/**
 * Model weights and scheduling options for FSRS-6.
 *
 * The default [weights] are the published FSRS-6 defaults, trained on a large set of Anki reviews.
 * They can later be replaced with weights optimised from a user's own review logs.
 */
data class FsrsParameters(
    val weights: List<Double> = DEFAULT_WEIGHTS,
    val desiredRetention: Double = 0.9,
    val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    val maximumIntervalDays: Int = 36_500,
    val enableFuzzing: Boolean = true,
) {
    init {
        require(weights.size == WEIGHT_COUNT) { "FSRS-6 needs $WEIGHT_COUNT weights, got ${weights.size}" }
        require(desiredRetention > 0.0 && desiredRetention < 1.0) {
            "desiredRetention must be between 0 and 1, got $desiredRetention"
        }
        require(maximumIntervalDays >= 1) { "maximumIntervalDays must be at least 1" }
        require((learningSteps + relearningSteps).none { it.isNegative || it.isZero }) {
            "learning and relearning steps must be positive"
        }
    }

    companion object {
        const val WEIGHT_COUNT = 21

        val DEFAULT_WEIGHTS: List<Double> =
            listOf(
                0.212,
                1.2931,
                2.3065,
                8.2956,
                6.4133,
                0.8334,
                3.0194,
                0.001,
                1.8722,
                0.1666,
                0.796,
                1.4835,
                0.0614,
                0.2629,
                1.6483,
                0.6014,
                1.8729,
                0.5425,
                0.0912,
                0.0658,
                0.1542,
            )
    }
}
