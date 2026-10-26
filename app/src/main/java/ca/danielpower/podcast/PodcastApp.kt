package ca.danielpower.podcast

import android.app.Application
import ca.danielpower.podcast.data.AppDatabase
import ca.danielpower.podcast.data.PodcastRepository
import ca.danielpower.podcast.data.SettingsStore
import ca.danielpower.podcast.work.Refresh
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PodcastApp : Application() {
    lateinit var repository: PodcastRepository
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient.Builder()
            .podcastUserAgent()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        settings = SettingsStore(this)
        repository = PodcastRepository(this, AppDatabase.create(this), client, settings)
        Refresh.schedulePeriodic(this)
    }
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
