package com.comp90018.flashcards.data.remote

/**
 * Thin Firestore access for cloud decks. Does not sync with Room; that is issue #19.
 */
interface DeckRemoteDataSource {
    suspend fun upsertDeck(deck: RemoteDeck): Result<Unit>

    suspend fun getDeck(deckId: String): Result<RemoteDeck?>

    suspend fun listDecksForOwner(ownerId: String): Result<List<RemoteDeck>>

    suspend fun listPublicDecks(): Result<List<RemoteDeck>>

    suspend fun deleteDeck(deckId: String): Result<Unit>

    suspend fun upsertCard(card: RemoteCard): Result<Unit>

    suspend fun listCards(deckId: String): Result<List<RemoteCard>>

    suspend fun deleteCard(
        deckId: String,
        cardId: String,
    ): Result<Unit>
}
