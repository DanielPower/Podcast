package ca.danielpower.podcast.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.R
import ca.danielpower.podcast.data.Scheme
import ca.danielpower.podcast.data.ThemeMode

// The look follows Jiwa Jiwa's "Washi" design: paper and ink, one accent, small square-ish corners,
// and one job per colour. In every scheme:
//   primary          the accent: what's playing, the main control, the selected tab
//   secondary        quiet links (the podcast name on an episode, links in show notes)
//   tertiary         in progress (downloading)
//   podcastColors.done  finished (played)
//   error            remove, failed
//   onSurface        ink: everything that only needs reading

/** Zen Kaku Gothic New, the sans serif Jiwa Jiwa uses for everything. */
val Gothic = FontFamily(
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_gothic_new_bold, FontWeight.Bold),
)

/** One scheme in one mode. Every text colour is at least 4.5:1 on [bg] and [raised]. */
@Immutable
data class Palette(
    val bg: Color,
    val raised: Color,
    val ink: Color,
    val soft: Color,
    val line: Color,
    val track: Color,
    val accent: Color,
    val onAccent: Color,
    /** Behind the accent's "this one", like the chapter playing now. */
    val tint: Color,
    val onTint: Color,
    val link: Color,
    val progress: Color,
    val done: Color,
    val danger: Color,
    val dangerBg: Color,
    val dark: Boolean,
)

fun Scheme.palette(dark: Boolean): Palette = when (this) {
    Scheme.WASHI -> if (!dark) {
        Palette(
            bg = Color(0xFFF6ECDA), raised = Color(0xFFFCF6EA), ink = Color(0xFF2A1F17), soft = Color(0xFF6A5848),
            line = Color(0xFFE3D3B9), track = Color(0xFFE6D5BA), accent = Color(0xFFB5381C), onAccent = Color(0xFFFCF6EA),
            tint = Color(0xFFF5D8C8), onTint = Color(0xFF5A1A0C), link = Color(0xFF2E5B9E), progress = Color(0xFF77560F),
            done = Color(0xFF3D6936), danger = Color(0xFF8E2643), dangerBg = Color(0xFFF3D6DC), dark = false,
        )
    } else {
        Palette(
            bg = Color(0xFF1E1711), raised = Color(0xFF2A2119), ink = Color(0xFFF1E5CF), soft = Color(0xFFB8A68F),
            line = Color(0xFF433629), track = Color(0xFF3F3226), accent = Color(0xFFE8704F), onAccent = Color(0xFF1E1711),
            tint = Color(0xFF52200F), onTint = Color(0xFFF7D2C2), link = Color(0xFF93B2D3), progress = Color(0xFFDDB862),
            done = Color(0xFF97C485), danger = Color(0xFFF1859A), dangerBg = Color(0xFF4E1A26), dark = true,
        )
    }
    Scheme.AI -> if (!dark) {
        Palette(
            bg = Color(0xFFEEF1F4), raised = Color(0xFFF8FAFC), ink = Color(0xFF16223A), soft = Color(0xFF4E5D73),
            line = Color(0xFFD5DCE5), track = Color(0xFFD8DFE8), accent = Color(0xFF2E4E8F), onAccent = Color(0xFFF8FAFC),
            tint = Color(0xFFDCE4F3), onTint = Color(0xFF14264A), link = Color(0xFF1F6470), progress = Color(0xFF8A5A0C),
            done = Color(0xFF2F6A4A), danger = Color(0xFF9B2335), dangerBg = Color(0xFFF4D7DB), dark = false,
        )
    } else {
        Palette(
            bg = Color(0xFF111826), raised = Color(0xFF1A2333), ink = Color(0xFFE4EAF3), soft = Color(0xFF9AA8BC),
            line = Color(0xFF2A3546), track = Color(0xFF2B3649), accent = Color(0xFF8FB0EE), onAccent = Color(0xFF111826),
            tint = Color(0xFF1E2B45), onTint = Color(0xFFD9E4FA), link = Color(0xFF7CC3CC), progress = Color(0xFFE0B567),
            done = Color(0xFF8CC7A3), danger = Color(0xFFF08A99), dangerBg = Color(0xFF4A1C26), dark = true,
        )
    }
    Scheme.MATCHA -> if (!dark) {
        Palette(
            bg = Color(0xFFEFEFE2), raised = Color(0xFFF8F8EE), ink = Color(0xFF232A1C), soft = Color(0xFF5A6350),
            line = Color(0xFFDADCC6), track = Color(0xFFDCDFC6), accent = Color(0xFF4F6B22), onAccent = Color(0xFFF8F8EE),
            tint = Color(0xFFE1E7CC), onTint = Color(0xFF26350E), link = Color(0xFF3B5E8C), progress = Color(0xFF85570E),
            done = Color(0xFF3B5E8C), danger = Color(0xFF8E2643), dangerBg = Color(0xFFF1D7DA), dark = false,
        )
    } else {
        Palette(
            bg = Color(0xFF171A12), raised = Color(0xFF21251A), ink = Color(0xFFE8EAD8), soft = Color(0xFFA9AE95),
            line = Color(0xFF353A2A), track = Color(0xFF353B28), accent = Color(0xFFA9C46A), onAccent = Color(0xFF171A12),
            tint = Color(0xFF262D19), onTint = Color(0xFFE3EDC6), link = Color(0xFF93B4DE), progress = Color(0xFFDDB862),
            done = Color(0xFF93B4DE), danger = Color(0xFFF1859A), dangerBg = Color(0xFF4E1A26), dark = true,
        )
    }
    Scheme.SUMI -> if (!dark) {
        Palette(
            bg = Color(0xFFF4F4F2), raised = Color(0xFFFFFFFF), ink = Color(0xFF161616), soft = Color(0xFF5C5C5C),
            line = Color(0xFFDEDEDA), track = Color(0xFFDCDCD8), accent = Color(0xFF161616), onAccent = Color(0xFFFFFFFF),
            tint = Color(0xFFE6E6E2), onTint = Color(0xFF161616), link = Color(0xFF3A4F7A), progress = Color(0xFF7A5A12),
            done = Color(0xFF3D6936), danger = Color(0xFFA12A2A), dangerBg = Color(0xFFF3D8D8), dark = false,
        )
    } else {
        Palette(
            bg = Color(0xFF0F0F0F), raised = Color(0xFF1A1A1A), ink = Color(0xFFECECEC), soft = Color(0xFFA0A0A0),
            line = Color(0xFF2C2C2C), track = Color(0xFF2E2E2E), accent = Color(0xFFECECEC), onAccent = Color(0xFF0F0F0F),
            tint = Color(0xFF262626), onTint = Color(0xFFECECEC), link = Color(0xFF9DB4E0), progress = Color(0xFFD8B35E),
            done = Color(0xFF97C485), danger = Color(0xFFEE8A8A), dangerBg = Color(0xFF4A1E1E), dark = true,
        )
    }
}

private fun Palette.toColorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = tint,
        onPrimaryContainer = onTint,
        inversePrimary = accent,
        secondary = link,
        onSecondary = bg,
        secondaryContainer = tint,
        onSecondaryContainer = onTint,
        tertiary = progress,
        onTertiary = bg,
        tertiaryContainer = track,
        onTertiaryContainer = ink,
        error = danger,
        onError = bg,
        errorContainer = dangerBg,
        onErrorContainer = danger,
        background = bg,
        onBackground = ink,
        surface = bg,
        onSurface = ink,
        surfaceVariant = track,
        onSurfaceVariant = soft,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = bg,
        outline = ink,
        outlineVariant = line,
        scrim = Color.Black,
        surfaceBright = raised,
        surfaceDim = bg,
        surfaceContainerLowest = raised,
        surfaceContainerLow = raised,
        surfaceContainer = raised,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
    )
}

/** The colours Material's scheme has no slot for. Read them as `MaterialTheme.podcastColors`. */
@Immutable
data class PodcastColors(val done: Color, val track: Color, val raised: Color)

private val LocalPodcastColors = staticCompositionLocalOf {
    Scheme.WASHI.palette(false).let { PodcastColors(it.done, it.track, it.raised) }
}

val MaterialTheme.podcastColors: PodcastColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPodcastColors.current

private val AppTypography: Typography = Typography().let { t ->
    fun TextStyle.bold() = copy(fontFamily = Gothic, fontWeight = FontWeight.Bold)
    fun TextStyle.plain() = copy(fontFamily = Gothic)
    t.copy(
        displayLarge = t.displayLarge.bold(),
        displayMedium = t.displayMedium.bold(),
        displaySmall = t.displaySmall.bold(),
        headlineLarge = t.headlineLarge.bold(),
        headlineMedium = t.headlineMedium.bold(),
        headlineSmall = t.headlineSmall.bold(),
        titleLarge = t.titleLarge.bold(),
        titleMedium = t.titleMedium.bold(),
        titleSmall = t.titleSmall.bold(),
        bodyLarge = t.bodyLarge.plain(),
        bodyMedium = t.bodyMedium.plain(),
        bodySmall = t.bodySmall.plain(),
        labelLarge = t.labelLarge.bold(),
        labelMedium = t.labelMedium.plain(),
        labelSmall = t.labelSmall.plain(),
    )
}

/** Small, square-ish corners, like cut paper. */
val PaperShape = RoundedCornerShape(4.dp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = PaperShape,
    medium = PaperShape,
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PodcastTheme(scheme: Scheme, dark: Boolean, content: @Composable () -> Unit) {
    val palette = scheme.palette(dark)
    CompositionLocalProvider(LocalPodcastColors provides PodcastColors(palette.done, palette.track, palette.raised)) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
