package cn.xinxitong.calendar

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarLogicTest {
    private fun millis(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun event(id: Long, start: Long, end: Long, allDay: Boolean = false) =
        CalendarEvent(id, "事件$id", start, end, "日历", allDay)

    @Test
    fun monthGridHas42MondayFirstCells() {
        // 2026-10-01 is a Thursday; grid starts Monday 2026-09-28.
        val grid = monthGrid(2026, 10)
        assertEquals(42, grid.size)
        assertEquals(LocalDate.of(2026, 9, 28), grid.first())
        assertEquals(LocalDate.of(2026, 11, 8), grid.last())
        assertTrue(grid.contains(LocalDate.of(2026, 10, 29)))
    }

    @Test
    fun monthGridStartsOnMondayWhenFirstIsMonday() {
        // 2026-06-01 is a Monday.
        val grid = monthGrid(2026, 6)
        assertEquals(LocalDate.of(2026, 6, 1), grid.first())
        assertEquals(42, grid.size)
    }

    @Test
    fun formatChineseDateFormats() {
        assertEquals("2026年10月29日 星期四", formatChineseDate(LocalDate.of(2026, 10, 29)))
    }

    @Test
    fun formatMonthTitleFormats() {
        assertEquals("2026年10月", formatMonthTitle(YearMonth.of(2026, 10)))
    }

    @Test
    fun eventsForDayIncludesMultiDayEvent() {
        val events = listOf(
            event(1, millis(LocalDate.of(2026, 10, 28), 18, 0), millis(LocalDate.of(2026, 10, 30), 9, 0)),
            event(2, millis(LocalDate.of(2026, 10, 29), 9, 0), millis(LocalDate.of(2026, 10, 29), 10, 0)),
            event(3, millis(LocalDate.of(2026, 10, 30), 9, 0), millis(LocalDate.of(2026, 10, 30), 10, 0)),
        )
        val day = eventsForDay(events, LocalDate.of(2026, 10, 29))
        assertEquals(listOf(1L, 2L), day.map { it.id })
    }

    @Test
    fun eventsForDayExcludesEventEndingAtMidnight() {
        val events = listOf(
            event(1, millis(LocalDate.of(2026, 10, 28), 18, 0), millis(LocalDate.of(2026, 10, 29), 0, 0)),
        )
        assertEquals(0, eventsForDay(events, LocalDate.of(2026, 10, 29)).size)
        assertEquals(1, eventsForDay(events, LocalDate.of(2026, 10, 28)).size)
    }

    @Test
    fun formatEventTimeFormats() {
        val timed = event(1, millis(LocalDate.of(2026, 10, 29), 9, 30), millis(LocalDate.of(2026, 10, 29), 10, 0))
        assertEquals("09:30–10:00", formatEventTime(timed))
        val allDay = event(2, millis(LocalDate.of(2026, 10, 29), 0, 0), millis(LocalDate.of(2026, 10, 30), 0, 0), allDay = true)
        assertEquals("全天", formatEventTime(allDay))
    }

    @Test
    fun eventDaysInMonthCollectsDays() {
        val events = listOf(
            event(1, millis(LocalDate.of(2026, 10, 28), 18, 0), millis(LocalDate.of(2026, 10, 30), 9, 0)),
        )
        val days = eventDaysInMonth(events, YearMonth.of(2026, 10))
        assertTrue(days.contains(LocalDate.of(2026, 10, 28)))
        assertTrue(days.contains(LocalDate.of(2026, 10, 29)))
        assertTrue(days.contains(LocalDate.of(2026, 10, 30)))
    }
}
