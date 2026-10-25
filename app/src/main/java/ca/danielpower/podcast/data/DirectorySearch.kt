package ca.danielpower.podcast.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class DirectoryResult(
    val title: String,
    val author: String?,
    val feedUrl: String,
    val artworkUrl: String?,
)

/**
 * The feed URL in a search box's text, when it holds one instead of search words. `feed://` and
 * `pcast://` links (as some sites publish) become https.
 */
fun feedUrlOrNull(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val lower = trimmed.lowercase()
    val url = when {
        lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
        lower.startsWith("feed://") || lower.startsWith("pcast://") -> "https://" + trimmed.substringAfter("://")
        else -> return null
    }
    return url.toHttpUrlOrNull()?.toString()
}

/** Searches the Apple Podcasts directory, which needs no API key. */
class DirectorySearch(private val client: OkHttpClient) {

    suspend fun search(term: String): List<DirectoryResult> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", "50")
            .addQueryParameter("term", term)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val results = JSONObject(response.body!!.string()).getJSONArray("results")
            (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                val feedUrl = item.optString("feedUrl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                DirectoryResult(
                    title = item.optString("collectionName").ifBlank { item.optString("trackName") },
                    author = item.optString("artistName").takeIf { it.isNotBlank() },
                    feedUrl = feedUrl,
                    artworkUrl = item.optString("artworkUrl600").ifBlank { item.optString("artworkUrl100") }
                        .takeIf { it.isNotBlank() },
                )
            }
                // The directory can list the same feed more than once; the results list is keyed by feed URL.
                .distinctBy { it.feedUrl }
        }
    }
}
