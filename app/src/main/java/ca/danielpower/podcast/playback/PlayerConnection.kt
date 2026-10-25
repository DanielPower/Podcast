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

    /** Plays the given episode now. The caller should also move it to the front of the queue. */
    fun play(episodeId: Long) {
        val player = controller ?: return
        if (player.currentMediaItem?.mediaId != episodeId.toString()) {
            player.setMediaItem(MediaItem.Builder().setMediaId(episodeId.toString()).build())
            player.prepare()
        }
        player.play()
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

    fun update(p: Player) {
        val item = p.currentMediaItem
        state.episodeId = item?.mediaId?.toLongOrNull()
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

    LaunchedEffect(player, state.isPlaying) {
        while (player != null && state.isPlaying) {
            state.positionMs = player.currentPosition
            state.durationMs = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }
    return state
}
