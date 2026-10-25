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
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private val networkConstraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

object Refresh {
    private const val PERIODIC = "refresh-periodic"
    private const val NOW = "refresh-now"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun now(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.repository.refreshAll()
        return Result.success()
    }
}

object Downloads {
    private const val KEY_EPISODE_ID = "episodeId"

    private fun name(episodeId: Long) = "download-$episodeId"

    fun enqueue(context: Context, episodeId: Long) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(networkConstraints)
            .setInputData(workDataOf(KEY_EPISODE_ID to episodeId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(episodeId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, episodeId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(name(episodeId))
    }

    internal fun episodeId(worker: CoroutineWorker) = worker.inputData.getLong(KEY_EPISODE_ID, -1)
}

/** Downloads one episode's audio, then appends the episode to the queue. */
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
        val partial = File(target.path + ".part")
        repository.setDownloadState(episodeId, DownloadState.DOWNLOADING)
        return try {
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(episode.audioUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    partial.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                }
                if (isStopped) throw IOException("Download cancelled")
                if (!partial.renameTo(target)) throw IOException("Could not move download into place")
            }
            repository.onDownloadComplete(episodeId, target)
            Result.success()
        } catch (e: Exception) {
            partial.delete()
            if (isStopped) {
                // Cancelled (for example the episode was dismissed); leave state to whoever cancelled.
                Result.failure()
            } else if (runAttemptCount < 3) {
                repository.setDownloadState(episodeId, DownloadState.QUEUED)
                Result.retry()
            } else {
                repository.setDownloadState(episodeId, DownloadState.FAILED)
                Result.failure()
            }
        }
    }

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }
}
