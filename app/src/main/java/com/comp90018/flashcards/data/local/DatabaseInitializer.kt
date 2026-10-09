package com.comp90018.flashcards.data.local

import com.comp90018.flashcards.data.local.dao.CardDao
import com.comp90018.flashcards.data.local.dao.DeckDao
import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.data.local.entity.DeckEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts a sample deck the first time a signed-in user has no decks.
 */
@Singleton
class DatabaseInitializer
    @Inject
    constructor(
        private val cardDao: CardDao,
        private val deckDao: DeckDao,
    ) {
        private val seedMutex = Mutex()

        suspend fun seedForUser(ownerId: String) {
            seedMutex.withLock {
                if (deckDao.countDecksForOwner(ownerId) > 0) {
                    return
                }
                val deckId = UUID.randomUUID().toString()
                deckDao.insertDeck(DeckEntity(deckId = deckId, name = "Sample Deck", ownerId = ownerId))
                val cards =
                    listOf(
                        CardEntity(
                            cardId = UUID.randomUUID().toString(),
                            deckId = deckId,
                            front = "What is Android?",
                            back = "A mobile operating system.",
                        ),
                        CardEntity(
                            cardId = UUID.randomUUID().toString(),
                            deckId = deckId,
                            front = "What is Kotlin?",
                            back = "A modern programming language.",
                        ),
                    )
                cards.forEach { card -> cardDao.insertCard(card) }
            }
        }
    }
