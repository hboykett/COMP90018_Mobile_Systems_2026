package com.comp90018.flashcards.ui.play

import com.comp90018.flashcards.data.local.entity.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuoGameTest {
    private fun card(id: String) = CardEntity(cardId = id, deckId = "deck", front = "front $id", back = "back $id")

    @Test
    fun `round waits to start and then shows the first front`() {
        val game = DuoGame(listOf(card("a"), card("b")))

        assertEquals(DuoPhase.READY, game.phase)
        assertNull(game.current)

        game.start()

        assertEquals(DuoPhase.FRONT, game.phase)
        assertEquals("a", game.current?.cardId)
    }

    @Test
    fun `reveal shows the back and only works from the front`() {
        val game = DuoGame(listOf(card("a")))

        game.reveal()
        assertEquals(DuoPhase.READY, game.phase)

        game.start()
        game.reveal()
        assertEquals(DuoPhase.BACK, game.phase)
    }

    @Test
    fun `marking before the back is shown is ignored`() {
        val game = DuoGame(listOf(card("a"), card("b")))
        game.start()

        game.mark(isCorrect = true)

        assertEquals(DuoPhase.FRONT, game.phase)
        assertEquals("a", game.current?.cardId)
        assertEquals(0, game.correctCount)
    }

    @Test
    fun `marking a card moves to the next front`() {
        val game = DuoGame(listOf(card("a"), card("b")))
        game.start()
        game.reveal()

        game.mark(isCorrect = true)

        assertEquals(DuoPhase.FRONT, game.phase)
        assertEquals("b", game.current?.cardId)
        assertEquals(1, game.correctCount)
    }

    @Test
    fun `wrong cards are kept in order and the round finishes after the last card`() {
        val game = DuoGame(listOf(card("a"), card("b"), card("c")))
        game.start()

        game.reveal()
        game.mark(isCorrect = false)
        game.reveal()
        game.mark(isCorrect = true)
        game.reveal()
        game.mark(isCorrect = false)

        assertEquals(DuoPhase.FINISHED, game.phase)
        assertNull(game.current)
        assertEquals(1, game.correctCount)
        assertEquals(listOf("a", "c"), game.missed.map { it.cardId })
        assertEquals(3, game.totalCount)
    }

    @Test
    fun `remaining count includes the current card`() {
        val game = DuoGame(listOf(card("a"), card("b"), card("c")))
        game.start()
        assertEquals(3, game.remainingCount)

        game.reveal()
        game.mark(isCorrect = true)

        assertEquals(2, game.remainingCount)
    }

    @Test
    fun `an empty deck is finished straight away`() {
        val game = DuoGame(emptyList())

        assertEquals(DuoPhase.FINISHED, game.phase)
        assertEquals(0, game.totalCount)
    }
}
