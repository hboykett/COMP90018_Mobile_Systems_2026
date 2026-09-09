package com.comp90018.flashcards.domain.repository

import com.comp90018.flashcards.domain.model.CardWithState
import com.comp90018.flashcards.domain.model.Rating
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    /**
     * Retrieves all cards in a deck that are due for review.
     */
    fun getDueCards(deckId: String): Flow<List<CardWithState>>

    /**
     * Updates the spaced repetition state of a card based on the user's rating.
     */
    suspend fun updateCardState(cardId: String, rating: Rating)
}
