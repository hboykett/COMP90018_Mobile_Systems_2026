package com.comp90018.flashcards.data.repository

import com.comp90018.flashcards.data.local.dao.CardDao
import com.comp90018.flashcards.data.local.dao.DeckDao
import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.data.local.entity.DeckEntity
import com.comp90018.flashcards.data.local.entity.SpacedRepetitionState
import com.comp90018.flashcards.domain.SpacedRepetitionLogic
import com.comp90018.flashcards.domain.model.CardWithState
import com.comp90018.flashcards.domain.model.Rating
import com.comp90018.flashcards.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Implementation of CardRepository using Room database.
 */
class CardRepositoryImpl
    @Inject
    constructor(
        private val cardDao: CardDao,
        private val deckDao: DeckDao,
    ) : CardRepository {
        override fun getAllDecks(ownerId: String): Flow<List<DeckEntity>> = deckDao.getAllDecks(ownerId)

        override suspend fun insertDeck(deck: DeckEntity) = deckDao.insertDeck(deck)

        override suspend fun updateDeck(deck: DeckEntity) = deckDao.updateDeck(deck)

        override suspend fun deleteDeck(deck: DeckEntity) = deckDao.deleteDeck(deck)

        override suspend fun getDeckById(deckId: String): DeckEntity? = deckDao.getDeckById(deckId)

        override fun getCardsByDeckId(deckId: String): Flow<List<CardEntity>> = cardDao.getCardsByDeckId(deckId)

        override fun getDueCards(deckId: String): Flow<List<CardWithState>> =
            cardDao.getCardsWithState(deckId).map { map ->
                map
                    .map { (card, state) ->
                        CardWithState(card = card, state = state)
                    }.filter { it.state.nextReviewDate <= LocalDate.now() }
            }

        override suspend fun getCardById(cardId: String): CardEntity? = cardDao.getCardById(cardId)

        override suspend fun insertCard(card: CardEntity) {
            cardDao.insertCard(card)
            cardDao.insertState(SpacedRepetitionState(cardId = card.cardId))
        }

        override suspend fun updateCard(card: CardEntity) = cardDao.updateCard(card)

        override suspend fun deleteCard(card: CardEntity) = cardDao.deleteCardAndState(card)

        override suspend fun updateCardState(
            cardId: String,
            rating: Rating,
        ) {
            // 1. Fetch current repetition state for the card
            val existingState = cardDao.getStateForCard(cardId)
            val currentState = existingState ?: SpacedRepetitionState(cardId = cardId)

            // 2. Calculate next state using the SM-2 logic
            val nextLogicState =
                SpacedRepetitionLogic.calculateNextState(
                    currentEF = currentState.easinessFactor,
                    currentRepetition = currentState.repetitionCount,
                    currentInterval = currentState.intervalDays,
                    rating = rating,
                    currentDate = LocalDate.now(),
                )

            // 3. Create updated state entity
            val updatedState =
                currentState.copy(
                    easinessFactor = nextLogicState.easinessFactor,
                    repetitionCount = nextLogicState.repetitionCount,
                    intervalDays = nextLogicState.intervalDays,
                    nextReviewDate = nextLogicState.nextReviewDate,
                )

            // 4. Persist updated state to DB
            if (existingState == null) {
                cardDao.insertState(updatedState)
            } else {
                cardDao.updateState(updatedState)
            }
        }
    }
