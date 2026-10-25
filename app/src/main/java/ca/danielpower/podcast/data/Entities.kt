package ca.danielpower.podcast.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcasts",
    indices = [Index(value = ["feedUrl"], unique = true)],
)
data class Podcast(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val author: String?,
    val artworkUrl: String?,
    val description: String?,
    /**
     * False for a podcast that was opened from search, or unsubscribed from. Its episodes can still be
     * queued, but it isn't shown with the subscriptions or refreshed.
     */
    @ColumnInfo(defaultValue = "1") val subscribed: Boolean = true,
    /** When an unsubscribed podcast's page was last opened. It's kept for a day after that. */
    @ColumnInfo(defaultValue = "0") val openedAt: Long = 0,
)

enum class DownloadState { NONE, QUEUED, DOWNLOADING, DOWNLOADED, FAILED }

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Podcast::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["podcastId", "guid"], unique = true),
        Index(value = ["queuePosition"]),
    ],
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val publishedAt: Long,
    val durationMs: Long?,
    val artworkUrl: String?,
    val downloadState: DownloadState = DownloadState.NONE,
    val localPath: String? = null,
    val downloadedBytes: Long = 0,
    val downloadTotalBytes: Long? = null,
    val positionMs: Long = 0,
    val played: Boolean = false,
    /** Position in the queue, or null when the episode is not queued. Lower plays first. */
    val queuePosition: Long? = null,
    /** When the episode last left the queue. Its download is kept for a week after this. */
    val removedFromQueueAt: Long? = null,
    /** The episode's web page. */
    val link: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val chaptersUrl: String? = null,
    /** Whether chapters have been loaded into the chapters table (from the feed or [chaptersUrl]). */
    @ColumnInfo(defaultValue = "0") val chaptersLoaded: Boolean = false,
    /** When the episode last started playing, or null if it never has. Orders the history. */
    val lastPlayedAt: Long? = null,
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["episodeId", "startMs"],
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["episodeId"])],
)
data class Chapter(
    val episodeId: Long,
    val startMs: Long,
    val title: String,
    val url: String?,
    val imageUrl: String?,
)

/**
 * What happened with one podcast on one day, for the Statistics tab. Rows are only ever added to, so
 * the numbers outlive the episodes (and downloads) they came from. There's no foreign key: a
 * podcast with statistics is kept (see [PodcastDao.deleteUnused]).
 */
@Entity(
    tableName = "daily_stats",
    primaryKeys = ["day", "podcastId"],
    indices = [Index(value = ["podcastId"])],
)
data class DailyStats(
    /** The local date, as days since 1970-01-01 (LocalDate.toEpochDay). */
    val day: Long,
    val podcastId: Long,
    /** Time spent playing, in real time (so 1 hour at 2x counts as 1 hour). */
    val listenedMs: Long = 0,
    /** Real time skip silence cut out. */
    val silenceSavedMs: Long = 0,
    /** Episodes played to the end. */
    val finished: Int = 0,
    val downloadedBytes: Long = 0,
)

/** One podcast's [DailyStats] added up over a range of days. */
data class PodcastStats(
    val podcastId: Long,
    /** Null if the podcast has been deleted since. */
    val title: String?,
    val artworkUrl: String?,
    val listenedMs: Long,
    val silenceSavedMs: Long,
    val finished: Int,
    val downloadedBytes: Long,
)

/** An episode along with the podcast it belongs to, for display. */
data class EpisodeWithPodcast(
    @Embedded val episode: Episode,
    @Embedded(prefix = "podcast_") val podcast: PodcastSummary,
) {
    val artworkUrl: String? get() = episode.artworkUrl ?: podcast.artworkUrl
    val isPlayable: Boolean get() = episode.downloadState == DownloadState.DOWNLOADED
}

data class PodcastSummary(
    val id: Long,
    val title: String,
    val artworkUrl: String?,
)
