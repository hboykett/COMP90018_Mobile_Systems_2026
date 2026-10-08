package com.comp90018.flashcards.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore implementation of [DeckRemoteDataSource].
 *
 * Returns a clear failure when Firebase is not configured (missing `google-services.json`).
 */
@Singleton
class FirestoreDeckRemoteDataSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore?,
    ) : DeckRemoteDataSource {
        override suspend fun upsertDeck(deck: RemoteDeck): Result<Unit> =
            runRemote {
                decks()
                    .document(deck.deckId)
                    .set(deck.toFirestoreMap(), SetOptions.merge())
                    .await()
            }

        override suspend fun getDeck(deckId: String): Result<RemoteDeck?> =
            runRemote {
                decks()
                    .document(deckId)
                    .get()
                    .await()
                    .takeIf { it.exists() }
                    ?.let { RemoteDeckMapper.from(it.id, it.data.orEmpty()) }
            }

        override suspend fun listDecksForOwner(ownerId: String): Result<List<RemoteDeck>> =
            runRemote {
                decks()
                    .whereEqualTo(FIELD_OWNER_ID, ownerId)
                    .orderBy(FIELD_UPDATED_AT, Query.Direction.DESCENDING)
                    .get()
                    .await()
                    .documents
                    .map { RemoteDeckMapper.from(it.id, it.data.orEmpty()) }
            }

        override suspend fun listPublicDecks(): Result<List<RemoteDeck>> =
            runRemote {
                decks()
                    .whereEqualTo(FIELD_VISIBILITY, DeckVisibility.PUBLIC.firestoreValue)
                    .orderBy(FIELD_UPDATED_AT, Query.Direction.DESCENDING)
                    .get()
                    .await()
                    .documents
                    .map { RemoteDeckMapper.from(it.id, it.data.orEmpty()) }
            }

        override suspend fun deleteDeck(deckId: String): Result<Unit> =
            runRemote {
                val cardDocs =
                    cards(deckId)
                        .get()
                        .await()
                        .documents
                // Batch delete cards then the deck so orphaned card docs are not left behind.
                val batch = requireFirestore().batch()
                cardDocs.forEach { batch.delete(it.reference) }
                batch.delete(decks().document(deckId))
                batch.commit().await()
            }

        override suspend fun upsertCard(card: RemoteCard): Result<Unit> =
            runRemote {
                cards(card.deckId)
                    .document(card.cardId)
                    .set(card.toFirestoreMap(), SetOptions.merge())
                    .await()
            }

        override suspend fun listCards(deckId: String): Result<List<RemoteCard>> =
            runRemote {
                cards(deckId)
                    .orderBy(FIELD_POSITION)
                    .get()
                    .await()
                    .documents
                    .map { RemoteCardMapper.from(deckId, it.id, it.data.orEmpty()) }
            }

        override suspend fun deleteCard(
            deckId: String,
            cardId: String,
        ): Result<Unit> =
            runRemote {
                cards(deckId).document(cardId).delete().await()
            }

        private fun decks() = requireFirestore().collection(COLLECTION_DECKS)

        private fun cards(deckId: String) = decks().document(deckId).collection(COLLECTION_CARDS)

        private fun requireFirestore(): FirebaseFirestore = firestore ?: error(NOT_CONFIGURED_MESSAGE)

        @Suppress("TooGenericExceptionCaught")
        private suspend fun <T> runRemote(block: suspend () -> T): Result<T> =
            try {
                Result.success(block())
            } catch (error: Exception) {
                Result.failure(error)
            }

        private companion object {
            const val COLLECTION_DECKS = "decks"
            const val COLLECTION_CARDS = "cards"
            const val FIELD_OWNER_ID = "ownerId"
            const val FIELD_VISIBILITY = "visibility"
            const val FIELD_UPDATED_AT = "updatedAt"
            const val FIELD_POSITION = "position"
            const val NOT_CONFIGURED_MESSAGE =
                "Firestore is not configured. Add app/google-services.json and enable Firestore."
        }
    }

internal object RemoteDeckMapper {
    fun from(
        deckId: String,
        data: Map<String, Any?>,
    ): RemoteDeck =
        RemoteDeck(
            deckId = deckId,
            name = data["name"] as? String ?: "",
            ownerId = data["ownerId"] as? String ?: "",
            visibility = DeckVisibility.fromFirestore(data["visibility"] as? String),
            updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.toInstant() ?: Instant.EPOCH,
            cardCount = (data["cardCount"] as? Number)?.toInt() ?: 0,
        )
}

internal object RemoteCardMapper {
    fun from(
        deckId: String,
        cardId: String,
        data: Map<String, Any?>,
    ): RemoteCard =
        RemoteCard(
            cardId = cardId,
            deckId = deckId,
            front = data["front"] as? String ?: "",
            back = data["back"] as? String ?: "",
            position = (data["position"] as? Number)?.toInt() ?: 0,
            frontImageUri = data["frontImageUri"] as? String,
            backImageUri = data["backImageUri"] as? String,
        )
}

private fun RemoteDeck.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "name" to name,
        "ownerId" to ownerId,
        "visibility" to visibility.firestoreValue,
        "updatedAt" to Timestamp(updatedAt.epochSecond, updatedAt.nano),
        "cardCount" to cardCount,
    )

private fun RemoteCard.toFirestoreMap(): Map<String, Any?> =
    buildMap {
        put("front", front)
        put("back", back)
        put("position", position)
        frontImageUri?.let { put("frontImageUri", it) }
        backImageUri?.let { put("backImageUri", it) }
    }
