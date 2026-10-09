package com.comp90018.flashcards.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.comp90018.flashcards.domain.model.Rating
import java.time.Instant

/**
 * One review, recorded once and never changed.
 *
 * Replaying a card's logs in time order through the scheduler rebuilds its FSRS state, so logs
 * from several devices can be merged by taking the union. They are also the input for
 * optimising per-user FSRS weights later. [reviewLogId] is a UUID so ids never collide
 * across devices.
 */
@Entity(
    tableName = "review_logs",
    indices = [Index("userId", "cardId"), Index("cardId")],
)
data class ReviewLogEntity(
    @PrimaryKey val reviewLogId: String,
    val userId: String,
    val cardId: String,
    val rating: Rating,
    val reviewedAt: Instant,
)
