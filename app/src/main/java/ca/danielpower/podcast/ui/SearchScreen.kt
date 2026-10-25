package ca.danielpower.podcast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.danielpower.podcast.data.DirectoryResult
import kotlinx.coroutines.flow.first

@Composable
fun SearchScreen(
    state: SearchState,
    subscribedFeedUrls: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSubscribe: (DirectoryResult) -> Unit,
    onOpen: (DirectoryResult) -> Unit,
    onBack: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Focus (and so the keyboard) only on first open, not when coming back from a podcast's page.
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            // The page is resumed once it has finished sliding in. Opening the keyboard before then
            // animates it, and resizes the page, in the middle of the slide.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            focusedOnce = true
            focus.requestFocus()
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(onBack) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search for a podcast") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSearch()
                }),
                trailingIcon = {
                    Row {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = {
                                onClear()
                                focus.requestFocus()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                        IconButton(onClick = {
                            keyboard?.hide()
                            onSearch()
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    state.error?.let { error ->
                        item {
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(state.results, key = { it.feedUrl }) { result ->
                        SearchResultRow(
                            result = result,
                            subscribed = result.feedUrl in subscribedFeedUrls,
                            subscribing = result.feedUrl in state.subscribing,
                            onSubscribe = { onSubscribe(result) },
                            onClick = { onOpen(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: DirectoryResult,
    subscribed: Boolean,
    subscribing: Boolean,
    onSubscribe: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = SideMargin, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Artwork(result.artworkUrl, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            result.author?.let {
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
