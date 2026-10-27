package ca.danielpower.podcast.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import ca.danielpower.podcast.data.DownloadState
import ca.danielpower.podcast.repository
import kotlinx.coroutines.launch

/** Why an episode wouldn't play, as the snackbar puts it, and what its button does about it. */
enum class PlaybackProblem(val message: String, val action: String) {
    OFFLINE("Couldn't play. No connection.", "Retry"),
    REFUSED("Couldn't play. The server refused it.", "Retry"),
    DAMAGED("This download is damaged.", "Download again"),
    OTHER("Couldn't play this episode.", "Retry"),
}

/**
 * What went wrong, from the player's error code. A downloaded episode that won't play has a bad
 * file whatever the code says, so the fix offered is to download it again.
 */
fun playbackProblem(errorCode: Int, downloaded: Boolean): PlaybackProblem = when {
    downloaded -> PlaybackProblem.DAMAGED
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackProblem.OFFLINE
    errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
        errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> PlaybackProblem.REFUSED
    else -> PlaybackProblem.OTHER
}

/**
 * Says so in [snackbar] when the episode in the player can't play, which would otherwise look like
 * a play button that does nothing. Retry prepares the player again; Download again replaces the
 * file, keeping the episode's place in the queue.
 */
@Composable
fun PlaybackErrorMessages(player: Player?, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val episodeId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                scope.launch {
                    val episode = context.repository.getEpisode(episodeId)?.episode
                    val problem = playbackProblem(error.errorCode, episode?.downloadState == DownloadState.DOWNLOADED)
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(
                        problem.message,
                        actionLabel = problem.action,
                        duration = SnackbarDuration.Long,
                    )
                    if (result != SnackbarResult.ActionPerformed) return@launch
                    if (problem == PlaybackProblem.DAMAGED) {
                        context.repository.redownload(episodeId)
                    } else {
                        player.prepare()
                        player.play()
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}
