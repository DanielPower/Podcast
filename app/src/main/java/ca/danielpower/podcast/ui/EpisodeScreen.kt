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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import ca.danielpower.podcast.data.Chapter
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.playback.PlayerUiState
import coil.compose.AsyncImage
import java.io.File

private enum class EpisodeTab(val label: String) { Chapters("Chapters"), Notes("Notes"), Details("Details") }

/**
 * One screen for both an episode's page and Now Playing. When the episode is the one loaded in the
 * player, the bottom pane holds the player controls; otherwise it offers Play now / Play next. The
 * pane is the same height either way, so starting playback doesn't move anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeScreen(
    item: EpisodeWithPodcast?,
    chapters: List<Chapter>,
    queueIndex: Int?,
    player: Player?,
    playerState: PlayerUiState,
    onBack: () -> Unit,
    onPlay: (startMs: Long?) -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveFromQueue: () -> Unit,
) {
    val isCurrent = item != null && player != null && playerState.episodeId == item.episode.id
    // Until the user picks a tab, show Chapters if there are any (they load after the screen opens).
    var pickedTab by rememberSaveable { mutableIntStateOf(-1) }
    val tab = if (pickedTab >= 0) pickedTab else if (chapters.isNotEmpty()) 0 else 1
    var menuOpen by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val positionMs = if (isCurrent) playerState.positionMs else item?.episode?.positionMs ?: 0
    val durationMs = if (isCurrent && playerState.durationMs > 0) {
        playerState.durationMs
    } else {
        item?.episode?.durationMs ?: 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isCurrent) "Now playing" else "Episode",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        if (isCurrent) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    item?.episode?.link?.let { link ->
                        IconButton(onClick = { uriHandler.openUri(link) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open episode web page")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (queueIndex == null) {
                            DropdownMenuItem(
                                text = { Text("Add to queue") },
                                onClick = {
                                    menuOpen = false
                                    onAddToQueue()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Remove from queue") },
                                onClick = {
                                    menuOpen = false
                                    onRemoveFromQueue()
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (item != null) {
                BottomPane(
                    item = item,
                    chapters = chapters,
                    queueIndex = queueIndex,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && playerState.isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    player = player,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                )
            }
        },
    ) { padding ->
        if (item == null) return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize()) {
            Header(item)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                EpisodeTab.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { pickedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, EpisodeTab.entries.size),
                        label = { Text(t.label) },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when (EpisodeTab.entries[tab]) {
                    EpisodeTab.Chapters -> ChapterList(
                        chapters = chapters,
                        current = if (isCurrent) chapters.chapterAt(positionMs) else null,
                        onChapter = { chapter ->
                            if (isCurrent) player?.seekTo(chapter.startMs) else onPlay(chapter.startMs)
                        },
                    )
                    EpisodeTab.Notes -> Notes(item.episode.description)
                    EpisodeTab.Details -> Details(item, queueIndex)
                }
            }
        }
    }
}

@Composable
private fun Header(item: EpisodeWithPodcast) {
    val episode = item.episode
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = item.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.podcast.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                episode.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(seasonEpisode(item), formatDate(episode.publishedAt).takeIf { it.isNotEmpty() })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChapterList(chapters: List<Chapter>, current: Chapter?, onChapter: (Chapter) -> Unit) {
    if (chapters.isEmpty()) {
        EmptyMessage("This episode has no chapters.")
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        items(chapters, key = { it.startMs }) { chapter ->
            val selected = chapter == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { onChapter(chapter) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    formatDuration(chapter.startMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    chapter.title.ifEmpty { "Untitled chapter" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Medium else null,
                )
            }
        }
    }
}

@Composable
private fun Notes(description: String?) {
    if (description.isNullOrBlank()) {
        EmptyMessage("This episode has no show notes.")
        return
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(description, linkColor) {
        if ('<' in description) {
            AnnotatedString.fromHtml(
                description,
                linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            )
        } else {
            AnnotatedString(description)
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun Details(item: EpisodeWithPodcast, queueIndex: Int?) {
    val episode = item.episode
    val uriHandler = LocalUriHandler.current
    val size = remember(episode.localPath) { episode.localPath?.let { File(it).length() }?.takeIf { it > 0 } }
    val rows = listOfNotNull(
        "Podcast" to item.podcast.title,
        formatDate(episode.publishedAt).takeIf { it.isNotEmpty() }?.let { "Published" to it },
        episode.durationMs?.let { "Length" to formatDuration(it) },
        seasonEpisode(item)?.let { "Episode" to it },
        "Queue" to (queueIndex?.let { "${ordinal(it + 1)} in queue" } ?: "Not in queue"),
        "Download" to when (episode.downloadState) {
            DownloadState.DOWNLOADED -> size?.let { "Downloaded · ${formatBytes(it)}" } ?: "Downloaded"
            DownloadState.DOWNLOADING, DownloadState.QUEUED -> "Downloading"
            DownloadState.FAILED -> "Download failed"
            DownloadState.NONE -> "Not downloaded"
        },
        "Played" to if (episode.played) "Yes" else "No",
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        for ((label, value) in rows) {
            DetailRow(label, value)
        }
        episode.link?.let { link ->
            Row(
                Modifier.fillMaxWidth().clickable { uriHandler.openUri(link) }.padding(vertical = 12.dp),
            ) {
                Text("Web page", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                Text(
                    link,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
}

@Composable
private fun BottomPane(
    item: EpisodeWithPodcast,
    chapters: List<Chapter>,
    queueIndex: Int?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    player: Player?,
    onPlay: (Long?) -> Unit,
    onPlayNext: () -> Unit,
) {
    val episode = item.episode
    val chapter = chapters.chapterAt(positionMs)
    val chapterIndex = chapter?.let { chapters.indexOf(it) } ?: -1

    // Every row has a fixed height, so the pane is the same size in both states.
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = if (isCurrent) {
                    chapter?.let { "Chapter ${chapterIndex + 1} · ${it.title}" } ?: item.podcast.title
                } else {
                    notPlayingStatus(episode.played, positionMs, durationMs, chapters.size, queueIndex)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(20.dp),
            )
            ChapterSeekBar(
                chapters = chapters,
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = if (isCurrent) ({ ms: Long -> player?.seekTo(ms) }) else null,
            )
            Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (durationMs > 0) "-${formatDuration((durationMs - positionMs).coerceAtLeast(0))}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().height(72.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isCurrent) Arrangement.SpaceBetween else Arrangement.spacedBy(12.dp),
            ) {
                if (isCurrent && player != null) {
                    IconButton(onClick = { previousChapter(player, chapters) }, enabled = chapters.isNotEmpty()) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous chapter")
                    }
                    IconButton(onClick = { player.seekBack() }) {
                        Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds")
                    }
                    FilledIconButton(
                        onClick = { if (isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(onClick = { player.seekForward() }) {
                        Icon(Icons.Default.Forward30, contentDescription = "Forward 30 seconds")
                    }
                    val next = chapters.getOrNull(chapterIndex + 1)
                    IconButton(onClick = { next?.let { player.seekTo(it.startMs) } }, enabled = next != null) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next chapter")
                    }
                } else {
                    Button(onClick = { onPlay(null) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (positionMs > 0 && !episode.played) "Resume" else "Play now")
                    }
                    FilledTonalButton(
                        onClick = onPlayNext,
                        enabled = queueIndex == null || queueIndex > 1,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Play next")
                    }
                }
            }
        }
    }
}

/** Jumps to the start of the current chapter, or to the previous one if we're near its start. */
private fun previousChapter(player: Player, chapters: List<Chapter>) {
    val position = player.currentPosition
    val index = chapters.indexOfLast { it.startMs <= position }
    if (index < 0) {
        player.seekTo(0)
        return
    }
    val target = if (position - chapters[index].startMs > 3_000 || index == 0) index else index - 1
    player.seekTo(chapters[target].startMs)
}

private fun notPlayingStatus(played: Boolean, positionMs: Long, durationMs: Long, chapterCount: Int, queueIndex: Int?): String {
    val parts = mutableListOf<String>()
    parts += when {
        played -> "Played"
        positionMs > 0 && durationMs > 0 -> "${formatMinutes(durationMs - positionMs)} left"
        else -> "Not started"
    }
    if (chapterCount > 0) parts += "$chapterCount chapters"
    queueIndex?.let { parts += "${ordinal(it + 1)} in queue" }
    return parts.joinToString(" · ")
}

private fun seasonEpisode(item: EpisodeWithPodcast): String? {
    val season = item.episode.seasonNumber
    val number = item.episode.episodeNumber
    return when {
        season != null && number != null -> "S$season · Ep $number"
        number != null -> "Ep $number"
        season != null -> "Season $season"
        else -> null
    }
}
