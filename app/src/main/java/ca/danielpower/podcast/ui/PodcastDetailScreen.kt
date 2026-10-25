package ca.danielpower.podcast.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.DirectoryResult
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.data.Podcast

/**
 * A podcast's page, subscribed or not. Opened from search, [podcast] is null until its feed has
 * loaded, and [preview] fills the header meanwhile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    podcast: Podcast?,
    preview: DirectoryResult?,
    episodes: List<EpisodeWithPodcast>,
    error: String?,
    onRetry: () -> Unit,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit,
    onAddToQueue: (Long) -> Unit,
    onOpenEpisode: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var confirmUnsubscribe by remember { mutableStateOf(false) }
    val title = podcast?.title ?: preview?.title.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (podcast != null || preview != null) {
                item {
                    Header(
                        title = title,
                        author = podcast?.author ?: preview?.author,
                        artworkUrl = podcast?.artworkUrl ?: preview?.artworkUrl,
                        description = podcast?.description,
                        subscribed = podcast?.subscribed,
                        onSubscribe = onSubscribe,
                        onUnsubscribe = { confirmUnsubscribe = true },
                    )
                }
            }
            when {
                podcast != null -> items(episodes, key = { it.episode.id }) { item ->
                    EpisodeRow(
                        item,
                        onClick = { onOpenEpisode(item.episode.id) },
                        onAddToQueue = { onAddToQueue(item.episode.id) },
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
@Composable
private fun Header(
    title: String,
    author: String?,
    artworkUrl: String?,
    description: String?,
    subscribed: Boolean?,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit,
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
            // Subscribing is easily undone, so it takes one tap; unsubscribing asks first.
            if (subscribed == true) {
                SecondaryButton(onClick = onUnsubscribe) { Text("Unsubscribe") }
            } else {
                PrimaryButton(onClick = onSubscribe, enabled = subscribed != null) { Text("Subscribe") }
            }
        }
    }
    description?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = SideMargin, end = SideMargin, top = 12.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Episodes", Modifier.padding(horizontal = SideMargin, vertical = 6.dp))
}

@Composable
private fun EpisodeRow(item: EpisodeWithPodcast, onClick: () -> Unit, onAddToQueue: () -> Unit) {
    val episode = item.episode
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = SideMargin, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                episode.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (episode.played) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOfNotNull(
                    formatDate(episode.publishedAt).takeIf { it.isNotEmpty() },
                    episode.durationMs?.let(::formatDuration),
                    "Played".takeIf { episode.played },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

private val HeaderArtSize = 120.dp
