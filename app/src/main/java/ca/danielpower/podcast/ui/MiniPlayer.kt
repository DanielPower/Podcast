package ca.danielpower.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import ca.danielpower.podcast.data.Chapter
import ca.danielpower.podcast.playback.PlayerUiState

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    player: Player?,
    chapters: List<Chapter>,
    skipBackSeconds: Int,
    skipForwardSeconds: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (player == null || state.episodeId == null) return
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.podcastColors.raised)
            .clickable(onClick = onClick),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ChapterSeekBar(
            chapters = chapters,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            barHeight = 3.dp,
            height = 3.dp,
            gap = 2.dp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Artwork(state.artworkUri, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val chapter = chapters.chapterAt(state.positionMs)
                Text(
                    if (chapter != null && chapter.title.isNotEmpty()) {
                        "Ch. ${chapters.indexOf(chapter) + 1} · ${chapter.title}"
                    } else {
                        buildString {
                            append(formatDuration(state.positionMs))
                            if (state.durationMs > 0) append(" / ").append(formatDuration(state.durationMs))
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { player.seekBack() }) {
                SkipIcon(skipBackSeconds, forward = false)
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(PaperShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { if (state.isPlaying) player.pause() else player.play() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            IconButton(onClick = { player.seekForward() }) {
                SkipIcon(skipForwardSeconds, forward = true)
            }
        }
    }
}
