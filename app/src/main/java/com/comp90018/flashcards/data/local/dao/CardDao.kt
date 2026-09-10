package com.comp90018.flashcards.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.comp90018.flashcards.data.local.entity.CardEntity
import com.comp90018.flashcards.data.local.entity.SpacedRepetitionState
import kotlinx.coroutines.flow.Flow

/**
 * Interface for Card and SpacedRepetitionState database operations.
 */
@Dao
interface CardDao {

    @Query("""
        SELECT * FROM cards 
        JOIN spaced_repetition_states ON cards.cardId = spaced_repetition_states.cardId 
        WHERE cards.deckId = :deckId
    """)
    fun getCardsWithState(deckId: String): Flow<Map<CardEntity, SpacedRepetitionState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: SpacedRepetitionState)

    @Query("SELECT * FROM spaced_repetition_states WHERE cardId = :cardId")
    suspend fun getStateForCard(cardId: String): SpacedRepetitionState?

    @Update
    suspend fun updateState(state: SpacedRepetitionState)

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    fun getCardsByDeckId(deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE cardId = :cardId")
    suspend fun getCardById(cardId: String): CardEntity?

    @Update
    suspend fun updateCard(card: CardEntity)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Transaction
    suspend fun deleteCardAndState(card: CardEntity) {
        deleteCard(card)
        deleteState(card.cardId)
    }

    @Query("DELETE FROM spaced_repetition_states WHERE cardId = :cardId")
    suspend fun deleteState(cardId: String)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int
}
