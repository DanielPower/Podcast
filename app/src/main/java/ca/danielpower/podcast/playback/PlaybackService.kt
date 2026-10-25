package ca.danielpower.podcast.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import ca.danielpower.podcast.MainActivity
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.repository
import ca.danielpower.podcast.settings
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays the first downloaded episode in the queue. When an episode ends it is removed from the
 * queue and the next downloaded one starts; episodes still downloading are skipped over. The
 * player only ever holds the one episode that is playing, so the queue in the database stays the
 * single source of truth for what comes next.
 *
 * It is a library service so Android Auto can browse the queue.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: Player
    private var session: MediaLibrarySession? = null
    private val scope = MainScope()

    private val currentEpisodeId: Long?
        get() = player.currentMediaItem?.mediaId?.toLongOrNull()

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = QueuePlayer(exoPlayer)
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
        val session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
        this.session = session

        scope.launch {
            repository.observeQueue()
                // Only react to changes in order or in what is playable, not to download progress.
                .distinctUntilChangedBy { queue -> queue.map { it.episode.id to it.isPlayable } }
                .collect { queue ->
                    syncWithQueue(queue)
                    session.notifyChildrenChanged(QUEUE_ID, queue.count { it.isPlayable }, null)
                }
        }
        scope.launch {
            while (true) {
                delay(5_000)
                if (player.isPlaying) saveCurrentPosition()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveCurrentPosition()
        scope.cancel()
        session?.release()
        session = null
        exoPlayer.release()
        super.onDestroy()
    }

    private fun syncWithQueue(queue: List<EpisodeWithPodcast>) {
        val current = currentEpisodeId
        val next = queue.firstOrNull { it.isPlayable }
        when {
            // The playing episode is still queued: leave it alone.
            current != null && queue.any { it.episode.id == current } -> Unit
            next == null -> {
                player.stop()
                player.clearMediaItems()
            }
            // Nothing loaded yet: cue up the next episode, paused.
            current == null -> load(next, playWhenReady = false)
            // The playing episode left the queue (finished, skipped or dismissed): move on,
            // carrying over whether we were playing.
            else -> load(next, playWhenReady = player.playWhenReady)
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

    /** Skip to next: takes the current episode out of the queue, so the next one plays. */
    private fun skipCurrent() {
        val id = currentEpisodeId ?: return
        saveCurrentPosition()
        scope.launch { repository.dismiss(id) }
    }

    private fun saveCurrentPosition() {
        val id = currentEpisodeId ?: return
        if (player.playbackState == Player.STATE_ENDED) return
        val position = player.currentPosition
        scope.launch { repository.savePosition(id, position) }
    }

    /** Advertises "next" even though the player only holds one item, and maps it to a skip. */
    private inner class QueuePlayer(player: Player) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build()

        override fun isCommandAvailable(command: Int): Boolean =
            command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                super.isCommandAvailable(command)

        override fun seekToNext() = skipCurrent()
        override fun seekToNextMediaItem() = skipCurrent()

        // Skip lengths come from Settings, so they can change while the player is running.
        override fun getSeekBackIncrement(): Long = settings.current.skipBackSeconds * 1000L
        override fun getSeekForwardIncrement(): Long = settings.current.skipForwardSeconds * 1000L

        override fun seekBack() = seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0))

        override fun seekForward() {
            val target = currentPosition + seekForwardIncrement
            val length = duration
            seekTo(if (length != C.TIME_UNSET) target.coerceAtMost(length) else target)
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browsableItem(ROOT_ID, "Podcasts"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val children = when (parentId) {
                ROOT_ID -> listOf(browsableItem(QUEUE_ID, "Queue"))
                QUEUE_ID -> repository.getQueue().filter { it.isPlayable }.map { it.toMediaItem() }
                else -> emptyList()
            }
            LibraryResult.ofItemList(children, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            val episode = mediaId.toLongOrNull()?.let { repository.getEpisode(it) }
            if (episode != null) {
                LibraryResult.ofItem(episode.toMediaItem(), null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = future {
            resolve(mediaItems).map { it.toMediaItem() }.toMutableList()
        }

        /**
         * Controllers (our UI, Android Auto) ask to play an episode by sending a MediaItem holding
         * only its id. Move it to the front of the queue, resolve it to the full item, and resume
         * from the saved position.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            saveCurrentPosition()
            return future {
                val episodes = resolve(mediaItems)
                episodes.firstOrNull()?.let { repository.moveToFront(it.episode.id) }
                MediaSession.MediaItemsWithStartPosition(
                    episodes.map { it.toMediaItem() },
                    0,
                    // An explicit position (playing from a chapter) wins over the saved one.
                    if (startPositionMs != C.TIME_UNSET) startPositionMs else episodes.firstOrNull()?.episode?.positionMs ?: 0,
                )
            }
        }

        private suspend fun resolve(mediaItems: List<MediaItem>): List<EpisodeWithPodcast> =
            mediaItems.mapNotNull { item ->
                val query = item.requestMetadata.searchQuery
                when {
                    item.mediaId.toLongOrNull() != null -> repository.getEpisode(item.mediaId.toLong())
                    query != null -> searchQueue(query)
                    else -> null
                }
            }

        /**
         * Voice search ("play <podcast>"): the first downloaded queue episode whose title or podcast
         * matches, or the head of the queue for a bare "play".
         */
        private suspend fun searchQueue(query: String): EpisodeWithPodcast? {
            val playable = repository.getQueue().filter { it.isPlayable }
            val q = query.trim()
            if (q.isEmpty()) return playable.firstOrNull()
            return playable.firstOrNull {
                it.podcast.title.contains(q, ignoreCase = true) || it.episode.title.contains(q, ignoreCase = true)
            } ?: playable.firstOrNull()
        }
    }

    private fun <T> future(block: suspend CoroutineScope.() -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    private fun browsableItem(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                .build(),
        )
        .build()

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
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .build(),
            )
            .build()
    }

    companion object {
        private const val ROOT_ID = "root"
        private const val QUEUE_ID = "queue"
    }
}
