package com.comp90018.flashcards.data.remote

/**
 * Whether a cloud deck is only visible to its owner or listed for other signed-in users.
 */
enum class DeckVisibility(
    val firestoreValue: String,
) {
    PRIVATE("private"),
    PUBLIC("public"),
    ;

    companion object {
        fun fromFirestore(value: String?): DeckVisibility =
            entries.firstOrNull { it.firestoreValue == value } ?: PRIVATE
    }
}
