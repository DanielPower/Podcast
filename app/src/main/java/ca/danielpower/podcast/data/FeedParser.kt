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
    val link: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    /** Podcasting 2.0 `<podcast:chapters>` JSON file, fetched when the episode is opened. */
    val chaptersUrl: String? = null,
    /** Podlove Simple Chapters given inline in the feed. */
    val inlineChapters: List<ParsedChapter> = emptyList(),
)

data class ParsedChapter(
    val startMs: Long,
    val title: String,
    val url: String? = null,
    val imageUrl: String? = null,
)

/** Minimal RSS 2.0 + iTunes podcast feed parser. */
object FeedParser {

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var title: String? = null
        var author: String? = null
        val podcastImages = mutableListOf<PodcastImage>()
        var itunesImage: String? = null
        var rssImage: String? = null
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
                "podcast:image" -> readPodcastImage(parser)?.let { podcastImages += it }
                "itunes:image" -> if (itunesImage == null) itunesImage = parser.getAttributeValue(null, "href")
                "image" -> {
                    val url = readImageUrl(parser)
                    if (rssImage == null) rssImage = url
                }
            }
        }

        return ParsedFeed(
            title = title?.trim().orEmpty().ifEmpty { "Untitled podcast" },
            author = author?.trim(),
            // Podcasting 2.0 artwork first, then iTunes; the RSS image is often a small thumbnail.
            artworkUrl = bestArtwork(podcastImages) ?: itunesImage?.trim()?.takeIf { it.isNotEmpty() } ?: rssImage,
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
        val podcastImages = mutableListOf<PodcastImage>()
        var artwork: String? = null
        var contentHtml: String? = null
        var link: String? = null
        var season: String? = null
        var episodeNumber: String? = null
        var chaptersUrl: String? = null
        var inlineChapters: List<ParsedChapter> = emptyList()

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
                "podcast:image" -> readPodcastImage(parser)?.let { podcastImages += it }
                "itunes:image" -> artwork = parser.getAttributeValue(null, "href")
                "content:encoded" -> contentHtml = readText(parser)
                "link" -> link = readText(parser)
                "itunes:season" -> season = readText(parser)
                "itunes:episode" -> episodeNumber = readText(parser)
                "podcast:chapters" -> chaptersUrl = parser.getAttributeValue(null, "url")
                "psc:chapters" -> inlineChapters = readPodloveChapters(parser)
            }
        }

        val url = audioUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ParsedEpisode(
            guid = guid?.trim()?.takeIf { it.isNotEmpty() } ?: url,
            title = title?.trim().orEmpty().ifEmpty { "Untitled episode" },
            // Full show notes are usually in content:encoded; description is often a short summary.
            description = (contentHtml?.takeIf { it.isNotBlank() } ?: description)?.trim(),
            audioUrl = url,
            publishedAt = pubDate?.let(::parseDate) ?: 0L,
            durationMs = duration?.let(::parseDuration),
            artworkUrl = bestArtwork(podcastImages) ?: artwork?.trim()?.takeIf { it.isNotEmpty() },
            link = link?.trim()?.takeIf { it.isNotEmpty() },
            seasonNumber = season?.trim()?.toIntOrNull(),
            episodeNumber = episodeNumber?.trim()?.toIntOrNull(),
            chaptersUrl = chaptersUrl?.trim()?.takeIf { it.isNotEmpty() },
            inlineChapters = inlineChapters,
        )
    }

    /** A Podcasting 2.0 `<podcast:image>`. */
    private class PodcastImage(val href: String, val width: Int, val purpose: String?)

    private fun readPodcastImage(parser: XmlPullParser): PodcastImage? {
        val href = parser.getAttributeValue(null, "href")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return PodcastImage(
            href = href,
            width = parser.getAttributeValue(null, "width")?.trim()?.toIntOrNull() ?: 0,
            purpose = parser.getAttributeValue(null, "purpose"),
        )
    }

    /**
     * The largest `<podcast:image>` meant as artwork. One with no purpose counts as artwork; ones
     * only for other uses (a banner, social sharing) are skipped.
     */
    private fun bestArtwork(images: List<PodcastImage>): String? =
        images
            .filter { image -> image.purpose.isNullOrBlank() || image.purpose.split(' ').any { it == "artwork" } }
            .maxByOrNull { it.width }
            ?.href

    /** Reads `<psc:chapters>`, leaving the parser at its closing tag. */
    private fun readPodloveChapters(parser: XmlPullParser): List<ParsedChapter> {
        val chapters = mutableListOf<ParsedChapter>()
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "psc:chapter") {
                val start = parser.getAttributeValue(null, "start")?.let(::parseDuration) ?: continue
                chapters += ParsedChapter(
                    startMs = start,
                    title = parser.getAttributeValue(null, "title")?.trim().orEmpty(),
                    url = parser.getAttributeValue(null, "href"),
                    imageUrl = parser.getAttributeValue(null, "image"),
                )
            }
        }
        return chapters.sortedBy { it.startMs }
    }

    /** Parses a Podcasting 2.0 JSON chapters file. Chapters marked `"toc": false` are left out. */
    fun parseJsonChapters(json: String): List<ParsedChapter> {
        val array = org.json.JSONObject(json).optJSONArray("chapters") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.getJSONObject(i)
            if (!item.optBoolean("toc", true)) return@mapNotNull null
            if (!item.has("startTime")) return@mapNotNull null
            ParsedChapter(
                startMs = (item.getDouble("startTime") * 1000).toLong(),
                title = item.optString("title").trim(),
                url = item.optString("url").takeIf { it.isNotBlank() },
                imageUrl = item.optString("img").takeIf { it.isNotBlank() },
            )
        }.sortedBy { it.startMs }
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
