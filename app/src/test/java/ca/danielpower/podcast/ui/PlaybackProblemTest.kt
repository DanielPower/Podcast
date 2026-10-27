package ca.danielpower.podcast.ui

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProblemTest {
    @Test
    fun streamingErrorsSayWhatWentWrong() {
        assertEquals(PlaybackProblem.OFFLINE, playbackProblem(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, false))
        assertEquals(PlaybackProblem.OFFLINE, playbackProblem(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, false))
        assertEquals(PlaybackProblem.REFUSED, playbackProblem(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, false))
        assertEquals(PlaybackProblem.OTHER, playbackProblem(PlaybackException.ERROR_CODE_DECODING_FAILED, false))
    }

    @Test
    fun aDownloadThatWontPlayIsDamaged() {
        assertEquals(PlaybackProblem.DAMAGED, playbackProblem(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, true))
        assertEquals(PlaybackProblem.DAMAGED, playbackProblem(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, true))
    }
}
