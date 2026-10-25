package ca.danielpower.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.data.Episode
import ca.danielpower.podcast.data.EpisodeWithPodcast
import coil.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queue: List<EpisodeWithPodcast>?,
    playingEpisodeId: Long?,
    onPlay: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onDismiss: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Queue") }) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                queue == null -> Unit
                queue.isEmpty() -> EmptyMessage(
                    "Your queue is empty.\nNew episodes from your podcasts are added here once they've downloaded.",
                )
                else -> QueueList(queue, playingEpisodeId, onPlay, onReorder, onDismiss, onRetryDownload)
            }
        }
    }
}

@Composable
private fun QueueList(
    queue: List<EpisodeWithPodcast>,
    playingEpisodeId: Long?,
    onPlay: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onDismiss: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit,
) {
    // A local copy that follows the drag; written back to the database when the drag ends.
    var items by remember { mutableStateOf(queue) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(queue) { if (!dragging) items = queue }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.episode.id }) { item ->
            ReorderableItem(reorderState, key = item.episode.id) { isDragging ->
                DismissibleRow(
                    onDismiss = {
                        items = items.filter { it.episode.id != item.episode.id }
                        onDismiss(item.episode.id)
                    },
                ) {
                    Surface(shadowElevation = if (isDragging) 8.dp else 0.dp) {
                        QueueRow(
                            item = item,
                            playing = item.episode.id == playingEpisodeId,
                            onClick = {
                                when {
                                    item.isPlayable -> onPlay(item.episode.id)
                                    item.episode.downloadState == DownloadState.FAILED -> onRetryDownload(item.episode.id)
                                }
                            },
                            handle = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = { dragging = true },
                                        onDragStopped = {
                                            dragging = false
                                            onReorder(items.map { it.episode.id })
                                        },
                                    ),
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = "Reorder")
                                }
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleRow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.StartToEnd) onDismiss()
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
            ) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        content()
    }
}

@Composable
private fun QueueRow(
    item: EpisodeWithPodcast,
    playing: Boolean,
    onClick: () -> Unit,
    handle: @Composable () -> Unit,
) {
    val episode = item.episode
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
    ) {
        AsyncImage(
            model = item.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (item.isPlayable) 1f else 0.5f,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.podcast.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                episode.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing) FontWeight.SemiBold else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Now playing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (item.isPlayable) episodeSubtitle(item) else downloadStatus(item.episode),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.episode.downloadState == DownloadState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (!item.isPlayable && item.episode.downloadState != DownloadState.FAILED) {
                DownloadProgress(item.episode)
            }
        }
        handle()
    }
}

@Composable
private fun DownloadProgress(episode: Episode) {
    val total = episode.downloadTotalBytes
    val modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp)
    if (episode.downloadState == DownloadState.DOWNLOADING && total != null && total > 0) {
        LinearProgressIndicator(
            progress = { (episode.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
            modifier = modifier,
        )
    } else {
        LinearProgressIndicator(modifier = modifier)
    }
}

private fun downloadStatus(episode: Episode): String = when (episode.downloadState) {
    DownloadState.DOWNLOADING -> {
        val total = episode.downloadTotalBytes
        if (total != null && total > 0) {
            "Downloading ${episode.downloadedBytes * 100 / total}%"
        } else {
            "Downloading ${episode.downloadedBytes / (1024 * 1024)} MB"
        }
    }
    DownloadState.FAILED -> "Download failed. Tap to retry."
    else -> "Waiting to download"
}

private fun episodeSubtitle(item: EpisodeWithPodcast): String {
    val episode = item.episode
    val parts = mutableListOf<String>()
    formatDate(episode.publishedAt).takeIf { it.isNotEmpty() }?.let(parts::add)
    episode.durationMs?.let { duration ->
        parts += if (episode.positionMs > 0) {
            "${formatDuration((duration - episode.positionMs).coerceAtLeast(0))} left"
        } else {
            formatDuration(duration)
        }
    }
    return parts.joinToString(" · ")
}

@Composable
fun EmptyMessage(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.size(16.dp))
            action()
        }
    }
}
