package com.comp90018.flashcards.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.comp90018.flashcards.data.local.entity.CardFsrsStateEntity
import com.comp90018.flashcards.data.local.entity.ReviewLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * FSRS scheduling state and review history.
 */
@Dao
interface ReviewDao {
    @Query(
        """
        SELECT card_fsrs_states.* FROM card_fsrs_states
        JOIN cards ON cards.cardId = card_fsrs_states.cardId
        WHERE card_fsrs_states.userId = :userId AND cards.deckId = :deckId
    """,
    )
    fun observeStatesForDeck(
        userId: String,
        deckId: String,
    ): Flow<List<CardFsrsStateEntity>>

    @Query("SELECT * FROM card_fsrs_states WHERE userId = :userId AND cardId = :cardId")
    suspend fun getState(
        userId: String,
        cardId: String,
    ): CardFsrsStateEntity?

    @Upsert
    suspend fun upsertState(state: CardFsrsStateEntity)

    @Insert
    suspend fun insertReviewLog(log: ReviewLogEntity)

    /** Saves the card's new state and the log of the review that produced it together. */
    @Transaction
    suspend fun recordReview(
        state: CardFsrsStateEntity,
        log: ReviewLogEntity,
    ) {
        upsertState(state)
        insertReviewLog(log)
    }

    @Query("SELECT * FROM review_logs WHERE userId = :userId AND cardId = :cardId ORDER BY reviewedAt")
    suspend fun getReviewLogs(
        userId: String,
        cardId: String,
    ): List<ReviewLogEntity>
}
