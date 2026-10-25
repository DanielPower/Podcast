package ca.danielpower.podcast.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedFeed(
    val title: String,
    val author: String?,
    val artworkUrl: String?,
    val description: String?,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val publishedAt: Long,
    val durationMs: Long?,
    val artworkUrl: String?,
)

/** Minimal RSS 2.0 + iTunes podcast feed parser. */
object FeedParser {

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var title: String? = null
        var author: String? = null
        var artwork: String? = null
        var description: String? = null
        val episodes = mutableListOf<ParsedEpisode>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "item" -> parseItem(parser)?.let { episodes += it }
                "title" -> if (title == null) title = readText(parser)
                "itunes:author" -> if (author == null) author = readText(parser)
                "description" -> if (description == null) description = readText(parser)
                "itunes:summary" -> if (description == null) description = readText(parser)
                "itunes:image" -> if (artwork == null) artwork = parser.getAttributeValue(null, "href")
                "image" -> {
                    val url = readImageUrl(parser)
                    if (artwork == null) artwork = url
                }
            }
        }

        return ParsedFeed(
            title = title?.trim().orEmpty().ifEmpty { "Untitled podcast" },
            author = author?.trim(),
            artworkUrl = artwork,
            description = description?.trim(),
            episodes = episodes,
        )
    }

    private fun parseItem(parser: XmlPullParser): ParsedEpisode? {
        var guid: String? = null
        var title: String? = null
        var description: String? = null
        var audioUrl: String? = null
        var pubDate: String? = null
        var duration: String? = null
        var artwork: String? = null

        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "guid" -> guid = readText(parser)
                "title" -> title = readText(parser)
                "description" -> if (description == null) description = readText(parser)
                "itunes:summary" -> if (description == null) description = readText(parser)
                "enclosure" -> if (audioUrl == null) audioUrl = parser.getAttributeValue(null, "url")
                "pubDate" -> pubDate = readText(parser)
                "itunes:duration" -> duration = readText(parser)
                "itunes:image" -> artwork = parser.getAttributeValue(null, "href")
            }
        }

        val url = audioUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ParsedEpisode(
            guid = guid?.trim()?.takeIf { it.isNotEmpty() } ?: url,
            title = title?.trim().orEmpty().ifEmpty { "Untitled episode" },
            description = description?.trim(),
            audioUrl = url,
            publishedAt = pubDate?.let(::parseDate) ?: 0L,
            durationMs = duration?.let(::parseDuration),
            artworkUrl = artwork,
        )
    }

    /** Reads `<image><url>…</url></image>`, leaving the parser at the closing image tag. */
    private fun readImageUrl(parser: XmlPullParser): String? {
        var url: String? = null
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "url") {
                url = readText(parser)
            }
        }
        return url?.trim()
    }

    /** Reads the text of the current element (including CDATA), skipping any nested tags. */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            when (parser.eventType) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text)
            }
        }
        return sb.toString()
    }

    private val dateFormats = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    )

    internal fun parseDate(text: String): Long? {
        val value = text.trim()
        for (format in dateFormats) {
            try {
                return ZonedDateTime.parse(value, format).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
        }
        return null
    }

    internal fun parseDuration(text: String): Long? {
        val parts = text.trim().split(":")
        if (parts.isEmpty() || parts.size > 3) return null
        var seconds = 0.0
        for (part in parts) {
            seconds = seconds * 60 + (part.toDoubleOrNull() ?: return null)
        }
        return (seconds * 1000).toLong()
    }
}
