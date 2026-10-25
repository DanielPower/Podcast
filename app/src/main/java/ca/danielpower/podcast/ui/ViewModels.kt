package ca.danielpower.podcast.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.danielpower.podcast.PodcastApp
import ca.danielpower.podcast.data.Chapter
import ca.danielpower.podcast.data.DirectoryResult
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.data.Podcast
import ca.danielpower.podcast.data.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun <T> kotlinx.coroutines.flow.Flow<T>.stateIn(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    fun repo(app: Any?) = (app as PodcastApp).repository
    initializer { QueueViewModel(repo(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])) }
    initializer { PodcastsViewModel(repo(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])) }
    initializer { SearchViewModel(repo(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])) }
    initializer {
        EpisodeViewModel(
            repo(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]),
            createSavedStateHandle(),
        )
    }
    initializer {
        PodcastDetailViewModel(
            repo(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]),
            createSavedStateHandle(),
        )
    }
}

class QueueViewModel(private val repository: PodcastRepository) : ViewModel() {
    val queue: StateFlow<List<EpisodeWithPodcast>?> = repository.observeQueue().stateIn(this, null)

    fun reorder(orderedIds: List<Long>) = viewModelScope.launch { repository.reorderQueue(orderedIds) }
    fun dismiss(episodeId: Long) = viewModelScope.launch { repository.dismiss(episodeId) }
    fun moveToFront(episodeId: Long) = viewModelScope.launch { repository.moveToFront(episodeId) }
    fun retryDownload(episodeId: Long) = viewModelScope.launch { repository.retryDownload(episodeId) }
}

class PodcastsViewModel(private val repository: PodcastRepository) : ViewModel() {
    val podcasts: StateFlow<List<Podcast>?> = repository.observePodcasts().stateIn(this, null)
}

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<DirectoryResult> = emptyList(),
    val error: String? = null,
    val subscribing: Set<String> = emptySet(),
)

class SearchViewModel(private val repository: PodcastRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    val subscribed: StateFlow<List<String>> = repository.observeSubscribedFeedUrls().stateIn(this, emptyList())

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun clearSearch() = _state.update { it.copy(query = "", results = emptyList(), error = null) }

    fun search() {
        val term = _state.value.query.trim()
        if (term.isEmpty()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val results = repository.directory.search(term)
                _state.update { it.copy(loading = false, results = results) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Search failed: ${e.message}") }
            }
        }
    }

    fun subscribe(result: DirectoryResult) {
        _state.update { it.copy(subscribing = it.subscribing + result.feedUrl, error = null) }
        viewModelScope.launch {
            try {
                repository.subscribe(result.feedUrl)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't subscribe to ${result.title}: ${e.message}") }
            } finally {
                _state.update { it.copy(subscribing = it.subscribing - result.feedUrl) }
            }
        }
    }
}

class PodcastDetailViewModel(
    private val repository: PodcastRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val podcastId: Long = checkNotNull(savedStateHandle.get<Long>("podcastId"))

    val podcast: StateFlow<Podcast?> = repository.observePodcast(podcastId).stateIn(this, null)
    val episodes: StateFlow<List<EpisodeWithPodcast>> = repository.observeEpisodes(podcastId).stateIn(this, emptyList())

    fun addToQueue(episodeId: Long) = viewModelScope.launch { repository.addToQueue(episodeId) }
    fun unsubscribe(onDone: () -> Unit) = viewModelScope.launch {
        repository.unsubscribe(podcastId)
        onDone()
    }
}

class EpisodeViewModel(
    private val repository: PodcastRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val episodeId: Long = checkNotNull(savedStateHandle.get<Long>("episodeId"))

    val episode: StateFlow<EpisodeWithPodcast?> = repository.observeEpisode(episodeId).stateIn(this, null)
    val chapters: StateFlow<List<Chapter>> = repository.observeChapters(episodeId).stateIn(this, emptyList())

    /** 0-based place in the queue, or null when not queued. */
    val queueIndex: StateFlow<Int?> = repository.observeQueue()
        .map { queue -> queue.indexOfFirst { it.episode.id == episodeId }.takeIf { it >= 0 } }
        .stateIn(this, null)

    init {
        viewModelScope.launch {
            try {
                repository.loadChapters(episodeId)
            } catch (e: Exception) {
                Log.w("EpisodeViewModel", "Couldn't load chapters", e)
            }
        }
    }

    /** Makes sure the episode is queued (and downloading) before the player is asked to play it. */
    fun prepareToPlay() = viewModelScope.launch { repository.addToQueue(episodeId) }
    fun playNext() = viewModelScope.launch { repository.playNext(episodeId) }
    fun addToQueue() = viewModelScope.launch { repository.addToQueue(episodeId) }
    fun removeFromQueue() = viewModelScope.launch { repository.dismiss(episodeId) }
}
