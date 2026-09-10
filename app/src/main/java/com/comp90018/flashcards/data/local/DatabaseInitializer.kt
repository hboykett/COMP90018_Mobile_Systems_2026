package com.comp90018.flashcards.data.local

import com.comp90018.flashcards.data.local.dao.CardDao
import com.comp90018.flashcards.data.local.dao.DeckDao
import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.data.local.entity.DeckEntity
import com.comp90018.flashcards.data.local.entity.SpacedRepetitionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to seed the database with initial data.
 */
@Singleton
class DatabaseInitializer @Inject constructor(
    private val cardDao: CardDao,
    private val deckDao: DeckDao
) {
    fun seedDatabaseIfEmpty() {
        CoroutineScope(Dispatchers.IO).launch {
            if (cardDao.getCardCount() == 0) {
                val deckId = UUID.randomUUID().toString()
                
                val deck = DeckEntity(deckId = deckId, name = "Sample Deck")
                deckDao.insertDeck(deck)
                
                val cards = listOf(
                    CardEntity(
                        cardId = UUID.randomUUID().toString(),
                        deckId = deckId,
                        front = "What is Android?",
                        back = "A mobile operating system."
                    ),
                    CardEntity(
                        cardId = UUID.randomUUID().toString(),
                        deckId = deckId,
                        front = "What is Kotlin?",
                        back = "A modern programming language."
                    )
                )

                cards.forEach { card ->
                    cardDao.insertCard(card)
                    cardDao.insertState(SpacedRepetitionState(cardId = card.cardId))
                }
            }
        }
    }
}
