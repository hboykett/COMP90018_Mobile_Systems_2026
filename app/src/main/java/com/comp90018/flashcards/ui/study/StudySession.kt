package com.comp90018.flashcards.ui.study

import com.comp90018.flashcards.domain.fsrs.CardState
import com.comp90018.flashcards.domain.model.CardWithState
import java.time.Duration
import java.time.Instant

/**
 * The queue of cards for one study session, kept in memory.
 *
 * A card rated back into a learning or relearning step returns to the queue and is shown again
 * once that step is due. When nothing else is left, a step due within [learnAhead] is shown
 * early instead of ending the session, as Anki does.
 */
class StudySession(
    cards: List<CardWithState>,
    private val learnAhead: Duration = Duration.ofMinutes(20),
) {
    private val upcoming = ArrayDeque(cards)

    // Cards waiting on a learning or relearning step, earliest due first.
    private val inSteps = mutableListOf<CardWithState>()

    var current: CardWithState? = upcoming.removeFirstOrNull()
        private set

    var reviewedCount: Int = 0
        private set

    /** Cards still to be shown, counting the current one. */
    val remainingCount: Int
        get() = upcoming.size + inSteps.size + if (current != null) 1 else 0

    val isComplete: Boolean
        get() = current == null

    /** Records the current card's rating, given its rescheduled state, and moves to the next card. */
    fun answer(
        updated: CardWithState,
        now: Instant,
    ) {
        reviewedCount++
        if (updated.state.state == CardState.LEARNING || updated.state.state == CardState.RELEARNING) {
            val index = inSteps.indexOfFirst { it.state.due.isAfter(updated.state.due) }
            inSteps.add(if (index == -1) inSteps.size else index, updated)
        }
        current = next(now)
    }

    private fun next(now: Instant): CardWithState? {
        val firstStep = inSteps.firstOrNull()
        return when {
            firstStep != null && firstStep.state.isDue(now) -> inSteps.removeAt(0)
            upcoming.isNotEmpty() -> upcoming.removeFirst()
            firstStep != null && firstStep.state.isDue(now + learnAhead) -> inSteps.removeAt(0)
            else -> null
        }
    }
}
