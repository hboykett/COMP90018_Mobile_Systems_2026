package com.comp90018.flashcards.data.local

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Stores instants as UTC epoch milliseconds, so the values mean the same thing on every device.
 */
class Converters {
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()
}
