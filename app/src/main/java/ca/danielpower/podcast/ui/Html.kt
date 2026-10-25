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
 * Long text is parsed off the main thread, so a page opening with it doesn't drop frames as it
 * slides in; it's null until then. Short text, and text parsed before, is ready right away.
 */
@Composable
fun rememberFeedText(text: String): AnnotatedString? {
    val linkColor = MaterialTheme.colorScheme.secondary
    var parsed by remember(text, linkColor) {
        val cached = parsedFeedText.get(text to linkColor)
        mutableStateOf(cached ?: if (text.length <= PARSE_NOW_CHARS) parseFeedText(text, linkColor) else null)
    }
    LaunchedEffect(text, linkColor) {
        if (parsed == null) parsed = withContext(Dispatchers.Default) { parseFeedText(text, linkColor) }
    }
    return parsed
}

/** Text this long or shorter is parsed during composition; it's quick, and nothing pops in. */
private const val PARSE_NOW_CHARS = 2_000

/** Recently parsed show notes, so opening one again is instant. */
private val parsedFeedText = LruCache<Pair<String, Color>, AnnotatedString>(32)

private fun parseFeedText(text: String, linkColor: Color): AnnotatedString {
    val result = if ('<' in text || '&' in text) {
        val html = AnnotatedString.fromHtml(
            text,
            linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
        )
        // Images become an object replacement character, and block tags leave blank lines at the ends.
        trimmed(html)
    } else {
        AnnotatedString(text.trim())
    }
    parsedFeedText.put(text to linkColor, result)
    return result
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
