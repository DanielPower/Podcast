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

/** "1×", "1.25×", "0.9×": a speed without trailing zeros. */
fun formatSpeed(speed: Float): String =
    "%.2f".format(java.util.Locale.ROOT, speed).trimEnd('0').trimEnd('.') + "×"

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
    val playbackSpeed: Float = 1f,
    /** The speeds offered as one-tap buttons on Now Playing, in ascending order. */
    val speedPresets: List<Float> = listOf(1f, 1.25f, 1.5f),
    /** Shorten pauses: any silence longer than [silenceKeepMs] is cut down to that length. */
    val skipSilence: Boolean = false,
    val silenceKeepMs: Int = 200,
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
        val SPEED_CHOICES = listOf(0.75f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.75f, 2f, 2.5f, 3f)
        const val MAX_SPEED_PRESETS = 4
        val SILENCE_KEEP_CHOICES = listOf(100, 150, 200, 300, 500, 750, 1000)
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
            .putFloat(KEY_SPEED, next.playbackSpeed)
            .putString(KEY_SPEED_PRESETS, next.speedPresets.joinToString(","))
            .putBoolean(KEY_SKIP_SILENCE, next.skipSilence)
            .putInt(KEY_SILENCE_KEEP, next.silenceKeepMs)
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
            playbackSpeed = prefs.getFloat(KEY_SPEED, defaults.playbackSpeed),
            speedPresets = prefs.getString(KEY_SPEED_PRESETS, null)
                ?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.isNotEmpty() }
                ?: defaults.speedPresets,
            skipSilence = prefs.getBoolean(KEY_SKIP_SILENCE, defaults.skipSilence),
            silenceKeepMs = prefs.getInt(KEY_SILENCE_KEEP, defaults.silenceKeepMs),
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
        const val KEY_SPEED = "speed"
        const val KEY_SPEED_PRESETS = "speed_presets"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_SILENCE_KEEP = "silence_keep_ms"
    }
}
