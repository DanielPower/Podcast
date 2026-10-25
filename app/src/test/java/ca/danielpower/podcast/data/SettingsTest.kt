package ca.danielpower.podcast.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    @Test
    fun formatsSpeedsWithoutTrailingZeros() {
        assertEquals("1×", formatSpeed(1f))
        assertEquals("1.25×", formatSpeed(1.25f))
        assertEquals("1.5×", formatSpeed(1.5f))
        assertEquals("0.9×", formatSpeed(0.9f))
        assertEquals("2×", formatSpeed(2f))
    }
}
