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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.Chapter
import ca.danielpower.podcast.data.DownloadProgress
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.data.Episode
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.playback.PlayerUiState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    queue: List<EpisodeWithPodcast>?,
    downloadProgress: Map<Long, DownloadProgress.Bytes>,
    playerState: PlayerUiState,
    playingChapters: List<Chapter>,
    onOpen: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onSwitchTo: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Queue", Icons.AutoMirrored.Filled.QueueMusic) {
            if (!queue.isNullOrEmpty()) {
                Text(
                    queueSummary(queue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Outlined.History, contentDescription = "History")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                queue == null -> Unit
                queue.isEmpty() -> EmptyMessage(
                    "Your queue is empty.\nNew episodes from your podcasts are added here once they've downloaded.",
                )
                else -> QueueList(queue, downloadProgress, playerState, playingChapters, onOpen, onReorder, onSwitchTo, onDismiss, onRetryDownload)
            }
        }
    }
}

private fun queueSummary(queue: List<EpisodeWithPodcast>): String {
    val count = if (queue.size == 1) "1 episode" else "${queue.size} episodes"
    val remaining = queue.sumOf { item ->
        val e = item.episode
        ((e.durationMs ?: 0) - e.positionMs).coerceAtLeast(0)
    }
    return if (remaining > 0) "$count · ${formatMinutes(remaining)}" else count
}

@Composable
private fun QueueList(
    queue: List<EpisodeWithPodcast>,
    downloadProgress: Map<Long, DownloadProgress.Bytes>,
    playerState: PlayerUiState,
    playingChapters: List<Chapter>,
    onOpen: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onSwitchTo: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit,
) {
    // A local copy that follows the drag; written back to the database when the drag ends.
    var items by remember { mutableStateOf(queue) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(queue) { if (!dragging) items = queue }

    val listState = rememberLazyListState()
    // The list also holds section labels, so moves are matched by key rather than by index.
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = items.indexOfFirst { it.episode.id == from.key }
        val toIndex = items.indexOfFirst { it.episode.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            items = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }
    val playingId = playerState.episodeId
    val playingFirst = items.firstOrNull()?.episode?.id == playingId && playingId != null

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items.forEachIndexed { index, item ->
            when {
                index == 0 && playingFirst -> item(key = "label-playing") { QueueLabel("Now playing") }
                index == 0 || (index == 1 && playingFirst) -> item(key = "label-next") {
                    if (index == 1) HorizontalDivider(Modifier.padding(horizontal = SideMargin), color = MaterialTheme.colorScheme.outlineVariant)
                    QueueLabel("Up next")
                }
            }
            item(key = item.episode.id) {
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
                                progress = downloadProgress[item.episode.id],
                                playing = item.episode.id == playingId,
                                playerState = playerState,
                                chapters = playingChapters,
                                onClick = {
                                    if (item.episode.downloadState == DownloadState.FAILED) {
                                        onRetryDownload(item.episode.id)
                                    } else {
                                        onOpen(item.episode.id)
                                    }
                                },
                                handle = {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(
                                            onDragStarted = { dragging = true },
                                            onDragStopped = {
                                                dragging = false
                                                val order = items.map { it.episode.id }
                                                onReorder(order)
                                                // A downloaded episode dropped above the playing one
                                                // takes over from it.
                                                val playing = playerState.episodeId
                                                val id = item.episode.id
                                                if (playing != null && id != playing && item.isPlayable &&
                                                    playing in order && order.indexOf(id) < order.indexOf(playing)
                                                ) {
                                                    onSwitchTo(id)
                                                }
                                            },
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Reorder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueLabel(text: String) {
    SectionLabel(text, Modifier.padding(start = SideMargin, end = SideMargin, top = 14.dp, bottom = 4.dp))
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
                    .padding(horizontal = SideMargin),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(10.dp))
                Text("Remove", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        content()
    }
}

@Composable
private fun QueueRow(
    item: EpisodeWithPodcast,
    progress: DownloadProgress.Bytes?,
    playing: Boolean,
    playerState: PlayerUiState,
    chapters: List<Chapter>,
    onClick: () -> Unit,
    handle: @Composable () -> Unit,
) {
    val episode = item.episode
    val failed = episode.downloadState == DownloadState.FAILED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = SideMargin, top = 10.dp, bottom = 10.dp, end = 4.dp),
    ) {
        Artwork(item.artworkUrl, if (playing) 60.dp else 52.dp, alpha = if (item.isPlayable) 1f else 0.55f)
        Spacer(Modifier.width(14.dp))
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
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                playing -> PlayingStatus(playerState, chapters)
                !item.isPlayable && !failed -> {
                    DownloadBar(episode, progress)
                    Text(
                        downloadStatus(episode, progress),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                else -> {
                    val duration = episode.durationMs
                    if (!failed && duration != null && duration > 0 && episode.positionMs > 0) {
                        ThinBar(
                            episode.positionMs.toFloat() / duration,
                            MaterialTheme.colorScheme.onSurface,
                            Modifier.width(120.dp).padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                    Text(
                        if (failed) downloadStatus(episode, null) else episodeSubtitle(item),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        handle()
    }
}

/** The playing row's chapter-segmented progress, with which chapter it's in and the time left. */
@Composable
private fun PlayingStatus(playerState: PlayerUiState, chapters: List<Chapter>) {
    val position = playerState.positionMs
    val duration = playerState.durationMs
    ChapterSeekBar(
        chapters = chapters,
        positionMs = position,
        durationMs = duration,
        barHeight = 4.dp,
        height = 4.dp,
        gap = 2.dp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, end = 8.dp),
    )
    Row(Modifier.fillMaxWidth().padding(end = 8.dp)) {
        val chapter = chapters.chapterAt(position)
        Text(
            if (chapter != null) "Playing · Chapter ${chapters.indexOf(chapter) + 1} of ${chapters.size}" else "Playing",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (duration > 0) {
            Text(
                "${formatMinutes(duration - position)} left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadBar(episode: Episode, progress: DownloadProgress.Bytes?) {
    val total = progress?.total
    val modifier = Modifier.width(120.dp).padding(top = 4.dp, bottom = 2.dp)
    if (episode.downloadState == DownloadState.DOWNLOADING && progress != null && total != null && total > 0) {
        ThinBar(progress.downloaded.toFloat() / total, MaterialTheme.colorScheme.tertiary, modifier)
    } else {
        LinearProgressIndicator(
            modifier = modifier.height(3.dp),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.podcastColors.track,
            gapSize = 0.dp,
        )
    }
}

private fun downloadStatus(episode: Episode, progress: DownloadProgress.Bytes?): String = when (episode.downloadState) {
    DownloadState.DOWNLOADING -> {
        val total = progress?.total
        when {
            progress == null -> "Downloading"
            total != null && total > 0 -> "Downloading · ${progress.downloaded * 100 / total}%"
            else -> "Downloading · ${progress.downloaded / (1024 * 1024)} MB"
        }
    }
    DownloadState.FAILED -> "Download failed. Tap to retry."
    else -> "Waiting to download"
}

private fun episodeSubtitle(item: EpisodeWithPodcast): String {
    val episode = item.episode
    val parts = mutableListOf<String>()
    episode.durationMs?.let { duration ->
        parts += if (episode.positionMs > 0) {
            "${formatMinutes((duration - episode.positionMs).coerceAtLeast(0))} left of ${formatMinutes(duration)}"
        } else {
            formatMinutes(duration)
        }
    }
    formatDate(episode.publishedAt).takeIf { it.isNotEmpty() }?.let(parts::add)
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
