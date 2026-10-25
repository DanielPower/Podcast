package ca.danielpower.podcast.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class StatsPeriodTest {
    private val today = LocalDate.of(2026, 10, 25) // a Sunday

    @Test
    fun weekStartsOnTheLocalesFirstDay() {
        val us = StatsPeriod.of(StatsRange.WEEK, 0, today, Locale.US)
        assertEquals(LocalDate.of(2026, 10, 25), us.from)
        assertEquals(LocalDate.of(2026, 10, 31), us.to)
        val uk = StatsPeriod.of(StatsRange.WEEK, 0, today, Locale.UK)
        assertEquals(LocalDate.of(2026, 10, 19), uk.from)
        assertEquals("Oct 19 – 25", uk.label(null, Locale.UK))
    }

    @Test
    fun previousMonthCoversTheWholeMonth() {
        val period = StatsPeriod.of(StatsRange.MONTH, -1, today, Locale.US)
        assertEquals(LocalDate.of(2026, 9, 1), period.from)
        assertEquals(LocalDate.of(2026, 9, 30), period.to)
        assertEquals("September 2026", period.label(null, Locale.US))
        assertTrue(period.canGoForward)
    }

    @Test
    fun canOnlyGoBackWhenThereIsSomethingEarlier() {
        val month = StatsPeriod.of(StatsRange.MONTH, 0, today, Locale.US)
        assertFalse(month.canGoBack(null))
        assertFalse(month.canGoBack(LocalDate.of(2026, 10, 25)))
        assertTrue(month.canGoBack(LocalDate.of(2026, 9, 30)))
        assertFalse(month.canGoForward)
    }

    @Test
    fun daysCountedStartAtTheFirstRecordedDayAndStopToday() {
        val month = StatsPeriod.of(StatsRange.MONTH, 0, today, Locale.US)
        assertEquals(25L, month.daysCounted(today, LocalDate.of(2026, 1, 1)))
        assertEquals(6L, month.daysCounted(today, LocalDate.of(2026, 10, 20)))
        assertEquals(0L, month.daysCounted(today, null))
    }

    @Test
    fun statTimes() {
        assertEquals("0 min", formatStatTime(0))
        assertEquals("<1 min", formatStatTime(30_000))
        assertEquals("52 min", formatStatTime(52 * 60_000L + 59_000))
        assertEquals("41 h 12 m", formatStatTime((41 * 60 + 12) * 60_000L))
    }
}
