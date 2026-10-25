package ca.danielpower.podcast

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ca.danielpower.podcast.data.AppSettings
import ca.danielpower.podcast.data.Chapter
import ca.danielpower.podcast.playback.PlayerConnection
import ca.danielpower.podcast.playback.rememberPlayerUiState
import ca.danielpower.podcast.ui.AppViewModelFactory
import ca.danielpower.podcast.ui.EpisodeScreen
import ca.danielpower.podcast.ui.EpisodeViewModel
import ca.danielpower.podcast.ui.HistoryScreen
import ca.danielpower.podcast.ui.HistoryViewModel
import ca.danielpower.podcast.ui.MiniPlayer
import ca.danielpower.podcast.ui.PaperNavBar
import ca.danielpower.podcast.ui.PaperNavItem
import ca.danielpower.podcast.ui.PodcastDetailScreen
import ca.danielpower.podcast.ui.PodcastDetailViewModel
import ca.danielpower.podcast.ui.PodcastTheme
import ca.danielpower.podcast.ui.PodcastsScreen
import ca.danielpower.podcast.ui.PodcastsViewModel
import ca.danielpower.podcast.ui.QueueScreen
import ca.danielpower.podcast.ui.QueueViewModel
import ca.danielpower.podcast.ui.SearchScreen
import ca.danielpower.podcast.ui.SearchViewModel
import ca.danielpower.podcast.ui.SettingsScreen
import ca.danielpower.podcast.ui.isDark
import ca.danielpower.podcast.work.Refresh
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {

    private var connection: PlayerConnection? = null
    private val connectionState = mutableStateOf<PlayerConnection?>(null)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) Refresh.now(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val appSettings by settings.settings.collectAsStateWithLifecycle()
            val dark = appSettings.themeMode.isDark()
            // The theme can differ from the system's, so the status bar icons follow the theme.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PodcastTheme(appSettings.scheme, dark) {
                App(connectionState.value, appSettings, dark)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connection = PlayerConnection(this).also { connectionState.value = it }
    }

    override fun onStop() {
        connectionState.value = null
        connection?.release()
        connection = null
        super.onStop()
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Queue("queue", "Queue", Icons.AutoMirrored.Filled.QueueMusic),
    Podcasts("podcasts", "Podcasts", Icons.Default.GridView),
}

@Composable
private fun App(connection: PlayerConnection?, appSettings: AppSettings, dark: Boolean) {
    val navController = rememberNavController()
    val player = connection?.controller
    val playerState = rememberPlayerUiState(player)
    val context = LocalContext.current
    val playingChapters by remember(playerState.episodeId) {
        playerState.episodeId?.let { context.repository.observeChapters(it) } ?: flowOf(emptyList<Chapter>())
    }.collectAsStateWithLifecycle(emptyList())
    val openEpisode = { id: Long -> navController.navigate("episode/$id") { launchSingleTop = true } }
    val openSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } }

    // The mini player and tab bar sit underneath the screens. Tab screens stop above them, the
    // episode screen covers the mini player but stops above the tab bar, and Settings covers both.
    // That way nothing pops in or resizes while a screen opens or closes over the one below.
    var chromeHeight by remember { mutableStateOf(0.dp) }
    var tabBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { chromeHeight = with(density) { it.height.toDp() } },
        ) {
            MiniPlayer(
                state = playerState,
                player = player,
                chapters = playingChapters,
                skipBackSeconds = appSettings.skipBackSeconds,
                skipForwardSeconds = appSettings.skipForwardSeconds,
                onClick = { playerState.episodeId?.let(openEpisode) },
            )
            BottomBar(navController, Modifier.onSizeChanged { tabBarHeight = with(density) { it.height.toDp() } })
        }
        NavHost(
            navController = navController,
            startDestination = Tab.Queue.route,
            modifier = Modifier.fillMaxSize(),
            // Switching tabs fades. Pages opened from a tab (a podcast, search, Settings, an episode)
            // slide in from the right and slide back out to the right, like Android's own screens;
            // with gesture navigation the back swipe drags them.
            enterTransition = { if (opensPage()) slideInHorizontally(tween(PAGE_MS)) { it } else fadeIn(tween(FADE_MS)) },
            exitTransition = { if (opensPage()) slideOutHorizontally(tween(PAGE_MS)) { -it / 4 } else fadeOut(tween(FADE_MS)) },
            popEnterTransition = {
                if (closesPage()) slideInHorizontally(tween(PAGE_MS)) { -it / 4 } else fadeIn(tween(FADE_MS))
            },
            popExitTransition = { if (closesPage()) slideOutHorizontally(tween(PAGE_MS)) { it } else fadeOut(tween(FADE_MS)) },
        ) {
            composable(Tab.Queue.route) {
                TabPage(chromeHeight) {
                    val vm: QueueViewModel = viewModel(factory = AppViewModelFactory)
                    val queue by vm.queue.collectAsStateWithLifecycle()
                    QueueScreen(
                        queue = queue,
                        playerState = playerState,
                        playingChapters = playingChapters,
                        onOpen = openEpisode,
                        onReorder = vm::reorder,
                        onDismiss = vm::dismiss,
                        onRetryDownload = vm::retryDownload,
                        onOpenHistory = { navController.navigate(HISTORY_ROUTE) { launchSingleTop = true } },
                        onOpenSettings = openSettings,
                    )
                }
            }
            composable(HISTORY_ROUTE) {
                TabPage(chromeHeight) {
                    val vm: HistoryViewModel = viewModel(factory = AppViewModelFactory)
                    val history by vm.history.collectAsStateWithLifecycle()
                    HistoryScreen(
                        history = history,
                        onOpen = openEpisode,
                        onAddToQueue = vm::addToQueue,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Tab.Podcasts.route) {
                TabPage(chromeHeight) {
                    val vm: PodcastsViewModel = viewModel(factory = AppViewModelFactory)
                    val podcasts by vm.podcasts.collectAsStateWithLifecycle()
                    PodcastsScreen(
                        podcasts = podcasts,
                        onOpen = { navController.navigate("podcast/$it") },
                        onSearch = { navController.navigate(SEARCH_ROUTE) },
                        onRefresh = { Refresh.now(context) },
                        onOpenSettings = openSettings,
                    )
                }
            }
            composable(SETTINGS_ROUTE) {
                FullPage {
                    SettingsScreen(
                        settings = appSettings,
                        dark = dark,
                        onChange = { change ->
                            val before = context.settings.current
                            context.settings.update(change)
                            // Wi-Fi only changes the network the hourly refresh waits for.
                            if (context.settings.current.wifiOnly != before.wifiOnly) Refresh.schedulePeriodic(context)
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(SEARCH_ROUTE) {
                TabPage(chromeHeight) {
                    val vm: SearchViewModel = viewModel(factory = AppViewModelFactory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    val subscribed by vm.subscribed.collectAsStateWithLifecycle()
                    SearchScreen(
                        state = state,
                        subscribedFeedUrls = subscribed,
                        onQueryChange = vm::setQuery,
                        onSearch = vm::search,
                        onClear = vm::clearSearch,
                        onSubscribe = vm::subscribe,
                        onOpen = { result ->
                            val args = listOf(
                                "feedUrl" to result.feedUrl,
                                "title" to result.title,
                                "author" to result.author,
                                "artworkUrl" to result.artworkUrl,
                            ).mapNotNull { (name, value) -> value?.let { "$name=${Uri.encode(it)}" } }
                            navController.navigate("podcast?" + args.joinToString("&"))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(
                "podcast/{podcastId}",
                arguments = listOf(navArgument("podcastId") { type = NavType.LongType }),
            ) {
                TabPage(chromeHeight) { PodcastPage(openEpisode, onBack = { navController.popBackStack() }) }
            }
            // From search: the feed isn't necessarily subscribed to, or even loaded yet.
            composable(
                PODCAST_FROM_SEARCH_ROUTE,
                arguments = listOf("feedUrl", "title", "author", "artworkUrl").map { name ->
                    navArgument(name) {
                        type = NavType.StringType
                        nullable = true
                    }
                },
            ) {
                TabPage(chromeHeight) { PodcastPage(openEpisode, onBack = { navController.popBackStack() }) }
            }
            composable(
                EPISODE_ROUTE,
                arguments = listOf(navArgument("episodeId") { type = NavType.LongType }),
            ) {
                // Covers the mini player (it would only repeat the controls) but not the tab bar.
                FullPage(aboveHeight = tabBarHeight) {
                    val vm: EpisodeViewModel = viewModel(factory = AppViewModelFactory)
                    val episode by vm.episode.collectAsStateWithLifecycle()
                    val chapters by vm.chapters.collectAsStateWithLifecycle()
                    val queueIndex by vm.queueIndex.collectAsStateWithLifecycle()
                    EpisodeScreen(
                        item = episode,
                        chapters = chapters,
                        queueIndex = queueIndex,
                        player = player,
                        playerState = playerState,
                        skipBackSeconds = appSettings.skipBackSeconds,
                        skipForwardSeconds = appSettings.skipForwardSeconds,
                        speed = appSettings.playbackSpeed,
                        speedPresets = appSettings.speedPresets,
                        skipSilence = appSettings.skipSilence,
                        silenceKeepMs = appSettings.silenceKeepMs,
                        onSpeed = { speed -> context.settings.update { it.copy(playbackSpeed = speed) } },
                        onSkipSilence = { on -> context.settings.update { it.copy(skipSilence = on) } },
                        onBack = { navController.popBackStack() },
                        onPlay = { startMs ->
                            vm.prepareToPlay()
                            connection?.play(vm.episodeId, startMs)
                        },
                        onPlayNext = vm::playNext,
                        onAddToQueue = vm::addToQueue,
                        onRemoveFromQueue = vm::removeFromQueue,
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastPage(openEpisode: (Long) -> Unit, onBack: () -> Unit) {
    val vm: PodcastDetailViewModel = viewModel(factory = AppViewModelFactory)
    val podcast by vm.podcast.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    PodcastDetailScreen(
        podcast = podcast,
        preview = vm.preview,
        episodes = episodes,
        error = error,
        onRetry = vm::load,
        onSubscribe = vm::subscribe,
        onUnsubscribe = vm::unsubscribe,
        onAddToQueue = vm::addToQueue,
        onOpenEpisode = openEpisode,
        onBack = onBack,
    )
}

private const val EPISODE_ROUTE = "episode/{episodeId}"
private const val PODCAST_FROM_SEARCH_ROUTE = "podcast?feedUrl={feedUrl}&title={title}&author={author}&artworkUrl={artworkUrl}"

/** A podcast's page, opened from the subscriptions or from search. */
private fun isPodcastRoute(route: String) = route.startsWith("podcast/") || route.startsWith("podcast?")

private fun tabFor(route: String): Tab = when {
    route == SEARCH_ROUTE || isPodcastRoute(route) -> Tab.Podcasts
    else -> Tab.entries.firstOrNull { it.route == route } ?: Tab.Queue
}
private const val SETTINGS_ROUTE = "settings"
private const val SEARCH_ROUTE = "search"
private const val HISTORY_ROUTE = "history"
private const val FADE_MS = 200
private const val PAGE_MS = 300

/** A page opened on top of a tab, as opposed to a tab itself. */
private fun NavBackStackEntry.isPage(): Boolean =
    destination.route.let {
        it == SETTINGS_ROUTE || it == SEARCH_ROUTE || it == HISTORY_ROUTE || it == EPISODE_ROUTE ||
            it?.let(::isPodcastRoute) == true
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.opensPage() = targetState.isPage()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.closesPage() = initialState.isPage()

/**
 * A tab-level screen: below the status bar and above the mini player and tab bar. Opaque, so a
 * page sliding over another doesn't show the one underneath.
 */
@Composable
private fun TabPage(chromeHeight: Dp, content: @Composable () -> Unit) {
    val chrome = PaddingValues(bottom = chromeHeight)
    Box(
        Modifier
            .fillMaxSize()
            .padding(chrome)
            .consumeWindowInsets(chrome)
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        content()
    }
}

/**
 * An opaque screen that covers the mini player and tab bar, touches included, or with
 * [aboveHeight], everything but the bottom [aboveHeight] of them.
 */
@Composable
private fun FullPage(aboveHeight: Dp = 0.dp, content: @Composable () -> Unit) {
    val below = PaddingValues(bottom = aboveHeight)
    Box(
        Modifier
            .fillMaxSize()
            .padding(below)
            .consumeWindowInsets(below)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        content()
    }
}

@Composable
private fun BottomBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val backStack by navController.currentBackStackEntryAsState()
    // An episode belongs to the tab it was opened from.
    val current = if (backStack?.destination?.route == EPISODE_ROUTE) navController.previousBackStackEntry else backStack
    val currentTab = current?.destination?.route?.let(::tabFor)
    PaperNavBar(modifier) {
        for (tab in Tab.entries) {
            val selected = tab == currentTab
            PaperNavItem(
                label = tab.label,
                icon = tab.icon,
                selected = selected,
                onClick = {
                    // Tapping the open tab goes back to its top, closing an episode or podcast.
                    if (selected) {
                        navController.popBackStack(tab.route, inclusive = false)
                    } else {
                        // Leaving the Podcasts tab drops a search in progress, so it isn't restored
                        // (with the keyboard popping up) when the tab is opened again.
                        if (runCatching { navController.getBackStackEntry(SEARCH_ROUTE) }.isSuccess) {
                            navController.popBackStack(SEARCH_ROUTE, inclusive = true)
                        }
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}
