package com.comp90018.flashcards.ui.play

import com.comp90018.flashcards.data.local.entity.CardEntity

/**
 * Where a 2-player round is.
 *
 * READY: waiting to start. FRONT: the front is showing and the Guesser is guessing.
 * BACK: the back is showing so the players can check the guess. FINISHED: no cards left.
 */
enum class DuoPhase { READY, FRONT, BACK, FINISHED }

/**
 * The rules of one 2-player round, kept free of timers and UI so they are easy to test.
 *
 * Each card is shown front first. Once the back is revealed, the players mark the guess right or
 * wrong, and the next card comes up. Cards marked wrong are kept to review at the end.
 */
class DuoGame(
    cards: List<CardEntity>,
) {
    private val upcoming = ArrayDeque(cards)
    private val correctCards = mutableListOf<CardEntity>()
    private val missedCards = mutableListOf<CardEntity>()

    val totalCount: Int = cards.size

    var phase: DuoPhase = if (cards.isEmpty()) DuoPhase.FINISHED else DuoPhase.READY
        private set

    var current: CardEntity? = null
        private set

    val correctCount: Int
        get() = correctCards.size

    /** Cards marked wrong, in the order they came up. */
    val missed: List<CardEntity>
        get() = missedCards.toList()

    /** Cards still to be shown, counting the current one. */
    val remainingCount: Int
        get() = upcoming.size + if (current != null) 1 else 0

    fun start() {
        if (phase == DuoPhase.READY) advance()
    }

    /** Shows the back of the current card. Does nothing unless the front is showing. */
    fun reveal() {
        if (phase == DuoPhase.FRONT) phase = DuoPhase.BACK
    }

    /** Marks the current card right or wrong. Does nothing unless the back is showing. */
    fun mark(isCorrect: Boolean) {
        val card = current
        if (phase != DuoPhase.BACK || card == null) return
        if (isCorrect) correctCards.add(card) else missedCards.add(card)
        advance()
    }

    private fun advance() {
        current = upcoming.removeFirstOrNull()
        phase = if (current == null) DuoPhase.FINISHED else DuoPhase.FRONT
    }
}
