package ca.danielpower.podcast.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimestampTest {
    @Test
    fun findsMinutesAndHours() {
        val text = "Intro 0:00, guest at 12:34, Q&A 1:02:03."
        assertEquals(listOf(0L, 754_000L, 3_723_000L), findTimestamps(text).map { it.ms })
        val last = findTimestamps(text).last()
        assertEquals("1:02:03", text.substring(last.start, last.end))
    }

    @Test
    fun allowsLongMinutesWithoutHours() {
        assertEquals(listOf(4_510_000L), findTimestamps("at 75:10").map { it.ms })
    }

    @Test
    fun skipsImpossibleTimes() {
        assertEquals(emptyList<Timestamp>(), findTimestamps("12:75 and 1:60:00"))
    }

    @Test
    fun skipsLongerNumberRuns() {
        assertEquals(emptyList<Timestamp>(), findTimestamps("1:02:03:04 and 123:45"))
    }

    @Test
    fun readsSeekUris() {
        assertEquals(90_000L, seekUriMs(seekUri(90_000)))
        assertNull(seekUriMs("https://example.com"))
    }
}
