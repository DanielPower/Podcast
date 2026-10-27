package ca.danielpower.podcast

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.WorkInfo
import ca.danielpower.podcast.data.AppSettings
import ca.danielpower.podcast.data.Chapter
import ca.danielpower.podcast.data.DirectoryResult
import ca.danielpower.podcast.data.DownloadProgress
import ca.danielpower.podcast.data.feedUrlOrNull
import ca.danielpower.podcast.playback.PlayerConnection
import ca.danielpower.podcast.playback.PlayerUiState
import ca.danielpower.podcast.playback.rememberPlayerUiState
import ca.danielpower.podcast.ui.AppViewModelFactory
import ca.danielpower.podcast.ui.EpisodeScreen
import ca.danielpower.podcast.ui.EpisodeViewModel
import ca.danielpower.podcast.ui.HistoryScreen
import ca.danielpower.podcast.ui.HistoryViewModel
import ca.danielpower.podcast.ui.MiniPlayer
import ca.danielpower.podcast.ui.PaperNavBar
import ca.danielpower.podcast.ui.PlaybackErrorMessages
import ca.danielpower.podcast.ui.PaperNavItem
import ca.danielpower.podcast.ui.PodcastDetailScreen
import ca.danielpower.podcast.ui.PodcastDetailViewModel
import ca.danielpower.podcast.ui.PodcastTheme
import ca.danielpower.podcast.ui.PodcastsScreen
import ca.danielpower.podcast.ui.PodcastsViewModel
import ca.danielpower.podcast.ui.QueueScreen
import ca.danielpower.podcast.ui.QueueViewModel
import ca.danielpower.podcast.ui.SettingsScreen
import ca.danielpower.podcast.ui.StatisticsScreen
import ca.danielpower.podcast.ui.StatisticsViewModel
import ca.danielpower.podcast.ui.isDark
import ca.danielpower.podcast.ui.podcastCount
import ca.danielpower.podcast.ui.rememberOnUnmeteredNetwork
import ca.danielpower.podcast.work.Refresh
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    // Connected for the activity's whole life rather than only while it's visible: reconnecting on
    // every return to the app made the mini player vanish and pop back, resizing every page.
    private var connection: PlayerConnection? = null
    private val connectionState = mutableStateOf<PlayerConnection?>(null)

    // Set by a tap on the media notification, until Now Playing has been opened.
    private val openNowPlaying = mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connection = PlayerConnection(this).also { connectionState.value = it }
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            Refresh.now(this)
            // Not when reopened from recents, which replays the intent that first started the app.
            openNowPlaying.value = intent?.action == ACTION_OPEN_NOW_PLAYING &&
                (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        }
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
                App(
                    connectionState.value,
                    appSettings,
                    dark,
                    openNowPlaying = openNowPlaying.value,
                    onNowPlayingOpened = { openNowPlaying.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_OPEN_NOW_PLAYING) openNowPlaying.value = true
    }

    override fun onDestroy() {
        connectionState.value = null
        connection?.release()
        connection = null
        super.onDestroy()
    }

    companion object {
        /** The media notification opens the app with this action to show Now Playing. */
        const val ACTION_OPEN_NOW_PLAYING = "ca.danielpower.podcast.OPEN_NOW_PLAYING"
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Queue("queue", "Queue", Icons.AutoMirrored.Filled.QueueMusic),
    Podcasts("podcasts", "Podcasts", Icons.Default.GridView),
    Statistics("statistics", "Statistics", Icons.Default.BarChart),
}

@Composable
private fun App(
    connection: PlayerConnection?,
    appSettings: AppSettings,
    dark: Boolean,
    openNowPlaying: Boolean,
    onNowPlayingOpened: () -> Unit,
) {
    val navController = rememberNavController()
    val player = connection?.controller
    val playerState = rememberPlayerUiState(player)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playingChapters by remember(playerState.episodeId) {
        playerState.episodeId?.let { context.repository.observeChapters(it) } ?: flowOf(emptyList<Chapter>())
    }.collectAsStateWithLifecycle(emptyList())
    val openEpisode = { id: Long -> navController.navigate("episode/$id") { launchSingleTop = true } }
    // Shown above the mini player, or on an episode's page above its controls.
    val playbackErrors = remember { SnackbarHostState() }
    PlaybackErrorMessages(player, playbackErrors)
    val onEpisodePage = navController.currentBackStackEntryAsState().value?.destination?.route == EPISODE_ROUTE
    val openSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } }

    // From a notification tap. On a cold start this waits for the player to connect, and the
    // episode is read when the effect runs, after the player state has caught up.
    LaunchedEffect(openNowPlaying, player) {
        if (!openNowPlaying || player == null) return@LaunchedEffect
        playerState.episodeId?.let(openEpisode)
        onNowPlayingOpened()
    }

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
                onClick = { playerState.episodeId?.let(openEpisode) },
            )
            BottomBar(navController, Modifier.onSizeChanged { tabBarHeight = with(density) { it.height.toDp() } })
        }
        NavHost(
            navController = navController,
            startDestination = Tab.Queue.route,
            modifier = Modifier.fillMaxSize(),
            // Switching tabs fades. Pages opened from a tab (a podcast, Settings, an episode)
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
                    val catchUps by vm.catchUps.collectAsStateWithLifecycle()
                    val downloadProgress by DownloadProgress.all.collectAsStateWithLifecycle()
                    val unmetered = rememberOnUnmeteredNetwork()
                    QueueScreen(
                        queue = queue,
                        catchUps = catchUps,
                        downloadProgress = downloadProgress,
                        playerState = playerState,
                        playingChapters = playingChapters,
                        onOpen = openEpisode,
                        onReorder = vm::reorder,
                        onSwitchTo = { id -> connection?.switchTo(id) },
                        onDismiss = vm::dismiss,
                        onUndoDismiss = { dismissed ->
                            vm.undoDismiss(dismissed)
                            if (dismissed.wasPlaying) connection?.switchTo(dismissed.episodeId)
                        },
                        onRetryDownload = vm::retryDownload,
                        waitingForWifi = appSettings.wifiOnly && !unmetered,
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
                    val snackbar = remember { SnackbarHostState() }
                    val scope = rememberCoroutineScope()
                    // The refresh the user asked for, followed until it ends and its result is shown.
                    var refreshId by rememberSaveable { mutableStateOf<String?>(null) }
                    val refresh by remember(refreshId) {
                        refreshId?.let { Refresh.observe(context, UUID.fromString(it)) } ?: flowOf(null)
                    }.collectAsStateWithLifecycle(null)
                    LaunchedEffect(refresh?.state) {
                        val info = refresh ?: return@LaunchedEffect
                        if (!info.state.isFinished) return@LaunchedEffect
                        refreshId = null
                        val message = refreshMessage(info)
                        // Not in this effect, which clearing refreshId restarts.
                        scope.launch { snackbar.showSnackbar(message) }
                    }
                    val search by vm.search.collectAsStateWithLifecycle()
                    val openResult = { result: DirectoryResult ->
                        val args = listOf(
                            "feedUrl" to result.feedUrl,
                            "title" to result.title,
                            "author" to result.author,
                            "artworkUrl" to result.artworkUrl,
                        ).mapNotNull { (name, value) -> value?.let { "$name=${Uri.encode(it)}" } }
                        navController.navigate("podcast?" + args.joinToString("&"))
                    }
                    PodcastsScreen(
                        podcasts = podcasts,
                        search = search,
                        onQueryChange = vm::setQuery,
                        // A pasted feed URL opens that podcast, for feeds the directory doesn't list.
                        onSearch = {
                            val feedUrl = feedUrlOrNull(search.query)
                            if (feedUrl != null) {
                                openResult(DirectoryResult(title = "", author = null, feedUrl = feedUrl, artworkUrl = null))
                            } else {
                                vm.searchNow()
                            }
                        },
                        onClearSearch = vm::clearSearch,
                        onSubscribe = vm::subscribe,
                        onOpenResult = openResult,
                        onOpen = { navController.navigate("podcast/$it") },
                        refreshing = refreshId != null,
                        onRefresh = { refreshId = Refresh.now(context).toString() },
                        onOpenSettings = openSettings,
                        snackbar = snackbar,
                    )
                }
            }
            composable(Tab.Statistics.route) {
                TabPage(chromeHeight) {
                    val vm: StatisticsViewModel = viewModel(factory = AppViewModelFactory)
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    StatisticsScreen(ui = ui, onRange = vm::setRange, onPrevious = vm::previous, onNext = vm::next)
                }
            }
            composable(SETTINGS_ROUTE) {
                FullPage {
                    val opmlImport by context.repository.opmlImport.collectAsStateWithLifecycle()
                    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) context.repository.importOpml(uri)
                    }
                    var exportMessage by remember { mutableStateOf<String?>(null) }
                    val exportLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("text/x-opml"),
                    ) { uri ->
                        if (uri != null) {
                            scope.launch {
                                exportMessage = try {
                                    "Saved ${podcastCount(context.repository.exportOpml(uri))}"
                                } catch (e: Exception) {
                                    "Couldn't save the file"
                                }
                            }
                        }
                    }
                    SettingsScreen(
                        settings = appSettings,
                        dark = dark,
                        onChange = { change ->
                            val before = context.settings.current
                            context.settings.update(change)
                            // Wi-Fi only changes the network the hourly refresh and waiting downloads wait for.
                            if (context.settings.current.wifiOnly != before.wifiOnly) {
                                Refresh.schedulePeriodic(context)
                                scope.launch { context.repository.requeueWaitingDownloads() }
                            }
                        },
                        opmlImport = opmlImport,
                        // Files shared as OPML come with all sorts of types, so any file can be picked.
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        exportMessage = exportMessage,
                        onExport = { exportLauncher.launch("podcasts.opml") },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(
                "podcast/{podcastId}",
                arguments = listOf(navArgument("podcastId") { type = NavType.LongType }),
            ) {
                TabPage(chromeHeight) { PodcastPage(openEpisode, connection, playerState, onBack = { navController.popBackStack() }) }
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
                TabPage(chromeHeight) { PodcastPage(openEpisode, connection, playerState, onBack = { navController.popBackStack() }) }
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
                        playbackErrors = playbackErrors,
                    )
                }
            }
        }
        if (!onEpisodePage) {
            SnackbarHost(playbackErrors, Modifier.align(Alignment.BottomCenter).padding(bottom = chromeHeight))
        }
    }
}

@Composable
private fun PodcastPage(
    openEpisode: (Long) -> Unit,
    connection: PlayerConnection?,
    playerState: PlayerUiState,
    onBack: () -> Unit,
) {
    val vm: PodcastDetailViewModel = viewModel(factory = AppViewModelFactory)
    val podcast by vm.podcast.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val catchUp by vm.catchUp.collectAsStateWithLifecycle()
    PodcastDetailScreen(
        podcast = podcast,
        preview = vm.preview,
        episodes = episodes,
        catchUp = catchUp,
        error = error,
        onRetry = vm::load,
        onSubscribe = { catchUp -> vm.subscribe(catchUp) },
        onUnsubscribe = vm::unsubscribe,
        onStartCatchUp = vm::startCatchingUp,
        onStopCatchUp = vm::stopCatchingUp,
        onAddToQueue = vm::addToQueue,
        // As on the episode page: queued if it wasn't, and the player moves it to the front.
        onPlay = { id ->
            vm.addToQueue(id)
            connection?.play(id)
        },
        playerState = playerState,
        onPause = { connection?.controller?.pause() },
        onResume = { connection?.controller?.play() },
        onOpenEpisode = openEpisode,
        onBack = onBack,
    )
}

private const val EPISODE_ROUTE = "episode/{episodeId}"
private const val PODCAST_FROM_SEARCH_ROUTE = "podcast?feedUrl={feedUrl}&title={title}&author={author}&artworkUrl={artworkUrl}"

/** What to tell the user once the refresh they asked for has ended. */
private fun refreshMessage(info: WorkInfo): String {
    val output = info.outputData
    val newEpisodes = output.getInt(Refresh.KEY_NEW_EPISODES, 0)
    val failed = output.getInt(Refresh.KEY_FAILED_FEEDS, 0)
    val feeds = output.getInt(Refresh.KEY_FEEDS, 0)
    return when {
        info.state != WorkInfo.State.SUCCEEDED -> "Couldn't check for new episodes"
        feeds > 0 && failed == feeds -> "Couldn't reach any feeds. Check your connection."
        newEpisodes == 0 && failed > 0 -> "No new episodes ($failed ${if (failed == 1) "feed" else "feeds"} couldn't be reached)"
        newEpisodes == 0 -> "No new episodes"
        newEpisodes == 1 -> "1 new episode"
        else -> "$newEpisodes new episodes"
    }
}

/** A podcast's page, opened from the subscriptions or from search. */
private fun isPodcastRoute(route: String) = route.startsWith("podcast/") || route.startsWith("podcast?")

private fun tabFor(route: String): Tab = when {
    isPodcastRoute(route) -> Tab.Podcasts
    else -> Tab.entries.firstOrNull { it.route == route } ?: Tab.Queue
}
private const val SETTINGS_ROUTE = "settings"
private const val HISTORY_ROUTE = "history"
private const val FADE_MS = 200
private const val PAGE_MS = 300

/** A page opened on top of a tab, as opposed to a tab itself. */
private fun NavBackStackEntry.isPage(): Boolean =
    destination.route.let {
        it == SETTINGS_ROUTE || it == HISTORY_ROUTE || it == EPISODE_ROUTE ||
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
                        // Switching tabs always lands on the tab's root page; pages opened in the
                        // tab we're leaving (podcast, episode, history...) are dropped, not saved.
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
    }
}
