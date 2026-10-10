package com.comp90018.flashcards.di

import com.comp90018.flashcards.domain.fsrs.FsrsParameters
import com.comp90018.flashcards.domain.fsrs.FsrsScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SchedulerModule {
    @Provides
    @Singleton
    fun provideFsrsScheduler(): FsrsScheduler = FsrsScheduler(FsrsParameters())

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
