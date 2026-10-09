package com.comp90018.flashcards.ui.deck

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comp90018.flashcards.domain.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for the screens that show a deck before it is played.
 */
data class DeckSummaryUiState(
    val deckName: String = "",
    val cardCount: Int = 0,
)

/**
 * ViewModel for the deck page and the choose-mode page. Both show the deck's name and size.
 */
@HiltViewModel
class DeckSummaryViewModel
    @Inject
    constructor(
        repository: CardRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        // The deckId is passed via navigation arguments
        val deckId: String = checkNotNull(savedStateHandle["deckId"])

        private val deckName = MutableStateFlow("")

        val uiState: StateFlow<DeckSummaryUiState> =
            combine(deckName, repository.getCardsByDeckId(deckId)) { name, cards ->
                DeckSummaryUiState(deckName = name, cardCount = cards.size)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DeckSummaryUiState())

        init {
            viewModelScope.launch {
                deckName.value = repository.getDeckById(deckId)?.name.orEmpty()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5000L
        }
    }
