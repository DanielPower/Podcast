package ca.danielpower.podcast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ca.danielpower.podcast.playback.PlayerConnection
import ca.danielpower.podcast.playback.rememberPlayerUiState
import ca.danielpower.podcast.ui.AppViewModelFactory
import ca.danielpower.podcast.ui.MiniPlayer
import ca.danielpower.podcast.ui.PodcastDetailScreen
import ca.danielpower.podcast.ui.PodcastDetailViewModel
import ca.danielpower.podcast.ui.PodcastTheme
import ca.danielpower.podcast.ui.PodcastsScreen
import ca.danielpower.podcast.ui.PodcastsViewModel
import ca.danielpower.podcast.ui.QueueScreen
import ca.danielpower.podcast.ui.QueueViewModel
import ca.danielpower.podcast.ui.SearchScreen
import ca.danielpower.podcast.ui.SearchViewModel
import ca.danielpower.podcast.work.Refresh

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
            PodcastTheme {
                App(connectionState.value)
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
private fun App(connection: PlayerConnection?) {
    val navController = rememberNavController()
    val player = connection?.controller
    val playerState = rememberPlayerUiState(player)

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(playerState, player)
                BottomBar(navController)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Queue.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Tab.Queue.route) {
                val vm: QueueViewModel = viewModel(factory = AppViewModelFactory)
                val queue by vm.queue.collectAsStateWithLifecycle()
                QueueScreen(
                    queue = queue,
                    playingEpisodeId = playerState.episodeId,
                    onPlay = { id ->
                        vm.moveToFront(id)
                        connection?.play(id)
                    },
                    onReorder = vm::reorder,
                    onDismiss = vm::dismiss,
                )
            }
            composable(Tab.Podcasts.route) {
                val vm: PodcastsViewModel = viewModel(factory = AppViewModelFactory)
                val podcasts by vm.podcasts.collectAsStateWithLifecycle()
                val context = LocalContext.current
                PodcastsScreen(
                    podcasts = podcasts,
                    onOpen = { navController.navigate("podcast/$it") },
                    onSearch = { navController.navigate("search") },
                    onRefresh = { Refresh.now(context) },
                )
            }
            composable("search") {
                val vm: SearchViewModel = viewModel(factory = AppViewModelFactory)
                val state by vm.state.collectAsStateWithLifecycle()
                val subscribed by vm.subscribed.collectAsStateWithLifecycle()
                SearchScreen(
                    state = state,
                    subscribedFeedUrls = subscribed,
                    onQueryChange = vm::setQuery,
                    onSearch = vm::search,
                    onSubscribe = vm::subscribe,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                "podcast/{podcastId}",
                arguments = listOf(navArgument("podcastId") { type = NavType.LongType }),
            ) {
                val vm: PodcastDetailViewModel = viewModel(factory = AppViewModelFactory)
                val podcast by vm.podcast.collectAsStateWithLifecycle()
                val episodes by vm.episodes.collectAsStateWithLifecycle()
                PodcastDetailScreen(
                    podcast = podcast,
                    episodes = episodes,
                    onAddToQueue = vm::addToQueue,
                    onUnsubscribe = { vm.unsubscribe { navController.popBackStack() } },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    NavigationBar {
        for (tab in Tab.entries) {
            val selected = destination?.hierarchy?.any { it.route == tab.route } == true ||
                (tab == Tab.Podcasts && destination?.route?.let { it == "search" || it.startsWith("podcast/") } == true)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
