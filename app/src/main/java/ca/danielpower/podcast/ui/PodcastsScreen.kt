package ca.danielpower.podcast.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import ca.danielpower.podcast.data.DirectoryResult
import ca.danielpower.podcast.data.feedUrlOrNull
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.Podcast
import coil.compose.AsyncImage

@Composable
fun PodcastsScreen(
    podcasts: List<Podcast>?,
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onSubscribe: (DirectoryResult) -> Unit,
    onOpenResult: (DirectoryResult) -> Unit,
    onOpen: (Long) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val focusManager = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }
    val searching = search.query.isNotBlank()
    // Back lets go of the search before it leaves the tab.
    BackHandler(enabled = search.query.isNotEmpty() || fieldFocused) {
        onClearSearch()
        focusManager.clearFocus()
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Podcasts", Icons.Default.GridView) {
                if (refreshing) {
                    // Same footprint as the button it replaces, so the header doesn't shift.
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check for new episodes")
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
            SearchField(
                query = search.query,
                onQueryChange = onQueryChange,
                onSearch = {
                    // Like other search screens: searching lets go of the field, which also hides the keyboard.
                    focusManager.clearFocus()
                    onSearch()
                },
                onClear = onClearSearch,
                onFocusChange = { fieldFocused = it },
            )
            when {
                searching -> SearchResults(
                    search = search,
                    podcasts = podcasts.orEmpty(),
                    onOpen = onOpen,
                    onOpenResult = onOpenResult,
                    onSubscribe = onSubscribe,
                )
                podcasts == null -> Unit
                podcasts.isEmpty() -> EmptyMessage("You're not subscribed to any podcasts yet.\nFind one with the search above.")
                else -> {
                    val grid = rememberLazyGridState()
                    DropFocusOnScroll(grid.isScrollInProgress)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        state = grid,
                        contentPadding = PaddingValues(start = SideMargin, end = SideMargin, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionLabel("Subscribed · ${podcasts.size}", Modifier.padding(top = 14.dp))
                        }
                        items(podcasts, key = { it.id }) { podcast ->
                            PodcastTile(podcast, onClick = { onOpen(podcast.id) })
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Scrolling lets go of the search field, which also hides the keyboard. */
@Composable
private fun DropFocusOnScroll(scrolling: Boolean) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(scrolling) {
        if (scrolling) focusManager.clearFocus()
    }
}

/** Searches the subscriptions as you type, and the directory (or a pasted feed URL) below them. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier
            .padding(horizontal = SideMargin, vertical = 4.dp)
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            },
        decorationBox = { field ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(PaperShape)
                    .border(
                        if (focused) 1.5.dp else 1.dp,
                        if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        PaperShape,
                    )
                    .background(MaterialTheme.podcastColors.raised)
                    .padding(start = 14.dp, end = 2.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search, or paste a feed URL",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onClear()
                        // Shown explicitly too: if the keyboard was closed with back, the field
                        // kept focus and focusing it again wouldn't bring the keyboard back.
                        focus.requestFocus()
                        keyboard?.show()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                } else {
                    // Keeps the field the same height with and without the x.
                    Spacer(Modifier.size(48.dp))
                }
            }
        },
    )
}

@Composable
private fun SearchResults(
    search: SearchState,
    podcasts: List<Podcast>,
    onOpen: (Long) -> Unit,
    onOpenResult: (DirectoryResult) -> Unit,
    onSubscribe: (DirectoryResult) -> Unit,
) {
    val term = search.query.trim()
    val feedUrl = remember(term) { feedUrlOrNull(term) }
    val matches = remember(podcasts, term, feedUrl) {
        if (feedUrl != null) emptyList()
        else podcasts.filter { it.title.contains(term, ignoreCase = true) || it.author?.contains(term, ignoreCase = true) == true }
    }
    val subscribedUrls = remember(podcasts) { podcasts.mapTo(HashSet()) { it.feedUrl } }
    // Podcasts already subscribed to when the results came in are left out, as they're listed above.
    // One subscribed to from here stays put and shows as subscribed.
    val alreadySubscribed = remember(search.results) { subscribedUrls }
    val directory = remember(search.results, alreadySubscribed) { search.results.filter { it.feedUrl !in alreadySubscribed } }
    val showDirectory = feedUrl == null && (search.loading || search.searchedFor != null || search.error != null)
    val list = rememberLazyListState()
    DropFocusOnScroll(list.isScrollInProgress)
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 16.dp)) {
        if (feedUrl != null) {
            item(key = "feed-label") { ResultsLabel("Feed URL") }
            item(key = "feed") {
                val result = DirectoryResult(title = "", author = null, feedUrl = feedUrl, artworkUrl = null)
                SearchResultRow(
                    result = result,
                    title = "Open this feed",
                    subtitle = feedUrl,
                    subscribed = feedUrl in subscribedUrls,
                    subscribing = feedUrl in search.subscribing,
                    onSubscribe = { onSubscribe(result) },
                    onClick = { onOpenResult(result) },
                )
            }
            search.error?.let { item(key = "error") { ErrorText(it) } }
        }
        if (matches.isNotEmpty()) {
            item(key = "mine-label") { ResultsLabel("Your podcasts") }
            items(matches, key = { "mine-${it.id}" }) { podcast ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(podcast.id) }
                        .padding(horizontal = SideMargin, vertical = 8.dp),
                ) {
                    Artwork(podcast.artworkUrl, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            highlight(podcast.title, term, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        podcast.author?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (showDirectory) {
            item(key = "directory-label") { ResultsLabel("Podcast directory", loading = search.loading) }
            search.error?.let { item(key = "error") { ErrorText(it) } }
            items(directory, key = { "directory-${it.feedUrl}" }) { result ->
                SearchResultRow(
                    result = result,
                    title = result.title,
                    subtitle = result.author,
                    subscribed = result.feedUrl in subscribedUrls,
                    subscribing = result.feedUrl in search.subscribing,
                    onSubscribe = { onSubscribe(result) },
                    onClick = { onOpenResult(result) },
                )
            }
            if (!search.loading && search.error == null && search.searchedFor == term && directory.isEmpty()) {
                item(key = "none") {
                    Text(
                        if (matches.isEmpty()) "No podcasts found" else "Nothing else in the directory",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = SideMargin, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** A section heading in the results, with room for a spinner so the rows below don't move when it shows. */
@Composable
private fun ResultsLabel(text: String, loading: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = SideMargin, end = SideMargin, top = 14.dp, bottom = 4.dp).height(20.dp),
    ) {
        SectionLabel(text, Modifier.weight(1f))
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.tertiary, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = SideMargin, vertical = 8.dp),
    )
}

/** [text] with each occurrence of [term] picked out in [color]. */
private fun highlight(text: String, term: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    if (term.isEmpty()) return@buildAnnotatedString
    var from = 0
    while (true) {
        val at = text.indexOf(term, from, ignoreCase = true)
        if (at < 0) break
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), at, at + term.length)
        from = at + term.length
    }
}

@Composable
private fun SearchResultRow(
    result: DirectoryResult,
    title: String,
    subtitle: String?,
    subscribed: Boolean,
    subscribing: Boolean,
    onSubscribe: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = SideMargin, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Artwork(result.artworkUrl, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            subscribing -> CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.tertiary, strokeWidth = 2.dp)
            subscribed -> Text("Subscribed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.podcastColors.done)
            else -> SecondaryButton(onClick = onSubscribe) { Text("Subscribe") }
        }
    }
}

@Composable
private fun PodcastTile(podcast: Podcast, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(PaperShape)
                .background(MaterialTheme.podcastColors.track),
        ) {
            AsyncImage(
                model = podcast.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            podcast.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        podcast.author?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
