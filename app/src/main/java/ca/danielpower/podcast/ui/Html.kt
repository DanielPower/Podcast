package ca.danielpower.podcast.ui

import android.util.LruCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Feed text, which is often HTML, as styled text. Only formatting (bold, italics, paragraphs,
 * lists) and links come through: nothing is run or loaded, and images are dropped.
 *
 * With [timestamps], times like 12:34 or 1:02:03 become links to [seekUri]s, for show notes.
 *
 * Long text is parsed off the main thread, so a page opening with it doesn't drop frames as it
 * slides in; it's null until then. Short text, and text parsed before, is ready right away.
 */
@Composable
fun rememberFeedText(text: String, timestamps: Boolean = false): AnnotatedString? {
    val linkColor = MaterialTheme.colorScheme.secondary
    val key = FeedTextKey(text, linkColor, timestamps)
    var parsed by remember(key) {
        val cached = parsedFeedText.get(key)
        mutableStateOf(cached ?: if (text.length <= PARSE_NOW_CHARS) parseFeedText(key) else null)
    }
    LaunchedEffect(key) {
        if (parsed == null) parsed = withContext(Dispatchers.Default) { parseFeedText(key) }
    }
    return parsed
}

/** The link a timestamp in show notes opens; [seekUriMs] reads it back. */
fun seekUri(ms: Long) = "$SEEK_SCHEME:$ms"

fun seekUriMs(uri: String): Long? = uri.takeIf { it.startsWith("$SEEK_SCHEME:") }?.substringAfter(':')?.toLongOrNull()

private const val SEEK_SCHEME = "podcast-seek"

/** A time in show notes: `m:ss`, `mm:ss` or `h:mm:ss`, as a character range and a position. */
data class Timestamp(val start: Int, val end: Int, val ms: Long)

private val timestampPattern = Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?![\d:])""")

fun findTimestamps(text: CharSequence): List<Timestamp> = timestampPattern.findAll(text).mapNotNull { match ->
    val hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toInt()
    val seconds = match.groupValues[3].toInt()
    // Without hours, minutes can run past 60 ("75:10"), but not with them.
    if (seconds >= 60 || (match.groupValues[1].isNotEmpty() && minutes >= 60)) return@mapNotNull null
    Timestamp(match.range.first, match.range.last + 1, ((hours * 60L + minutes) * 60 + seconds) * 1000)
}.toList()

private data class FeedTextKey(val text: String, val linkColor: Color, val timestamps: Boolean)

/** Text this long or shorter is parsed during composition; it's quick, and nothing pops in. */
private const val PARSE_NOW_CHARS = 2_000

/** Recently parsed show notes, so opening one again is instant. */
private val parsedFeedText = LruCache<FeedTextKey, AnnotatedString>(32)

private fun parseFeedText(key: FeedTextKey): AnnotatedString {
    val text = key.text
    val linkStyles = TextLinkStyles(SpanStyle(color = key.linkColor, textDecoration = TextDecoration.Underline))
    var result = if ('<' in text || '&' in text) {
        val html = AnnotatedString.fromHtml(text, linkStyles = linkStyles)
        // Images become an object replacement character, and block tags leave blank lines at the ends.
        trimmed(html)
    } else {
        AnnotatedString(text.trim())
    }
    if (key.timestamps) result = withTimestampLinks(result, linkStyles)
    parsedFeedText.put(key, result)
    return result
}

private fun withTimestampLinks(text: AnnotatedString, styles: TextLinkStyles): AnnotatedString {
    val found = findTimestamps(text)
    if (found.isEmpty()) return text
    val links = text.getLinkAnnotations(0, text.length)
    val builder = AnnotatedString.Builder(text)
    for (timestamp in found) {
        // A time that's already part of a link stays that link.
        if (links.any { it.start < timestamp.end && timestamp.start < it.end }) continue
        builder.addLink(LinkAnnotation.Url(seekUri(timestamp.ms), styles), timestamp.start, timestamp.end)
    }
    return builder.toAnnotatedString()
}

private fun trimmed(text: AnnotatedString): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var start = 0
    // Drop image placeholders, keeping the styles around them.
    for (i in text.indices) {
        if (text[i] == '￼') {
            builder.append(text.subSequence(start, i))
            start = i + 1
        }
    }
    builder.append(text.subSequence(start, text.length))
    val clean = builder.toAnnotatedString()
    val first = clean.indexOfFirst { !it.isWhitespace() }
    if (first < 0) return AnnotatedString("")
    val last = clean.indexOfLast { !it.isWhitespace() }
    return clean.subSequence(first, last + 1)
}
