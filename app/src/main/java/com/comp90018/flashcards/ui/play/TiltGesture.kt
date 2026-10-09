package com.comp90018.flashcards.ui.play

/**
 * The two gestures the multiplayer modes understand: tilting the phone up or down.
 *
 * While the back of a card is showing, DOWN marks the guess right and UP marks it wrong.
 * The sensor code should send these to the game's ViewModel. Until it does, tapping the top
 * or bottom of the screen sends them.
 */
enum class TiltGesture { UP, DOWN }
