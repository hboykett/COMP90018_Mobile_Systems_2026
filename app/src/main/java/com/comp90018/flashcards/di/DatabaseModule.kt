package com.comp90018.flashcards.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.comp90018.flashcards.data.local.DatabaseInitializer
import com.comp90018.flashcards.data.local.FlashcardDatabase
import com.comp90018.flashcards.data.local.dao.CardDao
import com.comp90018.flashcards.data.local.dao.DeckDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        initializerProvider: Provider<DatabaseInitializer>
    ): FlashcardDatabase {
        return Room.databaseBuilder(
            context,
            FlashcardDatabase::class.java,
            "flashcard_db"
        ).fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Use the provider to avoid circular dependency
                initializerProvider.get().seedDatabaseIfEmpty()
            }
        }).build()
    }

    @Provides
    fun provideCardDao(database: FlashcardDatabase): CardDao {
        return database.cardDao()
    }

    @Provides
    fun provideDeckDao(database: FlashcardDatabase): DeckDao {
        return database.deckDao()
    }
}
