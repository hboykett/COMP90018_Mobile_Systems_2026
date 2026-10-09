package com.comp90018.flashcards.ui.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownTest {
    @Test
    fun `seconds are used as typed`() {
        assertEquals(45, parseCountdownSeconds("45", CountdownUnit.SECONDS))
    }

    @Test
    fun `minutes are converted to seconds`() {
        assertEquals(120, parseCountdownSeconds("2", CountdownUnit.MINUTES))
    }

    @Test
    fun `surrounding spaces are ignored`() {
        assertEquals(30, parseCountdownSeconds(" 30 ", CountdownUnit.SECONDS))
    }

    @Test
    fun `blank, zero and non-numbers are rejected`() {
        assertNull(parseCountdownSeconds("", CountdownUnit.SECONDS))
        assertNull(parseCountdownSeconds("0", CountdownUnit.SECONDS))
        assertNull(parseCountdownSeconds("abc", CountdownUnit.SECONDS))
        assertNull(parseCountdownSeconds("-5", CountdownUnit.SECONDS))
        assertNull(parseCountdownSeconds("1.5", CountdownUnit.MINUTES))
    }

    @Test
    fun `the longest allowed countdown is accepted and anything longer is rejected`() {
        assertEquals(600, parseCountdownSeconds("600", CountdownUnit.SECONDS))
        assertEquals(600, parseCountdownSeconds("10", CountdownUnit.MINUTES))
        assertNull(parseCountdownSeconds("601", CountdownUnit.SECONDS))
        assertNull(parseCountdownSeconds("11", CountdownUnit.MINUTES))
    }

    @Test
    fun `countdowns are described in words`() {
        assertEquals("1 second", describeCountdown(1))
        assertEquals("10 seconds", describeCountdown(10))
        assertEquals("90 seconds", describeCountdown(90))
        assertEquals("1 minute", describeCountdown(60))
        assertEquals("2 minutes", describeCountdown(120))
    }

    @Test
    fun `the clock shows seconds under a minute and minutes after`() {
        assertEquals("9s", formatCountdown(9))
        assertEquals("59s", formatCountdown(59))
        assertEquals("1:00", formatCountdown(60))
        assertEquals("1:05", formatCountdown(65))
        assertEquals("10:00", formatCountdown(600))
    }
}
