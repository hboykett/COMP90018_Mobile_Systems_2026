package com.comp90018.flashcards.di

import com.comp90018.flashcards.data.auth.FirebaseAuthProvider
import com.comp90018.flashcards.data.remote.DeckRemoteDataSource
import com.comp90018.flashcards.data.remote.FirestoreDeckRemoteDataSource
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseBindModule {
    @Binds
    @Singleton
    abstract fun bindDeckRemoteDataSource(impl: FirestoreDeckRemoteDataSource): DeckRemoteDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    /**
     * Firestore is only available when Firebase was initialised (google-services.json present).
     * Depends on [FirebaseAuthProvider] so Auth and Firestore share one FirebaseApp init path.
     */
    @Provides
    @Singleton
    fun provideFirestore(firebaseAuthProvider: FirebaseAuthProvider): FirebaseFirestore? =
        firebaseAuthProvider.auth?.app?.let { FirebaseFirestore.getInstance(it) }
}
