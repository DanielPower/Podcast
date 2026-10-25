package ca.danielpower.podcast

import android.app.Application
import ca.danielpower.podcast.data.AppDatabase
import ca.danielpower.podcast.data.PodcastRepository
import ca.danielpower.podcast.work.Refresh
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PodcastApp : Application() {
    lateinit var repository: PodcastRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        repository = PodcastRepository(this, AppDatabase.create(this), client)
        Refresh.schedulePeriodic(this)
    }
}

val android.content.Context.repository: PodcastRepository
    get() = (applicationContext as PodcastApp).repository
