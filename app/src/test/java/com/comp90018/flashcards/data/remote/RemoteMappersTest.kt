package com.comp90018.flashcards.data.remote

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RemoteMappersTest {
    @Test
    fun `maps deck document fields`() {
        val updatedAt = Instant.parse("2026-10-08T02:00:00Z")
        val deck =
            RemoteDeckMapper.from(
                deckId = "deck-1",
                data =
                    mapOf(
                        "name" to "Biology",
                        "ownerId" to "uid-1",
                        "visibility" to "public",
                        "updatedAt" to Timestamp(updatedAt.epochSecond, updatedAt.nano),
                        "cardCount" to 3L,
                    ),
            )

        assertEquals("deck-1", deck.deckId)
        assertEquals("Biology", deck.name)
        assertEquals("uid-1", deck.ownerId)
        assertEquals(DeckVisibility.PUBLIC, deck.visibility)
        assertEquals(updatedAt, deck.updatedAt)
        assertEquals(3, deck.cardCount)
    }

    @Test
    fun `unknown visibility defaults to private`() {
        val deck = RemoteDeckMapper.from("d", mapOf("visibility" to "friends-only"))
        assertEquals(DeckVisibility.PRIVATE, deck.visibility)
    }

    @Test
    fun `maps card document fields`() {
        val card =
            RemoteCardMapper.from(
                deckId = "deck-1",
                cardId = "card-1",
                data =
                    mapOf(
                        "front" to "Q",
                        "back" to "A",
                        "position" to 2,
                        "frontImageUri" to "content://front",
                    ),
            )

        assertEquals("card-1", card.cardId)
        assertEquals("deck-1", card.deckId)
        assertEquals("Q", card.front)
        assertEquals("A", card.back)
        assertEquals(2, card.position)
        assertEquals("content://front", card.frontImageUri)
        assertNull(card.backImageUri)
    }
}
