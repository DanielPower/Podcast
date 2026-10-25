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
