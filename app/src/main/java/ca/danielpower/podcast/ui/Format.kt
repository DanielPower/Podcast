package ca.danielpower.podcast.ui

import java.text.DateFormat
import java.util.Date

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDate(epochMs: Long): String =
    if (epochMs <= 0) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))

/** "1st", "2nd", "3rd", "4th"… */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

fun formatMinutes(ms: Long): String {
    val minutes = (ms + 59_999) / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / 1024} KB"
}
