package ca.danielpower.podcast

import android.app.Application
import ca.danielpower.podcast.data.AppDatabase
import ca.danielpower.podcast.data.PodcastRepository
import ca.danielpower.podcast.data.SettingsStore
import ca.danielpower.podcast.work.Refresh
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PodcastApp : Application(), ImageLoaderFactory {
    private lateinit var client: OkHttpClient
    lateinit var repository: PodcastRepository
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        client = OkHttpClient.Builder()
            .podcastUserAgent()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        settings = SettingsStore(this)
        repository = PodcastRepository(this, AppDatabase.create(this), client, settings)
        Refresh.schedulePeriodic(this)
    }

    // Coil would otherwise use its own client with OkHttp's default User-Agent.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).okHttpClient(client).build()
}

/**
 * Some hosts (Buzzsprout, for one) answer 403 to OkHttp's default "okhttp/x.y.z"
 * User-Agent, so every request names the app instead.
 */
fun OkHttpClient.Builder.podcastUserAgent(): OkHttpClient.Builder = addInterceptor { chain ->
    chain.proceed(
        chain.request().newBuilder()
            .header("User-Agent", "Podcast/1.0 (Android; +https://github.com/DanielPower/podcast)")
            .build()
    )
}

val android.content.Context.repository: PodcastRepository
    get() = (applicationContext as PodcastApp).repository

val android.content.Context.settings: SettingsStore
    get() = (applicationContext as PodcastApp).settings
