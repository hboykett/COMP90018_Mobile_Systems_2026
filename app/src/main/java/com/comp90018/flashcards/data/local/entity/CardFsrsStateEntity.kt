package com.comp90018.flashcards.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import com.comp90018.flashcards.domain.fsrs.CardState
import com.comp90018.flashcards.domain.fsrs.FsrsCard
import java.time.Instant

/**
 * One user's FSRS scheduling state for one card.
 *
 * Keyed by (userId, cardId) rather than cardId alone, so a shared deck keeps separate progress
 * for each user. A card with no row for a user is new to that user.
 *
 * Written for future sync: ids are the same UUIDs used remotely, instants are UTC epoch millis,
 * and [updatedAt] lets two devices resolve a conflict by keeping the latest write.
 */
@Entity(
    tableName = "card_fsrs_states",
    primaryKeys = ["userId", "cardId"],
    indices = [Index("cardId")],
)
data class CardFsrsStateEntity(
    val userId: String,
    val cardId: String,
    val state: CardState,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val due: Instant,
    val lastReview: Instant?,
    val reps: Int,
    val lapses: Int,
    val updatedAt: Instant,
) {
    fun toDomain(): FsrsCard =
        FsrsCard(
            state = state,
            step = step,
            stability = stability,
            difficulty = difficulty,
            due = due,
            lastReview = lastReview,
            reps = reps,
            lapses = lapses,
        )

    companion object {
        fun from(
            userId: String,
            cardId: String,
            card: FsrsCard,
            updatedAt: Instant,
        ): CardFsrsStateEntity =
            CardFsrsStateEntity(
                userId = userId,
                cardId = cardId,
                state = card.state,
                step = card.step,
                stability = card.stability,
                difficulty = card.difficulty,
                due = card.due,
                lastReview = card.lastReview,
                reps = card.reps,
                lapses = card.lapses,
                updatedAt = updatedAt,
            )
    }
}
