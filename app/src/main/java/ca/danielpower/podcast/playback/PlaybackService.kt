package ca.danielpower.podcast.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import ca.danielpower.podcast.MainActivity
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.repository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays the head of the queue. When an episode ends it is removed from the queue and the
 * new head starts playing. The player only ever holds the one episode that is playing, so
 * the queue in the database stays the single source of truth for what comes next.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = MainScope()

    private val currentEpisodeId: Long?
        get() = player.currentMediaItem?.mediaId?.toLongOrNull()

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEpisodeEnded()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveCurrentPosition()
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        scope.launch {
            repository.observeQueue()
                .distinctUntilChangedBy { queue -> queue.map { it.episode.id } }
                .collect(::syncWithQueue)
        }
        scope.launch {
            while (true) {
                delay(5_000)
                if (player.isPlaying) saveCurrentPosition()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveCurrentPosition()
        scope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private fun syncWithQueue(queue: List<EpisodeWithPodcast>) {
        val current = currentEpisodeId
        val head = queue.firstOrNull()
        when {
            head == null -> {
                player.stop()
                player.clearMediaItems()
            }
            // Nothing loaded yet: cue up the head, paused.
            current == null -> load(head, playWhenReady = false)
            // The playing episode left the queue (finished or dismissed): move on to the next one,
            // carrying over whether we were playing.
            queue.none { it.episode.id == current } -> load(head, playWhenReady = player.playWhenReady)
        }
    }

    private fun load(item: EpisodeWithPodcast, playWhenReady: Boolean) {
        player.setMediaItem(item.toMediaItem(), item.episode.positionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    private fun onEpisodeEnded() {
        val id = currentEpisodeId ?: return
        scope.launch { repository.markFinished(id) }
    }

    private fun saveCurrentPosition() {
        val id = currentEpisodeId ?: return
        if (player.playbackState == Player.STATE_ENDED) return
        val position = player.currentPosition
        scope.launch { repository.savePosition(id, position) }
    }

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * Controllers (our UI) ask to play an episode by sending a MediaItem holding only its
         * id. Resolve it to the full item and resume from the saved position.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            saveCurrentPosition()
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val episodes = mediaItems.mapNotNull { item ->
                        item.mediaId.toLongOrNull()?.let { repository.getEpisode(it) }
                    }
                    result.set(
                        MediaSession.MediaItemsWithStartPosition(
                            episodes.map { it.toMediaItem() },
                            0,
                            episodes.firstOrNull()?.episode?.positionMs ?: 0,
                        ),
                    )
                } catch (e: Exception) {
                    result.setException(e)
                }
            }
            return result
        }
    }

    private fun EpisodeWithPodcast.toMediaItem(): MediaItem {
        val uri = episode.localPath?.let { File(it) }?.takeIf { it.exists() }?.toUri()
            ?: episode.audioUrl.toUri()
        return MediaItem.Builder()
            .setMediaId(episode.id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(podcast.title)
                    .setArtworkUri(artworkUrl?.toUri())
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .build(),
            )
            .build()
    }
}
