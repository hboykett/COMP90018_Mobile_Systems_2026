package com.comp90018.flashcards.di

import android.content.Context
import androidx.room.Room
import com.comp90018.flashcards.data.local.FlashcardDatabase
import com.comp90018.flashcards.data.local.dao.CardDao
import com.comp90018.flashcards.data.local.dao.DeckDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FlashcardDatabase =
        Room
            .databaseBuilder(
                context,
                FlashcardDatabase::class.java,
                "flashcard_db",
            ).fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCardDao(database: FlashcardDatabase): CardDao = database.cardDao()

    @Provides
    fun provideDeckDao(database: FlashcardDatabase): DeckDao = database.deckDao()
}
