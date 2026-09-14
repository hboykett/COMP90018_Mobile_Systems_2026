package com.comp90018.flashcards.ui.deck

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.data.local.entity.DeckEntity
import com.comp90018.flashcards.domain.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckDetailViewModel
    @Inject
    constructor(
        private val repository: CardRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val deckId: String = checkNotNull(savedStateHandle["deckId"])

        private val _deck = MutableStateFlow<DeckEntity?>(null)
        val deck: StateFlow<DeckEntity?> = _deck.asStateFlow()

        val cards: StateFlow<List<CardEntity>> =
            repository
                .getCardsByDeckId(deckId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        init {
            viewModelScope.launch {
                _deck.value = repository.getDeckById(deckId)
            }
        }

        fun deleteCard(card: CardEntity) {
            viewModelScope.launch {
                repository.deleteCard(card)
            }
        }
    }
