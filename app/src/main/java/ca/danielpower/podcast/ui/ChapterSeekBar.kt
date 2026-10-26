package ca.danielpower.podcast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.Chapter

/**
 * A progress bar split into one segment per chapter, sized by chapter length. With [onSeek] it can
 * be tapped or dragged to seek; without it, it only shows progress. While dragging, a tooltip
 * above the finger previews the time and chapter it will seek to.
 */
@Composable
fun ChapterSeekBar(
    chapters: List<Chapter>,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    barHeight: Dp = 6.dp,
    height: Dp = 24.dp,
    gap: Dp = 3.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onSeek: ((Long) -> Unit)? = null,
) {
    // While dragging, show the drag position instead of the player's.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val starts = chapterStarts(chapters, durationMs)

    var gestures: Modifier = Modifier
    if (onSeek != null && durationMs > 0) {
        gestures = Modifier
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f).times(durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { onSeek((it * durationMs).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            }
    }

    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.matchParentSize().then(gestures)) {
            val total = durationMs.coerceAtLeast(1).toFloat()
            val progress = dragFraction ?: (positionMs / total).coerceIn(0f, 1f)
            val gapPx = gap.toPx()
            val h = barHeight.toPx()
            val top = (size.height - h) / 2
            val radius = CornerRadius(h / 2, h / 2)
            val usable = size.width - gapPx * (starts.size - 1)

            var x = 0f
            starts.forEachIndexed { i, start ->
                val end = starts.getOrNull(i + 1) ?: durationMs
                val startFraction = start / total
                val endFraction = end / total
                val width = (endFraction - startFraction).coerceAtLeast(0f) * usable
                drawRoundRect(trackColor, Offset(x, top), Size(width, h), radius)
                val filled = ((progress - startFraction) / (endFraction - startFraction).coerceAtLeast(1e-6f))
                    .coerceIn(0f, 1f)
                if (filled > 0f) {
                    drawRoundRect(activeColor, Offset(x, top), Size(width * filled, h), radius)
                }
                x += width + gapPx
            }
        }
        dragFraction?.let { fraction ->
            val previewMs = (fraction * durationMs).toLong()
            SeekPreview(previewMs, chapters.chapterAt(previewMs)?.title, fraction, barTop = (height - barHeight) / 2)
        }
    }
}

/**
 * The drag tooltip, centred over [fraction] of the bar's width and sitting just above the bar. It
 * takes no space, so the pane around the bar keeps its size.
 */
@Composable
private fun SeekPreview(positionMs: Long, chapterTitle: String?, fraction: Float, barTop: Dp) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = if (chapterTitle.isNullOrBlank()) formatDuration(positionMs) else "${formatDuration(positionMs)} · $chapterTitle",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = colors.inverseOnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
                val x = (fraction * width - placeable.width / 2f).toInt()
                    .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                val y = barTop.roundToPx() - 6.dp.roundToPx() - placeable.height
                layout(0, 0) { placeable.place(x, y) }
            }
            .widthIn(max = 260.dp)
            .background(colors.inverseSurface, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Chapter start times usable for drawing: sorted, within the episode, starting at zero. */
private fun chapterStarts(chapters: List<Chapter>, durationMs: Long): List<Long> {
    val starts = chapters.map { it.startMs }
        .filter { durationMs <= 0 || it < durationMs }
        .distinct()
        .sorted()
    if (starts.isEmpty() || durationMs <= 0) return listOf(0L)
    return if (starts.first() > 0) listOf(0L) + starts else starts
}

/** The chapter playing at [positionMs], if any. */
fun List<Chapter>.chapterAt(positionMs: Long): Chapter? = lastOrNull { it.startMs <= positionMs }
