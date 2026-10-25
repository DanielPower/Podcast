package ca.danielpower.podcast.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * How far each running download has got. Kept in memory rather than in the database: written there
 * twice a second, it re-ran every open episode query (the queue, a podcast's episode list, Now
 * Playing) and recomposed them each time, page transitions included.
 */
object DownloadProgress {
    data class Bytes(val downloaded: Long, val total: Long?)

    private val state = MutableStateFlow<Map<Long, Bytes>>(emptyMap())
    val all: StateFlow<Map<Long, Bytes>> = state.asStateFlow()

    fun set(episodeId: Long, downloaded: Long, total: Long?) =
        state.update { it + (episodeId to Bytes(downloaded, total)) }

    fun clear(episodeId: Long) = state.update { it - episodeId }
}
