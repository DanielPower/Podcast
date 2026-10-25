package ca.danielpower.podcast.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The colour schemes offered in Settings. Washi is Jiwa Jiwa's palette. */
enum class Scheme(val label: String, val blurb: String) {
    WASHI("Washi", "Paper, ink and vermilion"),
    AI("Ai", "Indigo-dyed cotton, cool and calm"),
    MATCHA("Matcha", "Green tea on pale celadon"),
    SUMI("Sumi", "Black ink only, no accent"),
}

enum class ThemeMode(val label: String) {
    AUTO("Auto"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class AppSettings(
    val scheme: Scheme = Scheme.WASHI,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val skipBackSeconds: Int = 10,
    val skipForwardSeconds: Int = 30,
    /** Download new episodes as soon as a refresh finds them (they then join the queue). */
    val autoDownload: Boolean = true,
    val wifiOnly: Boolean = false,
    /** How long a removed or finished episode's download is kept before it's deleted. */
    val keepDays: Int = 7,
) {
    companion object {
        val SKIP_BACK_CHOICES = listOf(5, 10, 15, 30)
        val SKIP_FORWARD_CHOICES = listOf(10, 15, 30, 45, 60)
        val KEEP_DAY_CHOICES = listOf(1, 3, 7, 14, 30)
    }
}

/** App settings, kept in SharedPreferences and exposed as a flow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    val current: AppSettings get() = state.value

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(state.value)
        prefs.edit()
            .putString(KEY_SCHEME, next.scheme.name)
            .putString(KEY_THEME, next.themeMode.name)
            .putInt(KEY_SKIP_BACK, next.skipBackSeconds)
            .putInt(KEY_SKIP_FORWARD, next.skipForwardSeconds)
            .putBoolean(KEY_AUTO_DOWNLOAD, next.autoDownload)
            .putBoolean(KEY_WIFI_ONLY, next.wifiOnly)
            .putInt(KEY_KEEP_DAYS, next.keepDays)
            .apply()
        state.value = next
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            scheme = enumOrDefault(prefs.getString(KEY_SCHEME, null), defaults.scheme),
            themeMode = enumOrDefault(prefs.getString(KEY_THEME, null), defaults.themeMode),
            skipBackSeconds = prefs.getInt(KEY_SKIP_BACK, defaults.skipBackSeconds),
            skipForwardSeconds = prefs.getInt(KEY_SKIP_FORWARD, defaults.skipForwardSeconds),
            autoDownload = prefs.getBoolean(KEY_AUTO_DOWNLOAD, defaults.autoDownload),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, defaults.wifiOnly),
            keepDays = prefs.getInt(KEY_KEEP_DAYS, defaults.keepDays),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_SCHEME = "scheme"
        const val KEY_THEME = "theme"
        const val KEY_SKIP_BACK = "skip_back_s"
        const val KEY_SKIP_FORWARD = "skip_forward_s"
        const val KEY_AUTO_DOWNLOAD = "auto_download"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_KEEP_DAYS = "keep_days"
    }
}
