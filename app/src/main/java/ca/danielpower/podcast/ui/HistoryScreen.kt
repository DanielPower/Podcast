package ca.danielpower.podcast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.EpisodeWithPodcast
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Episodes that have been played, most recently played first, grouped by the day they were played. */
@Composable
fun HistoryScreen(
    history: List<EpisodeWithPodcast>?,
    onOpen: (Long) -> Unit,
    onAddToQueue: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        Box(Modifier.weight(1f)) {
            when {
                history == null -> Unit
                history.isEmpty() -> EmptyMessage("Nothing played yet.\nEpisodes you listen to will be listed here.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    var lastDay: String? = null
                    for (item in history) {
                        val day = dayLabel(item.episode.lastPlayedAt ?: 0)
                        if (day != lastDay) {
                            lastDay = day
                            item(key = "day-$day") {
                                SectionLabel(day, Modifier.padding(start = SideMargin, end = SideMargin, top = 14.dp, bottom = 4.dp))
                            }
                        }
                        item(key = item.episode.id) {
                            HistoryRow(
                                item,
                                onClick = { onOpen(item.episode.id) },
                                onAddToQueue = { onAddToQueue(item.episode.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: EpisodeWithPodcast, onClick: () -> Unit, onAddToQueue: () -> Unit) {
    val episode = item.episode
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = SideMargin, top = 10.dp, bottom = 10.dp, end = 4.dp),
    ) {
        Artwork(item.artworkUrl, 52.dp)
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
            Text(
                historySubtitle(item),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (episode.queuePosition != null) {
            IconButton(onClick = {}, enabled = false) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = "In queue")
            }
        } else {
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to queue")
            }
        }
    }
}

private fun historySubtitle(item: EpisodeWithPodcast): String {
    val episode = item.episode
    val duration = episode.durationMs
    val progress = when {
        episode.played && episode.positionMs == 0L -> "Finished"
        duration != null && duration > 0 -> "${formatMinutes((duration - episode.positionMs).coerceAtLeast(0))} left"
        else -> null
    }
    val time = episode.lastPlayedAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    return listOfNotNull(progress, time).joinToString(" · ")
}

/** "Today", "Yesterday", or the date. */
private fun dayLabel(epochMs: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = Calendar.getInstance()
    fun Calendar.sameDay(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        day.sameDay(today) -> "Today"
        day.sameDay(yesterday) -> "Yesterday"
        else -> formatDate(epochMs)
    }
}
