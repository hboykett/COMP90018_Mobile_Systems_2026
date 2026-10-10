package com.comp90018.flashcards.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comp90018.flashcards.domain.model.CardWithState
import com.comp90018.flashcards.domain.model.Rating
import com.comp90018.flashcards.domain.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for the Study Screen.
 */
data class StudyUiState(
    val cards: List<CardWithState> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isSessionComplete: Boolean = false,
)

/**
 * ViewModel for managing a flashcard study session.
 */
@HiltViewModel
class StudyViewModel
    @Inject
    constructor(
        private val repository: CardRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        // The deckId is passed via navigation arguments
        private val deckId: String = checkNotNull(savedStateHandle["deckId"])

        private val _uiState = MutableStateFlow(StudyUiState())
        val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

        init {
            loadDueCards()
        }

        /**
         * Loads cards that are due for review in the current deck.
         */
        private fun loadDueCards() {
            viewModelScope.launch {
                // We take the first emission to have a stable list for the session.
                val dueCards = repository.getDueCards(deckId).first()
                _uiState.update {
                    it.copy(
                        cards = dueCards,
                        isSessionComplete = dueCards.isEmpty(),
                    )
                }
            }
        }

        /**
         * Toggles the flip state of the current card.
         */
        fun flipCard() {
            _uiState.update { it.copy(isFlipped = !it.isFlipped) }
        }

        /**
         * Rates the current card and moves to the next one.
         */
        fun rateCard(rating: Rating) {
            val currentState = uiState.value
            val currentCard = currentState.cards.getOrNull(currentState.currentIndex) ?: return

            viewModelScope.launch {
                // Update card state in the repository (spaced repetition logic)
                repository.updateCardState(currentCard.card.cardId, rating)
                // Proceed to the next card
                nextCard()
            }
        }

        /**
         * Moves to the next card in the list or completes the session.
         */
        fun nextCard() {
            _uiState.update { state ->
                val nextIndex = state.currentIndex + 1
                if (nextIndex < state.cards.size) {
                    state.copy(
                        currentIndex = nextIndex,
                        isFlipped = false,
                    )
                } else {
                    state.copy(isSessionComplete = true)
                }
            }
        }
    }
