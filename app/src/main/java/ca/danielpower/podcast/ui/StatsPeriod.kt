package ca.danielpower.podcast.ui

import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class StatsRange(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All time") }

/**
 * The days the Statistics tab adds up: the week, month or year [offset] periods back from the one
 * holding today (0 is the current one), or everything.
 */
data class StatsPeriod(val range: StatsRange, val offset: Int, val from: LocalDate, val to: LocalDate) {
    val canGoForward: Boolean get() = range != StatsRange.ALL && offset < 0

    /** Whether there's anything recorded before this period, given the first day anything was. */
    fun canGoBack(firstDay: LocalDate?): Boolean = range != StatsRange.ALL && firstDay != null && firstDay < from

    /**
     * How many days of this period have had statistics recorded: from the first day anything was,
     * or the period's start, to today or the period's end. For "about this much a day".
     */
    fun daysCounted(today: LocalDate, firstDay: LocalDate?): Long {
        if (firstDay == null) return 0
        val start = maxOf(from, firstDay)
        val end = minOf(to, today)
        return if (end < start) 0 else end.toEpochDay() - start.toEpochDay() + 1
    }

    fun label(firstDay: LocalDate?, locale: Locale = Locale.getDefault()): String = when (range) {
        StatsRange.WEEK -> {
            val start = DateTimeFormatter.ofPattern("MMM d", locale).format(from)
            val end = DateTimeFormatter.ofPattern(if (from.month == to.month) "d" else "MMM d", locale).format(to)
            "$start – $end"
        }
        StatsRange.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(from)
        StatsRange.YEAR -> from.year.toString()
        StatsRange.ALL -> firstDay?.let { "Since " + DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(it) } ?: "All time"
    }

    companion object {
        fun of(range: StatsRange, offset: Int, today: LocalDate, locale: Locale = Locale.getDefault()): StatsPeriod =
            when (range) {
                StatsRange.WEEK -> {
                    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
                    val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).plusWeeks(offset.toLong())
                    StatsPeriod(range, offset, start, start.plusDays(6))
                }
                StatsRange.MONTH -> {
                    val month = YearMonth.from(today).plusMonths(offset.toLong())
                    StatsPeriod(range, offset, month.atDay(1), month.atEndOfMonth())
                }
                StatsRange.YEAR -> {
                    val year = Year.from(today).plusYears(offset.toLong())
                    StatsPeriod(range, offset, year.atDay(1), year.atMonth(12).atEndOfMonth())
                }
                StatsRange.ALL -> StatsPeriod(range, 0, LocalDate.ofEpochDay(0), today)
            }
    }
}
