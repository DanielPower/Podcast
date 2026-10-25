package ca.danielpower.podcast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.Chapter

/**
 * A progress bar split into one segment per chapter, sized by chapter length. With [onSeek] it can
 * be tapped or dragged to seek; without it, it only shows progress.
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

    Canvas(modifier.fillMaxWidth().height(height).then(gestures)) {
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
