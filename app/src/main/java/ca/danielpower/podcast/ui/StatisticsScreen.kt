package ca.danielpower.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.podcast.data.PodcastStats

/** The four numbers the tab shows. Each can be broken down by podcast. */
enum class StatsMetric(val label: String, val breakdown: String) {
    LISTENED("Listened", "by time listened"),
    SAVED("Saved by skip silence", "by time saved"),
    FINISHED("Episodes finished", "by episodes finished"),
    DOWNLOADED("Downloaded", "by size downloaded"),
    ;

    fun of(stats: PodcastStats): Long = when (this) {
        LISTENED -> stats.listenedMs
        SAVED -> stats.silenceSavedMs
        FINISHED -> stats.finished.toLong()
        DOWNLOADED -> stats.downloadedBytes
    }

    fun format(value: Long): String = when (this) {
        LISTENED, SAVED -> formatStatTime(value)
        FINISHED -> value.toString()
        DOWNLOADED -> if (value == 0L) "0 MB" else formatBytes(value)
    }
}

/**
 * Time listened, time saved by skip silence, episodes finished and size downloaded, over a week,
 * month, year or all time. Tapping one of the four shows it per podcast below.
 */
@Composable
fun StatisticsScreen(
    ui: StatsUi?,
    onRange: (StatsRange) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var metric by rememberSaveable { mutableStateOf(StatsMetric.LISTENED) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader("Statistics", Icons.Default.BarChart) }
        if (ui == null) return@LazyColumn
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SideMargin, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (range in StatsRange.entries) {
                    PaperChip(selected = range == ui.period.range, onClick = { onRange(range) }, label = range.label)
                }
            }
        }
        item { PeriodStepper(ui, onPrevious, onNext) }
        item { Tiles(ui, metric, onSelect = { metric = it }) }
        item {
            SectionLabel(
                "By podcast",
                Modifier.padding(start = SideMargin, end = SideMargin, top = 20.dp, bottom = 6.dp),
                trailing = metric.breakdown,
            )
        }
        val rows = ui.byPodcast.filter { metric.of(it) > 0 }.sortedByDescending { metric.of(it) }
        if (rows.isEmpty()) {
            item {
                Text(
                    emptyText(ui, metric),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SideMargin, vertical = 24.dp),
                )
            }
        } else {
            val max = metric.of(rows.first()).toFloat()
            items(rows, key = { it.podcastId }) { stats ->
                BreakdownRow(stats, metric.format(metric.of(stats)), metric.of(stats) / max)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PeriodStepper(ui: StatsUi, onPrevious: () -> Unit, onNext: () -> Unit) {
    val period = ui.period
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (period.range == StatsRange.ALL) {
            Text(
                period.label(ui.firstDay),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        } else {
            IconButton(onClick = onPrevious, enabled = period.canGoBack(ui.firstDay)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous ${period.range.label.lowercase()}")
            }
            Text(
                period.label(ui.firstDay),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = period.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next ${period.range.label.lowercase()}")
            }
        }
    }
}

@Composable
private fun Tiles(ui: StatsUi, selected: StatsMetric, onSelect: (StatsMetric) -> Unit) {
    val listened = ui.byPodcast.sumOf { it.listenedMs }
    val saved = ui.byPodcast.sumOf { it.silenceSavedMs }
    val finished = ui.byPodcast.sumOf { it.finished }
    val downloaded = ui.byPodcast.sumOf { it.downloadedBytes }
    val days = ui.period.daysCounted(ui.today, ui.firstDay)
    val finishedFrom = ui.byPodcast.count { it.finished > 0 }

    val tiles = listOf(
        Triple(StatsMetric.LISTENED, formatStatTime(listened), if (days > 1) "about ${formatStatTime(listened / days)} a day" else ""),
        Triple(StatsMetric.SAVED, formatStatTime(saved), if (listened > 0) "${saved * 100 / listened}% of listening" else ""),
        Triple(StatsMetric.FINISHED, finished.toString(), if (finishedFrom > 0) "from $finishedFrom ${if (finishedFrom == 1) "podcast" else "podcasts"}" else ""),
        Triple(StatsMetric.DOWNLOADED, StatsMetric.DOWNLOADED.format(downloaded), "${formatBytes(ui.onPhoneBytes)} on the phone now"),
    )
    Column(
        Modifier.padding(horizontal = SideMargin, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (pair in tiles.chunked(2)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((metric, value, sub) in pair) {
                    Tile(metric.label, value, sub, metric == selected, { onSelect(metric) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(PaperShape)
            .background(MaterialTheme.podcastColors.raised)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, PaperShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = Gothic, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BreakdownRow(stats: PodcastStats, value: String, fraction: Float) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SideMargin, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(stats.artworkUrl, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stats.title ?: "Removed podcast",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            ThinBar(fraction, MaterialTheme.colorScheme.primary, height = 4.dp)
        }
    }
}

private fun emptyText(ui: StatsUi, metric: StatsMetric): String = when {
    ui.firstDay == null -> "Nothing yet. Statistics are kept from now on, so listen to something and it'll show up here."
    metric == StatsMetric.SAVED && ui.byPodcast.any { it.listenedMs > 0 } ->
        "Skip silence hasn't cut anything in this period. It's in Settings, and under the speed button."
    else -> "Nothing in this period."
}

/** "41 h 12 m", "52 min", "0 min". Rounded down, so "1 h" means a whole hour. */
fun formatStatTime(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60} h ${minutes % 60} m"
        ms in 1 until 60_000 -> "<1 min"
        else -> "$minutes min"
    }
}
