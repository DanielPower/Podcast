package ca.danielpower.podcast.ui

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Feed text, which is often HTML, laid out with the app's one style for it: show notes, podcast
 * descriptions, anything a feed writes. Headings, paragraphs, lists, quotes, code, rules, bold,
 * italics and links come through; nothing is run or loaded, and images are dropped.
 *
 * With [timestamps], times like 12:34 or 1:02:03 become links to [seekUri]s, for show notes.
 * With [collapsedHeight], longer text is cut to that height under a fade, with More and Less.
 */
@Composable
fun FeedText(
    text: String,
    modifier: Modifier = Modifier,
    timestamps: Boolean = false,
    collapsedHeight: Dp? = null,
) {
    val blocks = rememberFeedBlocks(text, timestamps) ?: return
    if (collapsedHeight == null) {
        FeedBlocks(blocks, modifier)
    } else {
        Collapsible(collapsedHeight, modifier) { FeedBlocks(blocks) }
    }
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

/** A block with its inline styles applied. */
@Immutable
private data class StyledBlock(val block: FeedBlock, val text: AnnotatedString)

@Immutable
private data class FeedColors(val link: Color, val codeBackground: Color)

private data class FeedTextKey(val text: String, val colors: FeedColors, val timestamps: Boolean)

/** Text this long or shorter is parsed during composition; it's quick, and nothing pops in. */
private const val PARSE_NOW_CHARS = 2_000

/** Recently parsed feed text, so opening it again is instant. */
private val parsedFeedText = LruCache<FeedTextKey, List<StyledBlock>>(32)

/**
 * Long text is parsed off the main thread, so a page opening with it doesn't drop frames as it
 * slides in; it's null until then. Short text, and text parsed before, is ready right away.
 */
@Composable
private fun rememberFeedBlocks(text: String, timestamps: Boolean): List<StyledBlock>? {
    val colors = FeedColors(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.surfaceVariant)
    val key = FeedTextKey(text, colors, timestamps)
    var parsed by remember(key) {
        val cached = parsedFeedText.get(key)
        mutableStateOf(cached ?: if (text.length <= PARSE_NOW_CHARS) parseFeedText(key) else null)
    }
    LaunchedEffect(key) {
        if (parsed == null) parsed = withContext(Dispatchers.Default) { parseFeedText(key) }
    }
    return parsed
}

private fun parseFeedText(key: FeedTextKey): List<StyledBlock> {
    val links = TextLinkStyles(SpanStyle(color = key.colors.link, textDecoration = TextDecoration.Underline))
    // Timestamps look different from web links: bold, not underlined.
    val seeks = TextLinkStyles(SpanStyle(color = key.colors.link, fontWeight = FontWeight.Bold))
    val result = parseFeedHtml(key.text).map { block ->
        val inline = when (block) {
            is FeedBlock.Paragraph -> block.text
            is FeedBlock.Heading -> block.text
            is FeedBlock.ListItem -> block.text
            is FeedBlock.Code -> Inline(block.text)
            is FeedBlock.Rule -> Inline("")
        }
        val stamps = key.timestamps && block !is FeedBlock.Code
        StyledBlock(block, styled(inline, key.colors, links, if (stamps) seeks else null))
    }
    parsedFeedText.put(key, result)
    return result
}

private fun styled(inline: Inline, colors: FeedColors, links: TextLinkStyles, seeks: TextLinkStyles?): AnnotatedString {
    val builder = AnnotatedString.Builder(inline.text)
    for (span in inline.spans) {
        when (val style = span.style) {
            is InlineStyle.Link -> builder.addLink(LinkAnnotation.Url(style.url, links), span.start, span.end)
            InlineStyle.Bold -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), span.start, span.end)
            InlineStyle.Italic -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
            InlineStyle.Underline -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), span.start, span.end)
            InlineStyle.Strike -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), span.start, span.end)
            InlineStyle.Code -> builder.addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.93.em, background = colors.codeBackground),
                span.start,
                span.end,
            )
        }
    }
    if (seeks != null) {
        val linked = inline.spans.filter { it.style is InlineStyle.Link }
        for (timestamp in findTimestamps(inline.text)) {
            // A time that's already part of a link stays that link.
            if (linked.any { it.start < timestamp.end && timestamp.start < it.end }) continue
            builder.addLink(LinkAnnotation.Url(seekUri(timestamp.ms), seeks), timestamp.start, timestamp.end)
        }
    }
    return builder.toAnnotatedString()
}

private val ListIndent = 24.dp
private val QuoteIndent = 15.dp
private val QuoteBar = 3.dp

/** Space above [block], after [previous]. */
private fun gapBefore(previous: FeedBlock?, block: FeedBlock): Dp = when {
    previous == null -> 0.dp
    block is FeedBlock.Heading -> 20.dp
    previous is FeedBlock.Heading -> 6.dp
    block is FeedBlock.Rule || previous is FeedBlock.Rule -> 16.dp
    block is FeedBlock.ListItem && previous is FeedBlock.ListItem -> 4.dp
    else -> 12.dp
}

@Composable
private fun FeedBlocks(blocks: List<StyledBlock>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
    val soft = colors.onSurfaceVariant
    val bar = soft.copy(alpha = 0.3f)
    Column(modifier) {
        blocks.forEachIndexed { index, (block, text) ->
            val previous = blocks.getOrNull(index - 1)?.block
            val gap = gapBefore(previous, block)
            // A quote's bar runs on through the gap between its blocks.
            val sharedQuote = minOf(previous?.quote ?: 0, block.quote)
            val color = if (block.quote > 0) soft else Color.Unspecified
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        for (level in 1..block.quote) {
                            val top = if (level <= sharedQuote) 0f else gap.toPx()
                            drawRect(bar, Offset((level - 1) * QuoteIndent.toPx(), top), Size(QuoteBar.toPx(), size.height - top))
                        }
                    }
                    .padding(top = gap, start = QuoteIndent * block.quote),
            ) {
                when (block) {
                    is FeedBlock.Paragraph -> Text(text, style = body, color = color)
                    is FeedBlock.Heading -> Text(
                        text,
                        style = if (block.level <= 3) body.copy(fontSize = 17.sp, lineHeight = 24.sp) else body,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    is FeedBlock.ListItem -> Row(Modifier.padding(start = ListIndent * block.depth)) {
                        Text(block.marker.orEmpty(), style = body, color = soft, modifier = Modifier.width(ListIndent))
                        Text(text, style = body, color = color)
                    }
                    is FeedBlock.Code -> Text(
                        text,
                        style = body.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp),
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.podcastColors.raised, PaperShape)
                            .border(1.dp, colors.outlineVariant, PaperShape)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    is FeedBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
                }
            }
        }
    }
}

/** [content] cut to [height] under a fade while it's taller, with a More / Less toggle. */
@Composable
private fun Collapsible(height: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val background = MaterialTheme.colorScheme.background
    Column(modifier) {
        Box(
            Modifier
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    if (overflows && !expanded) {
                        val fade = 44.dp.toPx()
                        drawRect(
                            Brush.verticalGradient(listOf(background.copy(alpha = 0f), background), startY = size.height - fade, endY = size.height),
                            topLeft = Offset(0f, size.height - fade),
                            size = Size(size.width, fade),
                        )
                    }
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                    val limit = height.roundToPx()
                    overflows = placeable.height > limit
                    val shown = if (expanded) placeable.height else minOf(placeable.height, limit)
                    layout(placeable.width, shown) { placeable.place(0, 0) }
                },
        ) { content() }
        if (overflows) {
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Show less" else "Show more") { expanded = !expanded },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    if (expanded) "Less" else "More",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}
