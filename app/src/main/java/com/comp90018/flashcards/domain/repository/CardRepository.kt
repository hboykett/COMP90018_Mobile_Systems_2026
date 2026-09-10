package com.comp90018.flashcards.domain.repository

import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.data.local.entity.DeckEntity
import com.comp90018.flashcards.domain.model.CardWithState
import com.comp90018.flashcards.domain.model.Rating
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    // Deck Operations
    fun getAllDecks(): Flow<List<DeckEntity>>
    suspend fun insertDeck(deck: DeckEntity)
    suspend fun updateDeck(deck: DeckEntity)
    suspend fun deleteDeck(deck: DeckEntity)
    suspend fun getDeckById(deckId: String): DeckEntity?

    // Card Operations
    fun getCardsByDeckId(deckId: String): Flow<List<CardEntity>>
    fun getDueCards(deckId: String): Flow<List<CardWithState>>
    suspend fun getCardById(cardId: String): CardEntity?
    suspend fun insertCard(card: CardEntity)
    suspend fun updateCard(card: CardEntity)
    suspend fun deleteCard(card: CardEntity)
    
    /**
     * Updates the spaced repetition state of a card based on the user's rating.
     */
    suspend fun updateCardState(cardId: String, rating: Rating)
}
