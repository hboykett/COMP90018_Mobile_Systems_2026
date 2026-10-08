package com.comp90018.flashcards.ui.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.domain.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * UI State for the 2-player screen.
 */
data class DuoUiState(
    val isLoading: Boolean = true,
    val deckName: String = "",
    val phase: DuoPhase = DuoPhase.READY,
    val current: CardEntity? = null,
    val secondsPerCard: Int = 0,
    val secondsLeft: Int = 0,
    val correctCount: Int = 0,
    val totalCount: Int = 0,
    val missed: List<CardEntity> = emptyList(),
)

/**
 * ViewModel for a 2-player round.
 *
 * Each card gets a countdown of the length the players chose. When it runs out, or the Guesser
 * acts first, the back is revealed. The players then tilt down for right or up for wrong.
 * [onGesture] is where the tilt sensor plugs in. Until then the screen sends it from taps.
 */
@HiltViewModel
class DuoViewModel
    @Inject
    constructor(
        private val repository: CardRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        // The deckId is passed via navigation arguments
        private val deckId: String = checkNotNull(savedStateHandle["deckId"])

        // How long the Guesser has for each card, picked on the choose-countdown page.
        private val secondsPerCard: Int = checkNotNull(savedStateHandle["seconds"])

        private val _uiState = MutableStateFlow(DuoUiState(secondsPerCard = secondsPerCard))
        val uiState: StateFlow<DuoUiState> = _uiState.asStateFlow()

        private var cards: List<CardEntity> = emptyList()
        private var game = DuoGame(emptyList())
        private var timerJob: Job? = null

        init {
            loadDeck()
        }

        private fun loadDeck() {
            viewModelScope.launch {
                val deckName = repository.getDeckById(deckId)?.name.orEmpty()
                cards = repository.getCardsByDeckId(deckId).first()
                // Shuffled so every round comes up in a different order.
                game = DuoGame(cards.shuffled())
                publish(isLoading = false, deckName = deckName)
            }
        }

        /** Starts the round, called from the Ready screen. */
        fun start() {
            game.start()
            if (game.phase == DuoPhase.FRONT) startTimer()
            publish()
        }

        /** Starts a new round with the same cards, in a new order, and the same countdown. */
        fun playAgain() {
            timerJob?.cancel()
            game = DuoGame(cards.shuffled())
            start()
        }

        /** Shows the back of the current card, ahead of the countdown. */
        fun reveal() {
            timerJob?.cancel()
            showBack()
        }

        /**
         * Handles a tilt. With the front showing, either direction reveals the back.
         * With the back showing, DOWN marks the guess right and UP marks it wrong.
         */
        fun onGesture(gesture: TiltGesture) {
            when (game.phase) {
                DuoPhase.FRONT -> reveal()
                DuoPhase.BACK -> mark(isCorrect = gesture == TiltGesture.DOWN)
                DuoPhase.READY, DuoPhase.FINISHED -> Unit
            }
        }

        private fun mark(isCorrect: Boolean) {
            game.mark(isCorrect)
            if (game.phase == DuoPhase.FRONT) startTimer()
            publish()
        }

        private fun showBack() {
            game.reveal()
            publish()
        }

        private fun startTimer() {
            timerJob?.cancel()
            timerJob =
                viewModelScope.launch {
                    for (second in secondsPerCard downTo 1) {
                        _uiState.update { it.copy(secondsLeft = second) }
                        delay(1.seconds)
                    }
                    showBack()
                }
        }

        private fun publish(
            isLoading: Boolean = false,
            deckName: String = _uiState.value.deckName,
        ) {
            _uiState.update {
                it.copy(
                    isLoading = isLoading,
                    deckName = deckName,
                    phase = game.phase,
                    current = game.current,
                    correctCount = game.correctCount,
                    totalCount = game.totalCount,
                    missed = game.missed,
                )
            }
        }

        override fun onCleared() {
            timerJob?.cancel()
        }
    }
