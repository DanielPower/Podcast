package ca.danielpower.podcast.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts WHERE subscribed = 1 ORDER BY title COLLATE NOCASE")
    fun observeSubscribed(): Flow<List<Podcast>>

    @Query("SELECT * FROM podcasts WHERE subscribed = 1")
    suspend fun getSubscribed(): List<Podcast>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    fun observe(id: Long): Flow<Podcast?>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun get(id: Long): Podcast?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getByFeedUrl(feedUrl: String): Podcast?

    @Query("SELECT feedUrl FROM podcasts WHERE subscribed = 1")
    fun observeSubscribedFeedUrls(): Flow<List<String>>

    @Query("UPDATE podcasts SET subscribed = :subscribed, openedAt = :now WHERE id = :id")
    suspend fun setSubscribed(id: Long, subscribed: Boolean, now: Long)

    /**
     * Deletes unsubscribed podcasts not opened since [cutoff] that have nothing left in use: no
     * episode queued, downloaded or downloading, and none started (so listening history survives).
     */
    @Query(
        """DELETE FROM podcasts WHERE subscribed = 0 AND openedAt < :cutoff AND NOT EXISTS (
        SELECT 1 FROM episodes e WHERE e.podcastId = podcasts.id AND (e.queuePosition IS NOT NULL
        OR e.localPath IS NOT NULL OR e.downloadState IN ('QUEUED', 'DOWNLOADING') OR e.played = 1 OR e.positionMs > 0))""",
    )
    suspend fun deleteUnused(cutoff: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(podcast: Podcast): Long

    /** Updates what the feed says, leaving the subscription alone. */
    @Query(
        """UPDATE podcasts SET title = :title, author = :author, artworkUrl = COALESCE(:artworkUrl, artworkUrl),
        description = :description WHERE id = :id""",
    )
    suspend fun updateFeedInfo(id: Long, title: String, author: String?, artworkUrl: String?, description: String?)

    @Query("UPDATE podcasts SET openedAt = :now WHERE id = :id")
    suspend fun markOpened(id: Long, now: Long)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun delete(id: Long)
}

private const val EPISODE_WITH_PODCAST = """
    SELECT e.*, p.id AS podcast_id, p.title AS podcast_title, p.artworkUrl AS podcast_artworkUrl
    FROM episodes e JOIN podcasts p ON p.id = e.podcastId
"""

@Dao
abstract class EpisodeDao {
    @Query("$EPISODE_WITH_PODCAST WHERE e.queuePosition IS NOT NULL ORDER BY e.queuePosition")
    abstract fun observeQueue(): Flow<List<EpisodeWithPodcast>>

    @Query("$EPISODE_WITH_PODCAST WHERE e.queuePosition IS NOT NULL ORDER BY e.queuePosition")
    abstract suspend fun getQueue(): List<EpisodeWithPodcast>

    @Query("$EPISODE_WITH_PODCAST WHERE e.podcastId = :podcastId ORDER BY e.publishedAt DESC")
    abstract fun observeForPodcast(podcastId: Long): Flow<List<EpisodeWithPodcast>>

    @Query("$EPISODE_WITH_PODCAST WHERE e.id = :id")
    abstract suspend fun getWithPodcast(id: Long): EpisodeWithPodcast?

    @Query("SELECT * FROM episodes WHERE id = :id")
    abstract suspend fun get(id: Long): Episode?

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId")
    abstract suspend fun getForPodcast(podcastId: Long): List<Episode>

    @Query("SELECT guid FROM episodes WHERE podcastId = :podcastId")
    abstract suspend fun getGuids(podcastId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(episodes: List<Episode>): List<Long>

    @Query("SELECT MAX(queuePosition) FROM episodes")
    abstract suspend fun maxQueuePosition(): Long?

    @Query("SELECT MIN(queuePosition) FROM episodes")
    abstract suspend fun minQueuePosition(): Long?

    @Query("UPDATE episodes SET queuePosition = :position, removedFromQueueAt = NULL WHERE id = :id")
    abstract suspend fun setQueuePosition(id: Long, position: Long)

    @Query("UPDATE episodes SET queuePosition = NULL, removedFromQueueAt = :now WHERE id = :id")
    abstract suspend fun removeFromQueue(id: Long, now: Long)

    /** Downloaded episodes that left the queue before [cutoff]. */
    @Query(
        """SELECT * FROM episodes WHERE queuePosition IS NULL AND localPath IS NOT NULL
        AND removedFromQueueAt IS NOT NULL AND removedFromQueueAt < :cutoff""",
    )
    abstract suspend fun getExpiredDownloads(cutoff: Long): List<Episode>

    @Query(
        """UPDATE episodes SET downloadState = :state, localPath = :localPath,
        downloadedBytes = 0, downloadTotalBytes = NULL WHERE id = :id""",
    )
    abstract suspend fun setDownload(id: Long, state: DownloadState, localPath: String?)

    @Query("UPDATE episodes SET downloadedBytes = :downloaded, downloadTotalBytes = :total WHERE id = :id")
    abstract suspend fun setDownloadProgress(id: Long, downloaded: Long, total: Long?)

    @Query("UPDATE episodes SET positionMs = :positionMs WHERE id = :id")
    abstract suspend fun setPosition(id: Long, positionMs: Long)

    @Query("UPDATE episodes SET played = 1, positionMs = 0 WHERE id = :id")
    abstract suspend fun markPlayed(id: Long)

    /** Appends to the end of the queue, unless the episode is already queued. */
    @Transaction
    open suspend fun appendToQueue(id: Long) {
        val episode = get(id) ?: return
        if (episode.queuePosition != null) return
        setQueuePosition(id, (maxQueuePosition() ?: 0) + 1)
    }

    /** Moves the episode to the front of the queue, adding it if needed. */
    @Transaction
    open suspend fun moveToFront(id: Long) {
        setQueuePosition(id, (minQueuePosition() ?: 0) - 1)
    }

    /** Rewrites queue positions so they follow the given order. */
    @Transaction
    open suspend fun reorderQueue(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setQueuePosition(id, index.toLong()) }
    }

    @Query("SELECT id FROM episodes WHERE podcastId = :podcastId AND guid = :guid")
    abstract suspend fun getId(podcastId: Long, guid: String): Long?

    @Query("$EPISODE_WITH_PODCAST WHERE e.id = :id")
    abstract fun observeWithPodcast(id: Long): Flow<EpisodeWithPodcast?>

    /** Fills in metadata that older versions of the app didn't store, for episodes already known. */
    @Query(
        """UPDATE episodes SET link = :link, seasonNumber = :seasonNumber, episodeNumber = :episodeNumber,
        chaptersUrl = :chaptersUrl, description = :description WHERE podcastId = :podcastId AND guid = :guid""",
    )
    abstract suspend fun updateMetadata(
        podcastId: Long,
        guid: String,
        link: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        chaptersUrl: String?,
        description: String?,
    )

    @Transaction
    open suspend fun updateAllMetadata(podcastId: Long, parsed: List<ParsedEpisode>) {
        for (item in parsed) {
            updateMetadata(
                podcastId,
                item.guid,
                item.link,
                item.seasonNumber,
                item.episodeNumber,
                item.chaptersUrl,
                item.description,
            )
        }
    }

    @Query("SELECT * FROM chapters WHERE episodeId = :episodeId ORDER BY startMs")
    abstract fun observeChapters(episodeId: Long): Flow<List<Chapter>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChapters(chapters: List<Chapter>)

    @Query("DELETE FROM chapters WHERE episodeId = :episodeId")
    abstract suspend fun deleteChapters(episodeId: Long)

    @Query("UPDATE episodes SET chaptersLoaded = 1 WHERE id = :episodeId")
    abstract suspend fun markChaptersLoaded(episodeId: Long)

    @Transaction
    open suspend fun replaceChapters(episodeId: Long, chapters: List<Chapter>) {
        deleteChapters(episodeId)
        insertChapters(chapters)
        markChaptersLoaded(episodeId)
    }
}
