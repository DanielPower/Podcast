package ca.danielpower.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import ca.danielpower.podcast.data.Chapter
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.data.formatSpeed
import ca.danielpower.podcast.playback.PlayerUiState
import java.io.File
import kotlinx.coroutines.launch

private enum class EpisodeTab(val label: String) { Notes("Notes"), Chapters("Chapters"), Details("Details") }

/**
 * The Chapters tab. Greyed out when the episode has none; tapping it then says so instead of
 * opening an empty list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersChip(selected: Boolean, hasChapters: Boolean, onClick: () -> Unit) {
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("This episode has no chapters") } },
        state = tooltip,
        enableUserInput = false,
    ) {
        PaperChip(
            selected = selected && hasChapters,
            enabled = hasChapters,
            onClick = { if (hasChapters) onClick() else scope.launch { tooltip.show() } },
            label = EpisodeTab.Chapters.label,
        )
    }
}

/**
 * One screen for both an episode's page and Now Playing. When the episode is the one loaded in the
 * player, the bottom pane holds the player controls; otherwise it offers Play now / Play next. The
 * pane is the same height either way, so starting playback doesn't move anything.
 */
@Composable
fun EpisodeScreen(
    item: EpisodeWithPodcast?,
    chapters: List<Chapter>,
    queueIndex: Int?,
    player: Player?,
    playerState: PlayerUiState,
    skipBackSeconds: Int,
    skipForwardSeconds: Int,
    speed: Float,
    speedPresets: List<Float>,
    skipSilence: Boolean,
    silenceKeepMs: Int,
    onSpeed: (Float) -> Unit,
    onSkipSilence: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPlay: (startMs: Long?) -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveFromQueue: () -> Unit,
) {
    val isCurrent = item != null && player != null && playerState.episodeId == item.episode.id
    var tab by rememberSaveable { mutableIntStateOf(EpisodeTab.Notes.ordinal) }
    var speedSheetOpen by remember { mutableStateOf(false) }

    val positionMs = if (isCurrent) playerState.positionMs else item?.episode?.positionMs ?: 0
    val durationMs = if (isCurrent && playerState.durationMs > 0) {
        playerState.durationMs
    } else {
        item?.episode?.durationMs ?: 0
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopBar(item, isCurrent, queueIndex, onBack, onAddToQueue, onRemoveFromQueue)
        if (item == null) return@Column
        Column(Modifier.weight(1f)) {
            Header(item)
            Row(
                Modifier.padding(start = SideMargin, end = SideMargin, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EpisodeTab.entries.forEachIndexed { index, t ->
                    if (t == EpisodeTab.Chapters) {
                        ChaptersChip(selected = tab == index, hasChapters = chapters.isNotEmpty(), onClick = { tab = index })
                    } else {
                        PaperChip(selected = tab == index, onClick = { tab = index }, label = t.label)
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when (EpisodeTab.entries[tab]) {
                    EpisodeTab.Chapters -> ChapterList(
                        chapters = chapters,
                        current = if (isCurrent) chapters.chapterAt(positionMs) else null,
                        positionMs = if (isCurrent) positionMs else 0,
                        onChapter = { chapter ->
                            if (isCurrent) player?.seekTo(chapter.startMs) else onPlay(chapter.startMs)
                        },
                    )
                    EpisodeTab.Notes -> Notes(item.episode.description)
                    EpisodeTab.Details -> Details(item, queueIndex)
                }
            }
        }
        BottomPane(
            item = item,
            chapters = chapters,
            queueIndex = queueIndex,
            isCurrent = isCurrent,
            isPlaying = isCurrent && playerState.isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            player = player,
            skipBackSeconds = skipBackSeconds,
            skipForwardSeconds = skipForwardSeconds,
            speed = speed,
            onOpenSpeed = { speedSheetOpen = true },
            onPlay = onPlay,
            onPlayNext = onPlayNext,
        )
    }

    if (speedSheetOpen) {
        SpeedSheet(
            speed = speed,
            presets = speedPresets,
            skipSilence = skipSilence,
            silenceKeepMs = silenceKeepMs,
            onSpeed = onSpeed,
            onSkipSilence = onSkipSilence,
            onDismiss = { speedSheetOpen = false },
        )
    }
}

@Composable
private fun TopBar(
    item: EpisodeWithPodcast?,
    isCurrent: Boolean,
    queueIndex: Int?,
    onBack: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveFromQueue: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            if (isCurrent) "NOW PLAYING" else "EPISODE",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        val link = item?.episode?.link
        if (link != null) {
            IconButton(onClick = { uriHandler.openUri(link) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open episode web page")
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }, enabled = item != null) {
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
        }
    }
}

@Composable
private fun Header(item: EpisodeWithPodcast) {
    val episode = item.episode
    Row(
        Modifier.padding(start = SideMargin, end = SideMargin, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Artwork(item.artworkUrl, 120.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
            Text(
                item.podcast.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                episode.title,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    seasonEpisode(item),
                    formatDate(episode.publishedAt).takeIf { it.isNotEmpty() },
                    episode.durationMs?.let(::formatMinutes),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChapterList(chapters: List<Chapter>, current: Chapter?, positionMs: Long, onChapter: (Chapter) -> Unit) {
    if (chapters.isEmpty()) {
        EmptyMessage("This episode has no chapters.")
        return
    }
    val colors = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(chapters, key = { _, chapter -> chapter.startMs }) { index, chapter ->
            val selected = chapter == current
            // Chapters already played (before the current one) fade to the secondary ink.
            val past = current != null && !selected && chapter.startMs < positionMs
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) colors.primaryContainer else colors.surface)
                    .clickable { onChapter(chapter) }
                    .padding(horizontal = SideMargin)
                    .height(52.dp),
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.width(22.dp),
                )
                Text(
                    chapter.title.ifEmpty { "Untitled chapter" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else null,
                    color = when {
                        selected -> colors.onPrimaryContainer
                        past -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDuration(chapter.startMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
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
    val linkColor = MaterialTheme.colorScheme.secondary
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
        lineHeight = 22.sp,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SideMargin, vertical = 4.dp),
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SideMargin)) {
        for ((label, value) in rows) {
            DetailRow(label, value)
        }
        episode.link?.let { link ->
            Row(
                Modifier.fillMaxWidth().clickable { uriHandler.openUri(link) }.padding(vertical = 12.dp),
            ) {
                Text(
                    "Web page",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp),
                )
                Text(
                    link,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun BottomPane(
    item: EpisodeWithPodcast?,
    chapters: List<Chapter>,
    queueIndex: Int?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    player: Player?,
    skipBackSeconds: Int,
    skipForwardSeconds: Int,
    speed: Float,
    onOpenSpeed: () -> Unit,
    onPlay: (Long?) -> Unit,
    onPlayNext: () -> Unit,
) {
    if (item == null) return
    val episode = item.episode
    val chapter = chapters.chapterAt(positionMs)
    val chapterIndex = chapter?.let { chapters.indexOf(it) } ?: -1
    val colors = MaterialTheme.colorScheme

    // Every row has a fixed height, so the pane is the same size in both states.
    Column(Modifier.fillMaxWidth().background(MaterialTheme.podcastColors.raised)) {
        HorizontalDivider(color = colors.outlineVariant)
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCurrent) {
                        chapter?.let { "Ch. ${chapterIndex + 1} · ${it.title}" } ?: item.podcast.title
                    } else {
                        notPlayingStatus(episode.played, positionMs, durationMs, queueIndex)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCurrent) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chapters.isNotEmpty()) {
                    Text(
                        if (isCurrent && chapterIndex >= 0) "${chapterIndex + 1} of ${chapters.size}" else "${chapters.size} chapters",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            ChapterSeekBar(
                chapters = chapters,
                positionMs = positionMs,
                durationMs = durationMs,
                height = 20.dp,
                activeColor = if (isCurrent) colors.primary else colors.onSurface,
                onSeek = if (isCurrent) ({ ms: Long -> player?.seekTo(ms) }) else null,
            )
            Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(positionMs), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(
                    if (durationMs > 0) "−${formatDuration((durationMs - positionMs).coerceAtLeast(0))}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isCurrent) Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(10.dp),
            ) {
                if (isCurrent && player != null) {
                    TextButton(
                        onClick = onOpenSpeed,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Playback speed ${formatSpeed(speed)}" },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            formatSpeed(speed),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { player.seekBack() }, modifier = Modifier.size(52.dp)) {
                        SkipIcon(skipBackSeconds, forward = false, size = 28.dp)
                    }
                    Box(
                        Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primary)
                            .clickable { if (isPlaying) player.pause() else player.play() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    IconButton(onClick = { player.seekForward() }, modifier = Modifier.size(52.dp)) {
                        SkipIcon(skipForwardSeconds, forward = true, size = 28.dp)
                    }
                    val next = chapters.getOrNull(chapterIndex + 1)
                    IconButton(onClick = { next?.let { player.seekTo(it.startMs) } }, enabled = next != null) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next chapter")
                    }
                } else {
                    PrimaryButton(onClick = { onPlay(null) }, modifier = Modifier.weight(1.3f).height(48.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (positionMs > 0 && !episode.played) "Resume" else "Play now")
                    }
                    SecondaryButton(
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

/** Speed and skip silence, in a sheet at the bottom so it opens under the thumb. Tapping outside closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(
    speed: Float,
    presets: List<Float>,
    skipSilence: Boolean,
    silenceKeepMs: Int,
    onSpeed: (Float) -> Unit,
    onSkipSilence: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
        containerColor = MaterialTheme.podcastColors.raised,
        dragHandle = {
            Box(Modifier.padding(top = 8.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp).background(colors.outlineVariant, RoundedCornerShape(2.dp)))
        },
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(start = SideMargin, end = SideMargin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Speed", Modifier.weight(1f))
                Text(formatSpeed(speed), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepButton(Icons.Default.Remove, "Slower", enabled = speed > MIN_SPEED) { onSpeed(snapSpeed(speed - SPEED_STEP)) }
                Slider(
                    value = speed,
                    onValueChange = { onSpeed(snapSpeed(it)) },
                    valueRange = MIN_SPEED..MAX_SPEED,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary,
                        inactiveTrackColor = MaterialTheme.podcastColors.track,
                    ),
                    modifier = Modifier.weight(1f),
                )
                StepButton(Icons.Default.Add, "Faster", enabled = speed < MAX_SPEED) { onSpeed(snapSpeed(speed + SPEED_STEP)) }
            }
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in presets) {
                    val selected = preset == speed
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, colors.outline, RoundedCornerShape(4.dp))
                            .background(if (selected) colors.onSurface else Color.Transparent)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSpeed(preset) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            formatSpeed(preset),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) colors.surface else colors.onSurface,
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .toggleable(value = skipSilence, role = Role.Switch, onValueChange = onSkipSilence),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Skip silence", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Pauses over $silenceKeepMs ms are shortened",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                PaperSwitch(skipSilence, onSkipSilence)
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).border(1.dp, if (enabled) colors.outline else colors.outlineVariant, RoundedCornerShape(4.dp)),
    ) {
        Icon(icon, contentDescription = description)
    }
}

private const val MIN_SPEED = 0.5f
private const val MAX_SPEED = 3f
private const val SPEED_STEP = 0.05f

/** Rounds to the nearest 0.05× within range, so the slider and stepper land on tidy values. */
private fun snapSpeed(speed: Float): Float =
    (Math.round(speed / SPEED_STEP) * SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED).let { Math.round(it * 100) / 100f }

private fun notPlayingStatus(played: Boolean, positionMs: Long, durationMs: Long, queueIndex: Int?): String {
    val parts = mutableListOf<String>()
    parts += when {
        played -> "Played"
        positionMs > 0 && durationMs > 0 -> "${formatMinutes(durationMs - positionMs)} left"
        else -> "Not started"
    }
    queueIndex?.let { parts += "${ordinal(it + 1)} in your queue" }
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
