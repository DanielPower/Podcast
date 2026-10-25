package ca.danielpower.podcast.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration

/**
 * Feed text, which is often HTML, as styled text. Only formatting (bold, italics, paragraphs,
 * lists) and links come through: nothing is run or loaded, and images are dropped.
 */
@Composable
fun rememberFeedText(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.secondary
    return remember(text, linkColor) {
        if ('<' in text || '&' in text) {
            val html = AnnotatedString.fromHtml(
                text,
                linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            )
            // Images become an object replacement character, and block tags leave blank lines at the ends.
            trimmed(html)
        } else {
            AnnotatedString(text.trim())
        }
    }
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
