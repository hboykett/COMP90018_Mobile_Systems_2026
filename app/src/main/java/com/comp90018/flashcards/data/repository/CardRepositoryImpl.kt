package com.comp90018.flashcards.data.repository

import com.comp90018.flashcards.data.local.entity.SpacedRepetitionState
import com.comp90018.flashcards.domain.SpacedRepetitionLogic
import com.comp90018.flashcards.domain.model.CardWithState
import com.comp90018.flashcards.domain.model.Rating
import com.comp90018.flashcards.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * Implementation of CardRepository.
 * Note: DAO interactions are placeholder until Room is integrated.
 */
class CardRepositoryImpl : CardRepository {

    override fun getDueCards(deckId: String): Flow<List<CardWithState>> {
        // Placeholder implementation
        // In reality, this would query the DB for cards where deckId matches 
        // and nextReviewDate <= current date.
        return flowOf(emptyList())
    }

    override suspend fun updateCardState(cardId: String, rating: Rating) {
        // 1. Fetch current repetition state for the card (mocked for now)
        val currentState = fetchCurrentState(cardId)

        // 2. Calculate next state using the SM-2 logic
        val nextLogicState = SpacedRepetitionLogic.calculateNextState(
            currentEF = currentState.easinessFactor,
            currentRepetition = currentState.repetitionCount,
            currentInterval = currentState.intervalDays,
            rating = rating,
            currentDate = LocalDate.now()
        )

        // 3. Create updated state entity
        val updatedState = currentState.copy(
            easinessFactor = nextLogicState.easinessFactor,
            repetitionCount = nextLogicState.repetitionCount,
            intervalDays = nextLogicState.intervalDays,
            nextReviewDate = nextLogicState.nextReviewDate
        )

        // 4. Persist updated state to DB
        saveState(updatedState)
    }

    private suspend fun fetchCurrentState(cardId: String): SpacedRepetitionState {
        // Mock fetch: return default state if not found
        return SpacedRepetitionState(cardId = cardId)
    }

    private suspend fun saveState(state: SpacedRepetitionState) {
        // Mock save logic
    }
}
