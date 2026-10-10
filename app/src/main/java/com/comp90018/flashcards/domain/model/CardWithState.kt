package com.comp90018.flashcards.domain.model

import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.domain.fsrs.FsrsCard

/**
 * A card together with the current user's FSRS state for it.
 */
data class CardWithState(
    val card: CardEntity,
    val state: FsrsCard,
)
