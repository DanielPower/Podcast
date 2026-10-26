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

    @Test
    fun parsesJsonChaptersSkippingHiddenOnes() {
        val json = """
            {"version": "1.2.0", "chapters": [
                {"startTime": 95.5, "title": "Second", "url": "https://example.com"},
                {"startTime": 0, "title": "Intro", "img": "https://example.com/a.jpg"},
                {"startTime": 120, "title": "Hidden", "toc": false}
            ]}
        """.trimIndent()
        val chapters = FeedParser.parseJsonChapters(json)
        assertEquals(listOf(0L, 95_500L), chapters.map { it.startMs })
        assertEquals(listOf("Intro", "Second"), chapters.map { it.title })
        assertEquals("https://example.com/a.jpg", chapters[0].imageUrl)
        assertEquals("https://example.com", chapters[1].url)
    }

    @Test
    fun readsFundingLinks() {
        assertEquals(
            FundingLink("https://patreon.com/show", "Support the show"),
            FeedParser.fundingLink(" https://patreon.com/show ", "\n  Support   the show\n"),
        )
        // No text: the host stands in.
        assertEquals("ko-fi.com", FeedParser.fundingLink("https://www.ko-fi.com/show", " ")?.label)
        assertEquals(128, FeedParser.fundingLink("https://example.com", "x".repeat(200))?.label?.length)
        assertNull(FeedParser.fundingLink("lightning:abc", "Boost"))
        assertNull(FeedParser.fundingLink("javascript:alert(1)", "Click"))
        assertNull(FeedParser.fundingLink(null, "Support"))
    }
}
