package com.comp90018.flashcards.ui.play

/** The longest countdown a player can pick for one card, in seconds. */
const val MAX_COUNTDOWN_SECONDS = 600

private const val SECONDS_PER_MINUTE = 60

/** The unit a player types a custom countdown in. */
enum class CountdownUnit(
    val label: String,
    val seconds: Int,
) {
    SECONDS("Seconds", 1),
    MINUTES("Minutes", SECONDS_PER_MINUTE),
}

/**
 * Turns what the player typed into a countdown in seconds, or null if it can't be used:
 * blank, not a whole number, zero, or longer than [MAX_COUNTDOWN_SECONDS].
 */
fun parseCountdownSeconds(
    input: String,
    unit: CountdownUnit,
): Int? {
    val amount = input.trim().toIntOrNull() ?: return null
    // The amount is capped first, so the multiplication below cannot overflow.
    val total = amount.takeIf { it in 1..MAX_COUNTDOWN_SECONDS }?.times(unit.seconds)
    return total?.takeIf { it <= MAX_COUNTDOWN_SECONDS }
}

/** A countdown in words, such as "30 seconds" or "2 minutes". */
fun describeCountdown(seconds: Int): String =
    if (seconds >= SECONDS_PER_MINUTE && seconds % SECONDS_PER_MINUTE == 0) {
        val minutes = seconds / SECONDS_PER_MINUTE
        if (minutes == 1) "1 minute" else "$minutes minutes"
    } else if (seconds == 1) {
        "1 second"
    } else {
        "$seconds seconds"
    }

/** The time left on the clock, as "45s" under a minute and "1:05" from a minute up. */
fun formatCountdown(seconds: Int): String =
    if (seconds < SECONDS_PER_MINUTE) {
        "${seconds}s"
    } else {
        "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
    }
