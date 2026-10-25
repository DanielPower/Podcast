package ca.danielpower.podcast.data

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
