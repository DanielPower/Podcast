package ca.danielpower.podcast.data

import android.content.Context
import android.util.Log
import ca.danielpower.podcast.work.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class PodcastRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {
    private val podcasts = db.podcastDao()
    private val episodes = db.episodeDao()

    val directory = DirectorySearch(client)

    fun observePodcasts() = podcasts.observeAll()
    fun observePodcast(id: Long) = podcasts.observe(id)
    fun observeSubscribedFeedUrls() = podcasts.observeFeedUrls()
    fun observeQueue() = episodes.observeQueue()
    fun observeEpisodes(podcastId: Long) = episodes.observeForPodcast(podcastId)
    fun observeHistory() = episodes.observeHistory()

    suspend fun getQueue() = episodes.getQueue()
    suspend fun getEpisode(id: Long) = episodes.getWithPodcast(id)

    /**
     * Subscribes to a feed. Its back catalogue is recorded but not downloaded, except
     * for the newest episode so there is something to listen to right away.
     */
    suspend fun subscribe(feedUrl: String): Long {
        podcasts.getByFeedUrl(feedUrl)?.let { return it.id }
        val feed = fetchFeed(feedUrl)
        val podcastId = podcasts.insert(
            Podcast(
                feedUrl = feedUrl,
                title = feed.title,
                author = feed.author,
                artworkUrl = feed.artworkUrl,
                description = feed.description,
            ),
        )
        episodes.insertAll(feed.episodes.map { it.toEntity(podcastId) })
        saveInlineChapters(podcastId, feed.episodes)
        episodes.getForPodcast(podcastId).maxByOrNull { it.publishedAt }?.let { startDownload(it.id) }
        return podcastId
    }

    suspend fun unsubscribe(podcastId: Long) {
        for (episode in episodes.getForPodcast(podcastId)) {
            Downloads.cancel(context, episode.id)
            episode.localPath?.let { File(it).delete() }
        }
        podcasts.delete(podcastId)
    }

    /** Checks every feed for new episodes and, if auto-download is on, starts downloading them. */
    suspend fun refreshAll() {
        for (podcast in podcasts.getAll()) {
            try {
                refresh(podcast)
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed for ${podcast.feedUrl}", e)
            }
        }
    }

    private suspend fun refresh(podcast: Podcast) {
        val feed = fetchFeed(podcast.feedUrl)
        podcasts.update(
            podcast.copy(
                title = feed.title,
                author = feed.author,
                artworkUrl = feed.artworkUrl ?: podcast.artworkUrl,
                description = feed.description,
            ),
        )
        val known = episodes.getGuids(podcast.id).toHashSet()
        val (old, fresh) = feed.episodes.distinctBy { it.guid }.partition { it.guid in known }
        episodes.updateAllMetadata(podcast.id, old)
        val ids = episodes.insertAll(fresh.map { it.toEntity(podcast.id) })
        saveInlineChapters(podcast.id, feed.episodes)
        if (settings.current.autoDownload) ids.filter { it > 0 }.forEach { startDownload(it) }
    }

    /** Stores chapters given inline in the feed, for episodes whose chapters aren't loaded yet. */
    private suspend fun saveInlineChapters(podcastId: Long, parsed: List<ParsedEpisode>) {
        val withChapters = parsed.filter { it.inlineChapters.isNotEmpty() }
        if (withChapters.isEmpty()) return
        val byGuid = episodes.getForPodcast(podcastId).associateBy { it.guid }
        for (item in withChapters) {
            val episode = byGuid[item.guid] ?: continue
            if (episode.chaptersLoaded) continue
            episodes.replaceChapters(episode.id, item.inlineChapters.map { it.toEntity(episode.id) })
        }
    }

    fun observeEpisode(id: Long) = episodes.observeWithPodcast(id)
    fun observeChapters(episodeId: Long) = episodes.observeChapters(episodeId)

    /** Fetches the episode's Podcasting 2.0 chapters file the first time the episode is opened. */
    suspend fun loadChapters(episodeId: Long) {
        val episode = episodes.get(episodeId) ?: return
        if (episode.chaptersLoaded) return
        val url = episode.chaptersUrl ?: return
        val chapters = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                FeedParser.parseJsonChapters(response.body!!.string())
            }
        }
        episodes.replaceChapters(episodeId, chapters.map { it.toEntity(episodeId) })
    }

    /**
     * Appends an episode to the queue. If it isn't downloaded yet it waits in the queue as a
     * placeholder, and the player skips over it until the download finishes.
     */
    suspend fun addToQueue(episodeId: Long) {
        val episode = episodes.get(episodeId) ?: return
        episodes.appendToQueue(episodeId)
        if (episode.downloadState != DownloadState.DOWNLOADED && episode.downloadState != DownloadState.DOWNLOADING) {
            startDownload(episodeId)
        }
    }

    /** Restarts a failed download, keeping the episode's place in the queue. */
    suspend fun retryDownload(episodeId: Long) = startDownload(episodeId)

    suspend fun moveToFront(episodeId: Long) = episodes.moveToFront(episodeId)

    /** Puts the episode second in the queue, right after the one playing (downloading it if needed). */
    suspend fun playNext(episodeId: Long) {
        addToQueue(episodeId)
        val queue = episodes.getQueue().map { it.episode.id }
        if (queue.firstOrNull() == episodeId) return
        val ids = queue.filter { it != episodeId }
        if (ids.isEmpty()) return
        episodes.reorderQueue(listOf(ids.first(), episodeId) + ids.drop(1))
    }

    suspend fun reorderQueue(orderedIds: List<Long>) = episodes.reorderQueue(orderedIds)

    /**
     * Removes an episode from the queue without marking it played. A finished download is kept
     * for a while; one still in progress is cancelled.
     */
    suspend fun dismiss(episodeId: Long) {
        episodes.removeFromQueue(episodeId, System.currentTimeMillis())
        val episode = episodes.get(episodeId) ?: return
        if (episode.downloadState != DownloadState.DOWNLOADED) {
            Downloads.cancel(context, episodeId)
            episodes.setDownload(episodeId, DownloadState.NONE, null)
        }
    }

    /** Called when an episode plays to the end. */
    suspend fun markFinished(episodeId: Long) {
        episodes.markPlayed(episodeId)
        episodes.removeFromQueue(episodeId, System.currentTimeMillis())
    }

    /** Deletes the files of episodes that have been out of the queue for longer than the "keep" setting. */
    suspend fun deleteExpiredDownloads() {
        val cutoff = System.currentTimeMillis() - settings.current.keepDays * DAY_MS
        for (episode in episodes.getExpiredDownloads(cutoff)) {
            episode.localPath?.let { File(it).delete() }
            episodes.setDownload(episode.id, DownloadState.NONE, null)
        }
    }

    suspend fun savePosition(episodeId: Long, positionMs: Long) = episodes.setPosition(episodeId, positionMs)

    /** Called whenever an episode starts playing, so it moves to the top of the history. */
    suspend fun markPlaying(episodeId: Long) = episodes.setLastPlayed(episodeId, System.currentTimeMillis())

    // Download bookkeeping, used by DownloadWorker.

    fun downloadFile(episodeId: Long): File {
        val dir = context.getExternalFilesDir("episodes") ?: File(context.filesDir, "episodes")
        dir.mkdirs()
        return File(dir, "$episodeId.audio")
    }

    suspend fun getEpisodeEntity(id: Long) = episodes.get(id)

    suspend fun setDownloadState(episodeId: Long, state: DownloadState, localPath: String? = null) =
        episodes.setDownload(episodeId, state, localPath)

    suspend fun setDownloadProgress(episodeId: Long, downloaded: Long, total: Long?) =
        episodes.setDownloadProgress(episodeId, downloaded, total)

    suspend fun onDownloadComplete(episodeId: Long, file: File) =
        episodes.setDownload(episodeId, DownloadState.DOWNLOADED, file.absolutePath)

    /** Starts downloading an episode and gives it its place at the end of the queue. */
    private suspend fun startDownload(episodeId: Long) {
        episodes.setDownload(episodeId, DownloadState.QUEUED, null)
        episodes.appendToQueue(episodeId)
        Downloads.enqueue(context, episodeId)
    }

    private suspend fun fetchFeed(url: String): ParsedFeed = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            FeedParser.parse(response.body!!.byteStream())
        }
    }

    private fun ParsedChapter.toEntity(episodeId: Long) = Chapter(
        episodeId = episodeId,
        startMs = startMs,
        title = title,
        url = url,
        imageUrl = imageUrl,
    )

    private fun ParsedEpisode.toEntity(podcastId: Long) = Episode(
        podcastId = podcastId,
        guid = guid,
        title = title,
        description = description,
        audioUrl = audioUrl,
        publishedAt = publishedAt,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        link = link,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        chaptersUrl = chaptersUrl,
    )

    companion object {
        private const val TAG = "PodcastRepository"
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
