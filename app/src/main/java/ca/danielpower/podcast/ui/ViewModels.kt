package ca.danielpower.podcast.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.danielpower.podcast.PodcastApp
import ca.danielpower.podcast.data.DirectoryResult
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.data.Podcast
import ca.danielpower.podcast.data.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
