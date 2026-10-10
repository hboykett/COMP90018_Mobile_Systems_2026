package com.comp90018.flashcards.domain.fsrs

import java.time.Instant

/**
 * One user's FSRS memory state for one card.
 *
 * [stability] is the number of days until recall probability drops to 90%.
 * [difficulty] is in the range 1-10. Both are null until the first review.
 * [step] is the index into the learning or relearning steps, and is null outside those states.
 */
data class FsrsCard(
    val state: CardState = CardState.NEW,
    val step: Int? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val due: Instant,
    val lastReview: Instant? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
) {
    fun isDue(now: Instant): Boolean = !due.isAfter(now)

    companion object {
        fun new(now: Instant): FsrsCard = FsrsCard(due = now)
    }
}
