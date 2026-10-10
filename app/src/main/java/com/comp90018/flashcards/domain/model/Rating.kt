package com.comp90018.flashcards.domain.model

/**
 * How well the user recalled a card. [grade] is the 1-4 value used in the FSRS formulas.
 */
enum class Rating(
    val grade: Int,
) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
}
