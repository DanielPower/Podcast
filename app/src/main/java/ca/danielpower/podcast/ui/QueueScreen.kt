package ca.danielpower.podcast.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.CatchUpProgress
import ca.danielpower.podcast.data.Chapter
import ca.danielpower.podcast.data.DownloadProgress
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.data.Episode
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.playback.PlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    queue: List<EpisodeWithPodcast>?,
    /** How far each podcast catching up has got, by the episode it's up to. */
    catchUps: Map<Long, CatchUpProgress>,
    downloadProgress: Map<Long, DownloadProgress.Bytes>,
    playerState: PlayerUiState,
    playingChapters: List<Chapter>,
    onOpen: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onSwitchTo: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onUndoDismiss: (Dismissed) -> Unit,
    onRetryDownload: (Long) -> Unit,
    waitingForWifi: Boolean,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // A swipe is instant, so it can be taken back for a few seconds.
    val dismiss = { dismissed: Dismissed ->
        onDismiss(dismissed.episodeId)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                "Removed \u201c${dismissed.title}\u201d",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndoDismiss(dismissed)
        }
        Unit
    }
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
                else -> QueueList(
                    queue, catchUps, downloadProgress, playerState, playingChapters, waitingForWifi,
                    onOpen, onReorder, onSwitchTo, dismiss, onRetryDownload,
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** An episode swiped out of the queue, with what's needed to put it back. */
data class Dismissed(val episodeId: Long, val title: String, val queuePosition: Long, val wasPlaying: Boolean)

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
    catchUps: Map<Long, CatchUpProgress>,
    downloadProgress: Map<Long, DownloadProgress.Bytes>,
    playerState: PlayerUiState,
    playingChapters: List<Chapter>,
    waitingForWifi: Boolean,
    onOpen: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onSwitchTo: (Long) -> Unit,
    onDismiss: (Dismissed) -> Unit,
    onRetryDownload: (Long) -> Unit,
) {
    // A local copy that follows the drag; written back to the database when the drag ends.
    var items by remember { mutableStateOf(queue) }
    var dragging by remember { mutableStateOf(false) }
    // The episode being dragged, and one dropped above the playing episode that is still taking over.
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var takingOverId by remember { mutableStateOf<Long?>(null) }
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
    LaunchedEffect(playingId) { if (playingId == takingOverId) takingOverId = null }
    // If the player never switches (no controller, say), stop showing the dropped episode as playing.
    LaunchedEffect(takingOverId) {
        if (takingOverId != null) {
            delay(2_000)
            takingOverId = null
        }
    }
    // The labels and rows show where the drag will leave things: a playable episode dragged above
    // the playing one is shown as playing before it's dropped and takes over, so nothing reflows on drop.
    val shownPlayingId = takingOverId ?: draggedId?.takeIf { id ->
        takesOver(items, id, playingId)
    } ?: playingId
    val playingFirst = items.firstOrNull()?.episode?.id == shownPlayingId && shownPlayingId != null

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items.forEachIndexed { index, item ->
            // The top label keeps one key whatever it says. The list stays anchored to its first
            // visible key, so a new key appearing above it (as when a dragged episode takes over
            // playback and "Up next" becomes "Now playing") would scroll the top row out of view.
            when {
                index == 0 -> item(key = "label-top") { QueueLabel(if (playingFirst) "Now playing" else "Up next") }
                index == 1 && playingFirst -> item(key = "label-next") {
                    HorizontalDivider(Modifier.padding(horizontal = SideMargin), color = MaterialTheme.colorScheme.outlineVariant)
                    QueueLabel("Up next")
                }
            }
            item(key = item.episode.id) {
                ReorderableItem(reorderState, key = item.episode.id) { isDragging ->
                    DismissibleRow(
                        onDismiss = {
                            items = items.filter { it.episode.id != item.episode.id }
                            onDismiss(
                                Dismissed(
                                    episodeId = item.episode.id,
                                    title = item.episode.title,
                                    queuePosition = item.episode.queuePosition ?: 0,
                                    wasPlaying = item.episode.id == playingId,
                                ),
                            )
                        },
                    ) {
                        Surface(shadowElevation = if (isDragging) 8.dp else 0.dp) {
                            QueueRow(
                                item = item,
                                catchUp = catchUps[item.episode.id],
                                progress = downloadProgress[item.episode.id],
                                waitingForWifi = waitingForWifi,
                                playing = item.episode.id == shownPlayingId,
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
                                            onDragStarted = {
                                                dragging = true
                                                draggedId = item.episode.id
                                            },
                                            onDragStopped = {
                                                dragging = false
                                                draggedId = null
                                                onReorder(items.map { it.episode.id })
                                                // A downloaded episode dropped above the playing one
                                                // takes over from it.
                                                val id = item.episode.id
                                                if (takesOver(items, id, playerState.episodeId)) {
                                                    takingOverId = id
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

/** Whether episode [id] sits above the playing episode in [items] and can play, so dropping it there switches to it. */
private fun takesOver(items: List<EpisodeWithPodcast>, id: Long, playingId: Long?): Boolean {
    if (playingId == null || id == playingId) return false
    val index = items.indexOfFirst { it.episode.id == id }
    val playingIndex = items.indexOfFirst { it.episode.id == playingId }
    return index >= 0 && playingIndex >= 0 && index < playingIndex && items[index].isPlayable
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

/** How long a row takes to grow into, or shrink out of, its playing layout. */
private const val RowChangeMs = 250

@Composable
private fun QueueRow(
    item: EpisodeWithPodcast,
    catchUp: CatchUpProgress?,
    progress: DownloadProgress.Bytes?,
    waitingForWifi: Boolean,
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
        val artSize by animateDpAsState(if (playing) 60.dp else 52.dp, tween(RowChangeMs), label = "artwork")
        Artwork(item.artworkUrl, artSize, alpha = if (item.isPlayable) 1f else 0.55f)
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
            // The status grows into the seek bar (or shrinks back) when a row starts or stops playing.
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    fadeIn(tween(RowChangeMs / 2, delayMillis = RowChangeMs / 2)) togetherWith
                        fadeOut(tween(RowChangeMs / 2)) using SizeTransform(clip = false) { _, _ -> tween(RowChangeMs) }
                },
                label = "status",
                modifier = Modifier.fillMaxWidth(),
            ) { shownPlaying ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    when {
                        // A row shown as playing ahead of the player switching to it shows its saved position.
                        shownPlaying && episode.id == playerState.episodeId ->
                            PlayingStatus(playerState.positionMs, playerState.durationMs, chapters)
                        shownPlaying -> PlayingStatus(episode.positionMs, episode.durationMs ?: 0L, emptyList())
                        !item.isPlayable && !failed -> {
                            // Waiting for Wi-Fi could take hours, so no busy animation for it.
                            val wifi = waitingForWifi && episode.downloadState == DownloadState.QUEUED
                            if (wifi) {
                                ThinBar(0f, MaterialTheme.colorScheme.tertiary, Modifier.width(120.dp).padding(top = 4.dp, bottom = 2.dp))
                            } else {
                                DownloadBar(episode, progress)
                            }
                            Text(
                                if (wifi) "Waiting for Wi-Fi" else downloadStatus(episode, progress),
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
                            if (!failed && catchUp != null) {
                                // More of this podcast follows once it's done, so the row says so.
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(CatchUpIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text(
                                        "Catching up · ${catchUp.number} of ${catchUp.total} · ${episodeSubtitle(item)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Text(
                                    if (failed) downloadStatus(episode, null) else episodeSubtitle(item),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        handle()
    }
}

/** The playing row's chapter-segmented progress, with which chapter it's in and the time left. */
@Composable
private fun PlayingStatus(position: Long, duration: Long, chapters: List<Chapter>) {
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
