package ca.danielpower.podcast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedParserTest {
    @Test
    fun parsesDurations() {
        assertEquals(3_723_000L, FeedParser.parseDuration("1:02:03"))
        assertEquals(125_000L, FeedParser.parseDuration("02:05"))
        assertEquals(3_600_000L, FeedParser.parseDuration("3600"))
        assertNull(FeedParser.parseDuration("about an hour"))
    }

    @Test
    fun parsesRfc822Dates() {
        assertEquals(1_761_384_600_000L, FeedParser.parseDate("Sat, 25 Oct 2025 09:30:00 GMT"))
        assertEquals(1_761_384_600_000L, FeedParser.parseDate("Sat, 25 Oct 2025 05:30:00 -0400"))
        assertNull(FeedParser.parseDate("yesterday"))
    }
}
