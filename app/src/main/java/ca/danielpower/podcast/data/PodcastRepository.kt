package ca.danielpower.podcast.data

import android.content.Context
import android.net.Uri
import android.util.Log
import ca.danielpower.podcast.work.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.LocalDate

data class RefreshResult(val newEpisodes: Int, val failedFeeds: Int, val feeds: Int)

/** Progress of importing subscriptions from an OPML file. */
data class OpmlImport(
    /** The file is still being read, so [total] isn't known yet. */
    val reading: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val added: Int = 0,
    val failed: Int = 0,
    /** Set when the file couldn't be read at all. */
    val unreadable: Boolean = false,
) {
    val running get() = reading || (!unreadable && done < total)
}

class PodcastRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {
    private val podcasts = db.podcastDao()
    private val episodes = db.episodeDao()
    private val stats = db.statsDao()

    val directory = DirectorySearch(client)

    /** For work that should finish even if the screen that started it is closed. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun observePodcasts() = podcasts.observeSubscribed()
    fun observePodcast(id: Long) = podcasts.observe(id)
    fun observeSubscribedFeedUrls() = podcasts.observeSubscribedFeedUrls()
    fun observeQueue() = episodes.observeQueue()
    fun observeEpisodes(podcastId: Long) = episodes.observeForPodcast(podcastId)
    fun observeHistory() = episodes.observeHistory()

    suspend fun getQueue() = episodes.getQueue()
    suspend fun getEpisode(id: Long) = episodes.getWithPodcast(id)

    /**
     * Loads a feed so its page can be shown, and its episodes queued, without subscribing.
     * Returns the podcast's id.
     */
    suspend fun open(feedUrl: String): Long {
        val existing = podcasts.getByFeedUrl(feedUrl) ?: return addFeed(feedUrl)
        if (!existing.subscribed) {
            podcasts.markOpened(existing.id, System.currentTimeMillis())
            // A page opened before shows what it had if the feed can't be fetched now.
            try {
                refresh(existing)
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed for $feedUrl", e)
            }
        }
        return existing.id
    }

    /** Subscribes to a feed from search, without opening its page first. */
    suspend fun subscribe(feedUrl: String) {
        subscribe(podcasts.getByFeedUrl(feedUrl)?.id ?: addFeed(feedUrl))
    }

    /**
     * Subscribes to a podcast whose feed is already loaded. Its back catalogue isn't downloaded,
     * except for the newest episode so there is something to listen to right away.
     */
    suspend fun subscribe(podcastId: Long) {
        val podcast = podcasts.get(podcastId) ?: return
        if (podcast.subscribed) return
        podcasts.setSubscribed(podcastId, true, System.currentTimeMillis())
        val newest = episodes.getForPodcast(podcastId).maxByOrNull { it.publishedAt } ?: return
        if (!newest.played && newest.queuePosition == null && newest.downloadState != DownloadState.DOWNLOADED) {
            startDownload(newest.id)
        }
    }

    /**
     * Unsubscribes: the podcast's episodes leave the queue and their downloads are deleted. It stays
     * in the database, unsubscribed, so its page can still be shown.
     */
    suspend fun unsubscribe(podcastId: Long) {
        val now = System.currentTimeMillis()
        for (episode in episodes.getForPodcast(podcastId)) {
            if (episode.queuePosition != null) episodes.removeFromQueue(episode.id, now)
            if (episode.downloadState != DownloadState.NONE || episode.localPath != null) {
                episodes.setDownload(episode.id, DownloadState.NONE, null)
                Downloads.cancel(context, episode.id)
                episode.localPath?.let { File(it).delete() }
                partialFile(episode.id).delete()
            }
        }
        podcasts.setSubscribed(podcastId, false, now)
    }

    private val _opmlImport = MutableStateFlow<OpmlImport?>(null)
    val opmlImport = _opmlImport.asStateFlow()

    /**
     * Subscribes to every feed in an OPML file, in the background. Unlike subscribing from search,
     * nothing is downloaded: a long list would otherwise fill the queue at once.
     */
    fun importOpml(uri: Uri) {
        if (_opmlImport.value?.running == true) return
        _opmlImport.value = OpmlImport(reading = true)
        appScope.launch {
            val urls = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { Opml.parseFeedUrls(it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read OPML file", e)
                _opmlImport.value = OpmlImport(unreadable = true)
                return@launch
            }
            var progress = OpmlImport(total = urls.size)
            _opmlImport.value = progress
            for (url in urls) {
                progress = try {
                    val existing = podcasts.getByFeedUrl(url)
                    if (existing?.subscribed == true) {
                        progress.copy(done = progress.done + 1)
                    } else {
                        podcasts.setSubscribed(existing?.id ?: addFeed(url), true, System.currentTimeMillis())
                        progress.copy(done = progress.done + 1, added = progress.added + 1)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't import $url", e)
                    progress.copy(done = progress.done + 1, failed = progress.failed + 1)
                }
                _opmlImport.value = progress
            }
        }
    }

    /** Writes the subscriptions to an OPML file, and returns how many there were. */
    suspend fun exportOpml(uri: Uri): Int {
        val subscribed = podcasts.getSubscribed()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { Opml.write(subscribed.sortedBy { p -> p.title.lowercase() }, it) }
        }
        return subscribed.size
    }

    /** Adds a feed's podcast and episodes, unsubscribed, and returns the podcast's id. */
    private suspend fun addFeed(feedUrl: String): Long {
        val feed = fetchFeed(feedUrl)
        val podcastId = podcasts.insert(
            Podcast(
                feedUrl = feedUrl,
                title = feed.title,
                author = feed.author,
                artworkUrl = feed.artworkUrl,
                description = feed.description,
                subscribed = false,
                openedAt = System.currentTimeMillis(),
            ),
        )
        // Added meanwhile by another call.
        if (podcastId <= 0) return podcasts.getByFeedUrl(feedUrl)!!.id
        episodes.insertAll(feed.episodes.map { it.toEntity(podcastId) })
        saveInlineChapters(podcastId, feed.episodes)
        return podcastId
    }

    /** Checks every subscribed feed for new episodes and, if auto-download is on, starts downloading them. */
    suspend fun refreshAll(): RefreshResult {
        val subscribed = podcasts.getSubscribed()
        var newEpisodes = 0
        var failed = 0
        for (podcast in subscribed) {
            try {
                newEpisodes += refresh(podcast)
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed for ${podcast.feedUrl}", e)
                failed++
            }
        }
        return RefreshResult(newEpisodes, failed, subscribed.size)
    }

    /** Returns how many new episodes the feed had. */
    private suspend fun refresh(podcast: Podcast): Int {
        val feed = fetchFeed(podcast.feedUrl)
        podcasts.updateFeedInfo(podcast.id, feed.title, feed.author, feed.artworkUrl, feed.description)
        val known = episodes.getGuids(podcast.id).toHashSet()
        val (old, fresh) = feed.episodes.distinctBy { it.guid }.partition { it.guid in known }
        episodes.updateAllMetadata(podcast.id, old)
        val ids = episodes.insertAll(fresh.map { it.toEntity(podcast.id) })
        saveInlineChapters(podcast.id, feed.episodes)
        val added = ids.filter { it > 0 }
        if (podcast.subscribed && settings.current.autoDownload) added.forEach { startDownload(it) }
        return added.size
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
            // State first, so the cancelled download doesn't put itself back to waiting.
            episodes.setDownload(episodeId, DownloadState.NONE, null)
            Downloads.cancel(context, episodeId)
            partialFile(episodeId).delete()
        }
    }

    /** Undoes [dismiss]: puts the episode back where it was, restarting its download if that was cancelled. */
    suspend fun restoreToQueue(episodeId: Long, queuePosition: Long) {
        episodes.setQueuePosition(episodeId, queuePosition)
        val episode = episodes.get(episodeId) ?: return
        if (episode.downloadState != DownloadState.DOWNLOADED) startDownload(episodeId)
    }

    /** Called when an episode plays to the end. */
    suspend fun markFinished(episodeId: Long) {
        episodes.markPlayed(episodeId)
        episodes.get(episodeId)?.let { stats.add(today(), it.podcastId, finished = 1) }
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

    /** Forgets podcasts opened from search (or unsubscribed from) that nothing uses any more. */
    suspend fun deleteUnusedPodcasts() = podcasts.deleteUnused(System.currentTimeMillis() - DAY_MS)

    suspend fun savePosition(episodeId: Long, positionMs: Long) = episodes.setPosition(episodeId, positionMs)

    /** Called with the length the player found, which feeds often leave out or get wrong. */
    suspend fun saveDuration(episodeId: Long, durationMs: Long) = episodes.setDuration(episodeId, durationMs)

    /** Called whenever an episode starts playing, so it moves to the top of the history. */
    suspend fun markPlaying(episodeId: Long) = episodes.setLastPlayed(episodeId, System.currentTimeMillis())

    // Statistics.

    fun observeStats(fromDay: Long, toDay: Long) = stats.observeByPodcast(fromDay, toDay)
    fun observeFirstStatsDay() = stats.observeFirstDay()
    fun observeDownloadedFiles() = episodes.observeLocalPaths()

    /** Called by the player every few seconds while it plays, and when it stops. */
    suspend fun recordListening(episodeId: Long, listenedMs: Long, silenceSavedMs: Long) {
        if (listenedMs <= 0 && silenceSavedMs <= 0) return
        val episode = episodes.get(episodeId) ?: return
        stats.add(today(), episode.podcastId, listenedMs = listenedMs, silenceSavedMs = silenceSavedMs)
    }

    private fun today() = LocalDate.now().toEpochDay()

    // Download bookkeeping, used by DownloadWorker.

    fun downloadFile(episodeId: Long): File {
        val dir = context.getExternalFilesDir("episodes") ?: File(context.filesDir, "episodes")
        dir.mkdirs()
        return File(dir, "$episodeId.audio")
    }

    /** Where a download in progress is written; kept between attempts so it can resume. */
    fun partialFile(episodeId: Long) = File(downloadFile(episodeId).path + ".part")

    suspend fun markDownloadWaiting(episodeId: Long) = episodes.markDownloadWaiting(episodeId)

    /**
     * Re-enqueues downloads that are waiting, so they pick up a changed Wi-Fi only setting. One
     * already running carries on.
     */
    suspend fun requeueWaitingDownloads() {
        for (id in episodes.getWaitingDownloads()) Downloads.enqueue(context, id, replace = true)
    }

    suspend fun getEpisodeEntity(id: Long) = episodes.get(id)

    suspend fun setDownloadState(episodeId: Long, state: DownloadState, localPath: String? = null) =
        episodes.setDownload(episodeId, state, localPath)

    suspend fun onDownloadComplete(episodeId: Long, file: File) =
        episodes.setDownload(episodeId, DownloadState.DOWNLOADED, file.absolutePath)

    /** Counts a finished download towards the day's statistics. */
    suspend fun recordDownload(episodeId: Long, bytes: Long) {
        val episode = episodes.get(episodeId) ?: return
        stats.add(today(), episode.podcastId, downloadedBytes = bytes)
    }

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
