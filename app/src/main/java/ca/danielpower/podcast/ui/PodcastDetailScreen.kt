package ca.danielpower.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.CatchUpProgress
import ca.danielpower.podcast.data.DirectoryResult
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.data.FundingLink
import ca.danielpower.podcast.data.Podcast
import ca.danielpower.podcast.data.joinArtwork
import ca.danielpower.podcast.playback.PlayerUiState

/**
 * A podcast's page, subscribed or not. Opened from search, [podcast] is null until its feed has
 * loaded, and [preview] fills the header meanwhile.
 */
@Composable
fun PodcastDetailScreen(
    podcast: Podcast?,
    preview: DirectoryResult?,
    episodes: List<EpisodeWithPodcast>,
    catchUp: CatchUpProgress?,
    error: String?,
    onRetry: () -> Unit,
    onSubscribe: (catchUp: Boolean) -> Unit,
    onUnsubscribe: () -> Unit,
    onStartCatchUp: () -> Unit,
    onStopCatchUp: () -> Unit,
    onAddToQueue: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    /** The player, for marking the episode loaded in it; [onPause] and [onResume] act on that one. */
    playerState: PlayerUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpenEpisode: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var confirmUnsubscribe by remember { mutableStateOf(false) }
    var choosingStart by remember { mutableStateOf(false) }
    val title = podcast?.title ?: preview?.title.orEmpty()
    // Catching up goes oldest first, so the episodes read in the order they'll be played.
    val shown = remember(episodes, catchUp != null) { if (catchUp != null) episodes.asReversed() else episodes }
    val upNextId = remember(shown, catchUp) {
        catchUp?.let { progress ->
            shown.dropWhile { it.episode.id != progress.episodeId }.drop(1).firstOrNull { !it.episode.played }?.episode?.id
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(onBack) { PageTitle(title) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (podcast != null || preview != null) {
                item {
                    Header(
                        title = title,
                        author = podcast?.author ?: preview?.author,
                        // The directory's copy of the artwork is the last resort.
                        artworkUrl = joinArtwork(podcast?.artworkUrl, preview?.artworkUrl),
                        description = podcast?.description,
                        funding = podcast?.funding.orEmpty(),
                        subscribed = podcast?.subscribed,
                        catchUp = catchUp,
                        onSubscribe = { choosingStart = true },
                        onUnsubscribe = { confirmUnsubscribe = true },
                        onStartCatchUp = onStartCatchUp,
                        onStopCatchUp = onStopCatchUp,
                    )
                }
            }
            when {
                podcast != null -> items(shown, key = { it.episode.id }) { item ->
                    EpisodeRow(
                        item,
                        catchUpMark = when (item.episode.id) {
                            catchUp?.episodeId -> CatchUpMark.IN_QUEUE
                            upNextId -> CatchUpMark.UP_NEXT
                            else -> null
                        },
                        player = playerState.takeIf { it.episodeId == item.episode.id },
                        onClick = { onOpenEpisode(item.episode.id) },
                        onAddToQueue = { onAddToQueue(item.episode.id) },
                        onPlay = { onPlay(item.episode.id) },
                        onPause = onPause,
                        onResume = onResume,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = SideMargin), color = MaterialTheme.colorScheme.outlineVariant)
                }
                error != null -> item {
                    Column(Modifier.fillMaxWidth().padding(SideMargin), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        SecondaryButton(onClick = onRetry) { Text("Try again") }
                    }
                }
                else -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }

    if (choosingStart) {
        SubscribeDialog(
            onSubscribe = { catchUp ->
                choosingStart = false
                onSubscribe(catchUp)
            },
            onDismiss = { choosingStart = false },
        )
    }

    if (confirmUnsubscribe) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            title = { Text("Unsubscribe?") },
            text = { Text("Its episodes will be removed from your queue and deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsubscribe = false
                    onUnsubscribe()
                }) { Text("Unsubscribe", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsubscribe = false }) { Text("Cancel") }
            },
        )
    }
}

/** [subscribed] is null while the feed is loading, which disables the button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    title: String,
    author: String?,
    artworkUrl: String?,
    description: String?,
    funding: List<FundingLink>,
    subscribed: Boolean?,
    catchUp: CatchUpProgress?,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit,
    onStartCatchUp: () -> Unit,
    onStopCatchUp: () -> Unit,
) {
    // The title, author and button fill the artwork's height, with the button level with its bottom.
    Row(Modifier.padding(start = SideMargin, end = SideMargin, top = 8.dp)) {
        Artwork(artworkUrl, HeaderArtSize)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.height(HeaderArtSize), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                author?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Subscribing asks where to start; unsubscribing asks first, as it deletes downloads.
            if (subscribed == true) {
                SecondaryButton(onClick = onUnsubscribe) { Text("Unsubscribe") }
            } else {
                PrimaryButton(onClick = onSubscribe, enabled = subscribed != null) { Text("Subscribe") }
            }
        }
    }
    if (!description.isNullOrBlank()) {
        FeedText(
            description,
            Modifier.fillMaxWidth().padding(start = SideMargin, end = SideMargin, top = 12.dp),
            collapsedHeight = 110.dp,
        )
    }
    if (funding.isNotEmpty()) {
        val uriHandler = LocalUriHandler.current
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = SideMargin, end = SideMargin, top = 12.dp),
        ) {
            for (link in funding) FundingChip(link.label, onClick = { uriHandler.openUri(link.url) })
        }
    }
    val catchUpModifier = Modifier.padding(start = SideMargin, end = SideMargin, top = 12.dp)
    when {
        catchUp != null -> CatchUpBanner(catchUp, onStop = onStopCatchUp, modifier = catchUpModifier)
        subscribed == true -> StartCatchUpButton(onClick = onStartCatchUp, modifier = catchUpModifier)
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel(
        "Episodes",
        Modifier.padding(horizontal = SideMargin, vertical = 6.dp),
        trailing = "Oldest first".takeIf { catchUp != null },
    )
}

/** A `<podcast:funding>` link, opened in the browser. */
@Composable
private fun FundingChip(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(34.dp)
            .clip(PaperShape)
            .border(1.dp, colors.outlineVariant, PaperShape)
            .background(MaterialTheme.podcastColors.raised)
            .clickable(role = Role.Button, onClickLabel = "Open in browser", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun EpisodeRow(
    item: EpisodeWithPodcast,
    catchUpMark: CatchUpMark?,
    /** Set when this is the episode loaded in the player. */
    player: PlayerUiState?,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val episode = item.episode
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (player != null) colors.primaryContainer else colors.surface)
            .clickable(onClick = onClick)
            .padding(start = SideMargin, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                episode.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (episode.played) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val details = listOfNotNull(
                formatDate(episode.publishedAt).takeIf { it.isNotEmpty() },
                // The one in the player shows what's left of it instead of its length.
                if (player != null && player.durationMs > 0) {
                    "${formatMinutes((player.durationMs - player.positionMs).coerceAtLeast(0))} left"
                } else {
                    episode.durationMs?.let(::formatDuration)
                },
                "Played".takeIf { episode.played && player == null },
            ).joinToString(" · ")
            Text(
                when {
                    player != null -> buildAnnotatedString {
                        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                            append(if (player.isPlaying) "Playing" else "Paused")
                        }
                        append(" · ")
                        append(details)
                    }
                    catchUpMark == null -> AnnotatedString(details)
                    // The episode catching up is on stands out; the one after it is only named.
                    catchUpMark == CatchUpMark.IN_QUEUE -> buildAnnotatedString {
                        append(details)
                        append(" · ")
                        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append("In queue") }
                    }
                    else -> AnnotatedString("$details · Up next")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            player?.isPlaying == true -> IconButton(onClick = onPause) {
                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = accent)
            }
            player != null -> IconButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = accent)
            }
            // Jumps to the front of the queue and plays now, streaming until the download finishes.
            else -> IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play now")
            }
        }
        when {
            episode.downloadState == DownloadState.QUEUED || episode.downloadState == DownloadState.DOWNLOADING ->
                IconButton(onClick = {}, enabled = false) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.tertiary, strokeWidth = 2.dp)
                }
            episode.queuePosition != null && episode.downloadState != DownloadState.FAILED ->
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = "In queue")
                }
            else -> IconButton(onClick = onAddToQueue) {
                if (episode.downloadState == DownloadState.FAILED) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = "Download failed, retry", tint = MaterialTheme.colorScheme.error)
                } else {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to queue")
                }
            }
        }
    }
}

/** Where an episode stands while its podcast catches up. */
private enum class CatchUpMark { IN_QUEUE, UP_NEXT }

private val HeaderArtSize = 120.dp
