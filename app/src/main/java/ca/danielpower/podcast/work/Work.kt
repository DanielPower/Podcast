package ca.danielpower.podcast.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ca.danielpower.podcast.data.DownloadProgress
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.podcastUserAgent
import ca.danielpower.podcast.repository
import ca.danielpower.podcast.settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Any network, or only an unmetered one (Wi-Fi) when that setting is on. */
private fun networkConstraints(context: Context) = Constraints.Builder()
    .setRequiredNetworkType(if (context.settings.current.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
    .build()

object Refresh {
    private const val PERIODIC = "refresh-periodic"
    private const val NOW = "refresh-now"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(networkConstraints(context))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    const val KEY_NEW_EPISODES = "newEpisodes"
    const val KEY_FAILED_FEEDS = "failedFeeds"
    const val KEY_FEEDS = "feeds"

    /**
     * Checks for new episodes right away, on any network: feeds are small, and new episodes'
     * downloads still wait for Wi-Fi if that's set. Returns the work's id, to follow with [observe].
     */
    fun now(context: Context): UUID {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    fun observe(context: Context, id: UUID) = WorkManager.getInstance(context).getWorkInfoByIdFlow(id)
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = applicationContext.repository
        val result = repository.refreshAll()
        repository.deleteExpiredDownloads()
        repository.deleteUnusedPodcasts()
        return Result.success(
            workDataOf(
                Refresh.KEY_NEW_EPISODES to result.newEpisodes,
                Refresh.KEY_FAILED_FEEDS to result.failedFeeds,
                Refresh.KEY_FEEDS to result.feeds,
            ),
        )
    }
}

object Downloads {
    private const val KEY_EPISODE_ID = "episodeId"

    private fun name(episodeId: Long) = "download-$episodeId"

    /** Starts the episode's download, or with [replace], restarts it with the current network setting. */
    fun enqueue(context: Context, episodeId: Long, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(networkConstraints(context))
            .setInputData(workDataOf(KEY_EPISODE_ID to episodeId))
            .build()
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(name(episodeId), policy, request)
    }

    fun cancel(context: Context, episodeId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(name(episodeId))
    }

    internal fun episodeId(worker: CoroutineWorker) = worker.inputData.getLong(KEY_EPISODE_ID, -1)
}

/**
 * Downloads one episode's audio, then appends the episode to the queue. A download that's stopped
 * part way (no network, or Android's time limit on background work) resumes where it left off.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = applicationContext.repository
        val episodeId = Downloads.episodeId(this)
        val episode = repository.getEpisodeEntity(episodeId) ?: return Result.failure()
        if (episode.downloadState == DownloadState.DOWNLOADED) {
            repository.onDownloadComplete(episodeId, File(episode.localPath!!))
            return Result.success()
        }

        val target = repository.downloadFile(episodeId)
        val partial = repository.partialFile(episodeId)
        repository.setDownloadState(episodeId, DownloadState.DOWNLOADING)
        return try {
            withContext(Dispatchers.IO) {
                val resumeFrom = if (partial.exists()) partial.length() else 0L
                val request = Request.Builder().url(episode.audioUrl)
                    .apply { if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-") }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 416) {
                        // The partial file doesn't fit the file on the server any more: start over.
                        partial.delete()
                        throw IOException("HTTP 416")
                    }
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    // 206 continues the partial file; a 200 means the server sent the whole file.
                    val resuming = response.code == 206
                    if (resuming && contentRangeStart(response.header("Content-Range")) != resumeFrom) {
                        partial.delete()
                        throw IOException("Unexpected Content-Range")
                    }
                    val body = response.body!!
                    val already = if (resuming) resumeFrom else 0L
                    val total = body.contentLength().takeIf { it > 0 }?.plus(already)
                    var downloaded = already
                    var lastReport = 0L
                    DownloadProgress.set(episodeId, downloaded, total)
                    FileOutputStream(partial, resuming).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (!isStopped) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                                    lastReport = now
                                    DownloadProgress.set(episodeId, downloaded, total)
                                }
                            }
                        }
                    }
                }
                if (isStopped) throw IOException("Download stopped")
                if (!partial.renameTo(target)) throw IOException("Could not move download into place")
            }
            repository.onDownloadComplete(episodeId, target)
            repository.recordDownload(episodeId, target.length())
            Result.success()
        } catch (e: Exception) {
            if (isStopped) {
                // Cancelled (the episode was dismissed, which also deletes the partial file), or
                // stopped by Android, which runs it again later. Either way it isn't downloading now.
                repository.markDownloadWaiting(episodeId)
                Result.failure()
            } else if (runAttemptCount < 3) {
                repository.setDownloadState(episodeId, DownloadState.QUEUED)
                Result.retry()
            } else {
                repository.setDownloadState(episodeId, DownloadState.FAILED)
                Result.failure()
            }
        } finally {
            DownloadProgress.clear(episodeId)
        }
    }

    /** The first byte of a `Content-Range: bytes 1000-1999/2000` header, or null. */
    private fun contentRangeStart(header: String?): Long? =
        header?.removePrefix("bytes")?.trim()?.substringBefore('-')?.toLongOrNull()

    companion object {
        private const val PROGRESS_INTERVAL_MS = 500L
        private val client = OkHttpClient.Builder()
            .podcastUserAgent()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }
}
