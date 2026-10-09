package com.comp90018.flashcards.domain.fsrs

/**
 * Where a card is in the FSRS learning cycle.
 */
enum class CardState {
    /** Never reviewed. Has no stability or difficulty yet. */
    NEW,

    /** Working through the short learning steps after the first review. */
    LEARNING,

    /** Graduated. Scheduled in whole days from its stability. */
    REVIEW,

    /** Forgotten during review and working through the relearning steps. */
    RELEARNING,
}
