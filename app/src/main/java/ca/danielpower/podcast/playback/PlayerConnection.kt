package ca.danielpower.podcast.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

/** Holds a MediaController connected to [PlaybackService] while the UI is visible. */
class PlayerConnection(context: Context) {
    var controller by mutableStateOf<MediaController?>(null)
        private set
    private var released = false

    private val future = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
    ).buildAsync()

    init {
        future.addListener({ if (!released) controller = future.get() }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Plays the given episode now; the service moves it to the front of the queue. Starts at
     * [startMs] if given, otherwise where it was left off.
     */
    fun play(episodeId: Long, startMs: Long? = null) {
        val player = controller ?: return
        if (player.currentMediaItem?.mediaId != episodeId.toString()) {
            val item = MediaItem.Builder().setMediaId(episodeId.toString()).build()
            if (startMs != null) player.setMediaItem(item, startMs) else player.setMediaItem(item)
            player.prepare()
        } else if (startMs != null) {
            player.seekTo(startMs)
        }
        player.play()
    }

    /** Loads the episode in place of the current one, playing only if the player was playing. */
    fun switchTo(episodeId: Long) {
        val player = controller ?: return
        val playWhenReady = player.playWhenReady
        player.setMediaItem(MediaItem.Builder().setMediaId(episodeId.toString()).build())
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    fun release() {
        released = true
        controller = null
        MediaController.releaseFuture(future)
    }
}

class PlayerUiState {
    var episodeId by mutableStateOf<Long?>(null)
    var title by mutableStateOf<String?>(null)
    var podcastTitle by mutableStateOf<String?>(null)
    var artworkUri by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
}

/** Mirrors the controller's state into Compose state, polling the position while playing. */
@Composable
fun rememberPlayerUiState(player: Player?): PlayerUiState {
    val state = remember { PlayerUiState() }
    // An episode the player has switched to but that is still loading, while the old one is shown.
    var loadingId by remember { mutableStateOf<Long?>(null) }

    fun update(p: Player, force: Boolean = false) {
        val item = p.currentMediaItem
        val id = item?.mediaId?.toLongOrNull()
        // Right after switching episodes the player has the new item but not yet its position,
        // duration or metadata. Keep showing the old episode until it has them, rather than
        // flashing a frame of empty progress and times. A slow stream gets [MaxHoldMs] at most.
        if (!force && id != null && state.episodeId != null && id != state.episodeId && loading(p)) {
            loadingId = id
            return
        }
        loadingId = null
        state.episodeId = id
        state.title = item?.mediaMetadata?.title?.toString()
        state.podcastTitle = item?.mediaMetadata?.artist?.toString()
        state.artworkUri = item?.mediaMetadata?.artworkUri?.toString()
        state.isPlaying = p.isPlaying
        state.positionMs = p.currentPosition
        state.durationMs = p.duration.coerceAtLeast(0)
    }

    DisposableEffect(player) {
        if (player == null) {
            state.episodeId = null
            return@DisposableEffect onDispose {}
        }
        update(player)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = update(player)
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, loadingId) {
        if (player != null && loadingId != null) {
            delay(MaxHoldMs)
            update(player, force = true)
        }
    }

    LaunchedEffect(player, state.isPlaying) {
        while (player != null && state.isPlaying) {
            if (player.currentMediaItem?.mediaId?.toLongOrNull() == state.episodeId) {
                state.positionMs = player.currentPosition
                state.durationMs = player.duration.coerceAtLeast(0)
            }
            delay(500)
        }
    }
    return state
}

/** How long the old episode stays shown while a newly switched-to one loads. */
private const val MaxHoldMs = 500L

/** Whether the player is still loading its current item and doesn't know its duration yet. */
private fun loading(p: Player): Boolean =
    p.playbackState == Player.STATE_BUFFERING && p.duration == C.TIME_UNSET
