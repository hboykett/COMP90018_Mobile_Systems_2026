package com.comp90018.flashcards.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comp90018.flashcards.data.auth.AuthRepository
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
import java.time.Clock
import javax.inject.Inject

/**
 * UI State for the Study Screen.
 */
data class StudyUiState(
    val currentCard: CardWithState? = null,
    val reviewedCount: Int = 0,
    val remainingCount: Int = 0,
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
        private val authRepository: AuthRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        // The deckId is passed via navigation arguments
        private val deckId: String = checkNotNull(savedStateHandle["deckId"])

        private val _uiState = MutableStateFlow(StudyUiState())
        val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

        private var session = StudySession(emptyList())

        // Stops a quick double tap from rating the same card twice.
        private var isSavingRating = false

        init {
            loadDueCards()
        }

        /**
         * Loads cards that are due for review in the current deck.
         */
        private fun loadDueCards() {
            val userId = authRepository.currentUid
            if (userId == null) {
                _uiState.update { it.copy(isSessionComplete = true) }
                return
            }
            viewModelScope.launch {
                // The first emission is the starting queue. Cards rated into a learning step are
                // added back by the session, so later database changes are not needed here.
                session = StudySession(repository.getDueCards(userId, deckId).first())
                publishSession()
            }
        }

        /**
         * Toggles the flip state of the current card.
         */
        fun flipCard() {
            _uiState.update { it.copy(isFlipped = !it.isFlipped) }
        }

        /**
         * Rates the current card, reschedules it with FSRS and moves to the next card.
         */
        fun rateCard(rating: Rating) {
            val currentCard = session.current
            val userId = authRepository.currentUid
            if (currentCard == null || userId == null || isSavingRating) return
            isSavingRating = true

            viewModelScope.launch {
                try {
                    val updated = repository.reviewCard(userId, currentCard.card.cardId, rating)
                    session.answer(currentCard.copy(state = updated), clock.instant())
                    publishSession()
                } finally {
                    isSavingRating = false
                }
            }
        }

        private fun publishSession() {
            _uiState.update {
                it.copy(
                    currentCard = session.current,
                    reviewedCount = session.reviewedCount,
                    remainingCount = session.remainingCount,
                    isFlipped = false,
                    isSessionComplete = session.isComplete,
                )
            }
        }
    }
