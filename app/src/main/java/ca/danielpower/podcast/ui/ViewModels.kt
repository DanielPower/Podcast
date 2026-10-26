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
import ca.danielpower.podcast.data.CatchUpProgress
import ca.danielpower.podcast.data.Chapter
import ca.danielpower.podcast.data.DirectoryResult
import ca.danielpower.podcast.data.EpisodeWithPodcast
import ca.danielpower.podcast.data.Podcast
import ca.danielpower.podcast.data.PodcastRepository
import ca.danielpower.podcast.data.PodcastStats
import ca.danielpower.podcast.data.feedUrlOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    initializer { HistoryViewModel(repo(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])) }
    initializer { StatisticsViewModel(repo(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])) }
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

    /** How far each podcast catching up has got, by the episode it's up to. */
    val catchUps: StateFlow<Map<Long, CatchUpProgress>> = repository.observeCatchUps()
        .map { list -> list.associateBy { it.episodeId } }
        .stateIn(this, emptyMap())

    fun reorder(orderedIds: List<Long>) = viewModelScope.launch { repository.reorderQueue(orderedIds) }
    fun dismiss(episodeId: Long) = viewModelScope.launch { repository.dismiss(episodeId) }
    fun undoDismiss(dismissed: Dismissed) =
        viewModelScope.launch { repository.restoreToQueue(dismissed.episodeId, dismissed.queuePosition) }
    fun moveToFront(episodeId: Long) = viewModelScope.launch { repository.moveToFront(episodeId) }
    fun retryDownload(episodeId: Long) = viewModelScope.launch { repository.retryDownload(episodeId) }
}

class HistoryViewModel(private val repository: PodcastRepository) : ViewModel() {
    val history: StateFlow<List<EpisodeWithPodcast>?> = repository.observeHistory().stateIn(this, null)

    fun addToQueue(episodeId: Long) = viewModelScope.launch { repository.addToQueue(episodeId) }
}

/** Everything the Statistics tab shows for the chosen period. */
data class StatsUi(
    val period: StatsPeriod,
    val today: LocalDate,
    /** The first day anything was recorded, or null if nothing has been yet. */
    val firstDay: LocalDate?,
    val byPodcast: List<PodcastStats>,
    /** The size of every download still on the phone, whenever it was downloaded. */
    val onPhoneBytes: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(private val repository: PodcastRepository) : ViewModel() {
    private val choice = MutableStateFlow(StatsRange.MONTH to 0)

    private val onPhoneBytes = repository.observeDownloadedFiles()
        .map { paths -> withContext(Dispatchers.IO) { paths.sumOf { File(it).length() } } }

    val ui: StateFlow<StatsUi?> = choice
        .flatMapLatest { (range, offset) ->
            val today = LocalDate.now()
            val period = StatsPeriod.of(range, offset, today)
            combine(
                repository.observeStats(period.from.toEpochDay(), period.to.toEpochDay()),
                repository.observeFirstStatsDay(),
                onPhoneBytes,
            ) { byPodcast, firstDay, bytes ->
                StatsUi(period, today, firstDay?.let(LocalDate::ofEpochDay), byPodcast, bytes)
            }
        }
        .stateIn(this, null)

    fun setRange(range: StatsRange) = choice.update { (current, offset) -> range to if (range == current) offset else 0 }
    fun previous() = choice.update { (range, offset) -> range to offset - 1 }
    fun next() = choice.update { (range, offset) -> range to minOf(offset + 1, 0) }
}

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    /** The directory's results for [searchedFor]; kept while a newer search loads, so the list doesn't jump. */
    val results: List<DirectoryResult> = emptyList(),
    val searchedFor: String? = null,
    val error: String? = null,
    val subscribing: Set<String> = emptySet(),
)

/** The subscriptions, and the search above them: subscriptions filter as you type, the directory after a pause. */
class PodcastsViewModel(private val repository: PodcastRepository) : ViewModel() {
    val podcasts: StateFlow<List<Podcast>?> = repository.observePodcasts().stateIn(this, null)

    private val _search = MutableStateFlow(SearchState())
    val search = _search.asStateFlow()
    private var searchJob: Job? = null

    fun setQuery(query: String) {
        _search.update { it.copy(query = query) }
        val term = query.trim()
        when {
            term.length < MIN_SEARCH_LENGTH || feedUrlOrNull(term) != null -> {
                searchJob?.cancel()
                _search.update { it.copy(loading = false, results = emptyList(), searchedFor = null, error = null) }
            }
            // Typing a space, say, doesn't change what's searched for.
            term != _search.value.searchedFor -> searchDirectory(term, SEARCH_DELAY_MS)
        }
    }

    /** The keyboard's search key: searches now instead of after the pause. */
    fun searchNow() {
        val term = _search.value.query.trim()
        if (term.isEmpty() || feedUrlOrNull(term) != null) return
        val state = _search.value
        if (term == state.searchedFor && state.error == null && !state.loading) return
        searchDirectory(term, 0)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.update { it.copy(query = "", loading = false, results = emptyList(), searchedFor = null, error = null) }
    }

    private fun searchDirectory(term: String, delayMs: Long) {
        // A newer search cancels the older one, so its results can't land after the newer ones.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(delayMs)
            _search.update { it.copy(loading = true, error = null) }
            try {
                val results = repository.directory.search(term)
                _search.update { it.copy(loading = false, results = results, searchedFor = term) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.update {
                    it.copy(loading = false, results = emptyList(), searchedFor = term, error = "Search failed: ${e.message}")
                }
            }
        }
    }

    fun subscribe(result: DirectoryResult, catchUp: Boolean) {
        _search.update { it.copy(subscribing = it.subscribing + result.feedUrl, error = null) }
        viewModelScope.launch {
            try {
                repository.subscribe(result.feedUrl, catchUp)
            } catch (e: Exception) {
                _search.update { it.copy(error = "Couldn't subscribe to ${result.title.ifEmpty { "this feed" }}: ${e.message}") }
            } finally {
                _search.update { it.copy(subscribing = it.subscribing - result.feedUrl) }
            }
        }
    }

    private companion object {
        const val MIN_SEARCH_LENGTH = 2
        const val SEARCH_DELAY_MS = 200L
    }
}

/**
 * A podcast's page, opened from the subscriptions by id or from search by feed URL. From search, the
 * feed is loaded first (unsubscribed), and the directory's title and artwork show meanwhile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastDetailViewModel(
    private val repository: PodcastRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val feedUrl: String? = savedStateHandle["feedUrl"]
    private val podcastId = MutableStateFlow(savedStateHandle.get<Long>("podcastId"))

    val preview: DirectoryResult? = feedUrl?.let {
        DirectoryResult(
            title = savedStateHandle["title"] ?: "",
            author = savedStateHandle["author"],
            feedUrl = it,
            artworkUrl = savedStateHandle["artworkUrl"],
        )
    }

    val podcast: StateFlow<Podcast?> = podcastId
        .flatMapLatest { id -> id?.let(repository::observePodcast) ?: flowOf(null) }
        .stateIn(this, null)
    val episodes: StateFlow<List<EpisodeWithPodcast>> = podcastId
        .flatMapLatest { id -> id?.let(repository::observeEpisodes) ?: flowOf(emptyList()) }
        .stateIn(this, emptyList())

    /** How far the podcast is through catching up, or null when it isn't. */
    val catchUp: StateFlow<CatchUpProgress?> = combine(podcastId, repository.observeCatchUps()) { id, list ->
        list.firstOrNull { it.podcastId == id }
    }.stateIn(this, null)

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        load()
    }

    /** Loads the feed of a podcast opened from search. */
    fun load() {
        val url = feedUrl ?: return
        _error.value = null
        viewModelScope.launch {
            try {
                podcastId.value = repository.open(url)
            } catch (e: Exception) {
                _error.value = "Couldn't load this podcast: ${e.message}"
            }
        }
    }

    fun addToQueue(episodeId: Long) = viewModelScope.launch { repository.addToQueue(episodeId) }
    fun subscribe(catchUp: Boolean) = viewModelScope.launch { podcastId.value?.let { repository.subscribe(it, catchUp) } }
    fun startCatchingUp() = viewModelScope.launch { podcastId.value?.let { repository.startCatchingUp(it) } }
    fun stopCatchingUp() = viewModelScope.launch { podcastId.value?.let { repository.stopCatchingUp(it) } }
    fun unsubscribe() = viewModelScope.launch { podcastId.value?.let { repository.unsubscribe(it) } }
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
}
