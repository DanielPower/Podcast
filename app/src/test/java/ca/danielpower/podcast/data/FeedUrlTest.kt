package ca.danielpower.podcast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedUrlTest {
    @Test
    fun recognisesFeedUrls() {
        assertEquals("https://example.com/feed.xml", feedUrlOrNull("  https://example.com/feed.xml "))
        assertEquals("http://example.com/rss", feedUrlOrNull("http://example.com/rss"))
        assertEquals("https://example.com/rss", feedUrlOrNull("feed://example.com/rss"))
    }

    @Test
    fun leavesSearchWordsAlone() {
        assertNull(feedUrlOrNull("the daily"))
        assertNull(feedUrlOrNull("example.com"))
        assertNull(feedUrlOrNull(""))
    }
}
