package com.comp90018.flashcards.ui.study

import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.domain.fsrs.CardState
import com.comp90018.flashcards.domain.fsrs.FsrsCard
import com.comp90018.flashcards.domain.model.CardWithState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class StudySessionTest {
    private val t0 = Instant.parse("2026-01-01T09:00:00Z")

    @Test
    fun `shows cards in order and completes`() {
        val session = StudySession(listOf(newCard("a"), newCard("b")))

        assertEquals("a", session.currentId())
        session.answer(graduated("a"), t0)
        assertEquals("b", session.currentId())
        session.answer(graduated("b"), t0)

        assertTrue(session.isComplete)
        assertNull(session.current)
        assertEquals(2, session.reviewedCount)
        assertEquals(0, session.remainingCount)
    }

    @Test
    fun `card in a learning step comes back once its step is due`() {
        val session = StudySession(listOf(newCard("a"), newCard("b"), newCard("c")))

        session.answer(inStep("a", due = t0 + Duration.ofMinutes(1)), t0)
        assertEquals("b", session.currentId())
        assertEquals(3, session.remainingCount)

        // Two minutes later, a's step is due, so it comes before c.
        session.answer(graduated("b"), t0 + Duration.ofMinutes(2))
        assertEquals("a", session.currentId())
        session.answer(graduated("a"), t0 + Duration.ofMinutes(2))
        assertEquals("c", session.currentId())
    }

    @Test
    fun `step card is shown early when nothing else is left`() {
        val session = StudySession(listOf(newCard("a")))

        session.answer(inStep("a", due = t0 + Duration.ofMinutes(10)), t0)

        assertEquals("a", session.currentId())
    }

    @Test
    fun `step card due beyond the learn ahead limit ends the session`() {
        val session = StudySession(listOf(newCard("a")), learnAhead = Duration.ofMinutes(20))

        session.answer(inStep("a", due = t0 + Duration.ofHours(1)), t0)

        assertTrue(session.isComplete)
    }

    @Test
    fun `step cards come back earliest due first`() {
        val session = StudySession(listOf(newCard("a"), newCard("b")))

        session.answer(inStep("a", due = t0 + Duration.ofMinutes(10)), t0)
        session.answer(inStep("b", due = t0 + Duration.ofMinutes(1)), t0)

        assertEquals("b", session.currentId())
        session.answer(graduated("b"), t0 + Duration.ofMinutes(1))
        assertEquals("a", session.currentId())
    }

    @Test
    fun `relearning cards also come back`() {
        val session = StudySession(listOf(newCard("a")))

        session.answer(inStep("a", due = t0 + Duration.ofMinutes(10), state = CardState.RELEARNING), t0)

        assertEquals("a", session.currentId())
    }

    @Test
    fun `empty session is complete`() {
        assertTrue(StudySession(emptyList()).isComplete)
    }

    private fun StudySession.currentId(): String? = current?.card?.cardId

    private fun card(
        id: String,
        state: FsrsCard,
    ) = CardWithState(CardEntity(cardId = id, deckId = "deck", front = id, back = id), state)

    private fun newCard(id: String) = card(id, FsrsCard.new(t0))

    private fun graduated(id: String) =
        card(id, FsrsCard(state = CardState.REVIEW, stability = 3.0, difficulty = 5.0, due = t0 + Duration.ofDays(3)))

    private fun inStep(
        id: String,
        due: Instant,
        state: CardState = CardState.LEARNING,
    ) = card(id, FsrsCard(state = state, step = 1, stability = 2.0, difficulty = 5.0, due = due))
}
