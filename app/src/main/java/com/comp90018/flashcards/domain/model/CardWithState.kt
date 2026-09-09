package com.comp90018.flashcards.domain.model

import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.data.local.entity.SpacedRepetitionState

/**
 * Domain model combining a card with its repetition state.
 */
data class CardWithState(
    val card: CardEntity,
    val state: SpacedRepetitionState
)
