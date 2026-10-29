package cn.xinxitong.clock

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockLogicTest {
    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val dateTime = LocalDateTime.of(year, month, day, hour, minute)
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Test
    fun formatClockTimeFormats() {
        assertEquals("07:05:09", formatClockTime(millis(2026, 10, 29, 7, 5) + 9_000))
    }

    @Test
    fun formatChineseDateFormats() {
        // 2026-10-29 is a Thursday.
        assertEquals("2026年10月29日 星期四", formatChineseDate(millis(2026, 10, 29, 7, 0)))
    }

    @Test
    fun formatStopwatchFormats() {
        assertEquals("00:00.00", formatStopwatch(0))
        assertEquals("01:02.34", formatStopwatch(62_340))
        assertEquals("1:02:03.45", formatStopwatch(3_723_450))
        assertEquals("00:00.00", formatStopwatch(-500))
    }

    @Test
    fun formatCountdownFormats() {
        assertEquals("05:00", formatCountdown(300_000))
        assertEquals("01:00", formatCountdown(59_500))
        assertEquals("1:00:00", formatCountdown(3_600_000))
        assertEquals("00:00", formatCountdown(0))
    }

    @Test
    fun timerInputToMillisConvertsAndClamps() {
        assertEquals(330_000L, timerInputToMillis(5, 30))
        assertEquals(0L, timerInputToMillis(-1, -5))
    }

    @Test
    fun nextAlarmOneTimeTodayFuture() {
        val now = millis(2026, 10, 29, 6, 0)
        assertEquals(millis(2026, 10, 29, 7, 0), nextAlarmTimeMillis(7, 0, emptySet(), now))
    }

    @Test
    fun nextAlarmOneTimeTodayPastGoesTomorrow() {
        val now = millis(2026, 10, 29, 8, 0)
        assertEquals(millis(2026, 10, 30, 7, 0), nextAlarmTimeMillis(7, 0, emptySet(), now))
    }

    @Test
    fun nextAlarmRepeatingSkipsNonMatchingDays() {
        // 2026-10-29 is Thursday (value 4). Repeat Monday only (1): next is Monday 2026-11-02.
        val now = millis(2026, 10, 29, 6, 0)
        assertEquals(millis(2026, 11, 2, 7, 0), nextAlarmTimeMillis(7, 0, setOf(1), now))
    }

    @Test
    fun nextAlarmRepeatingTodayMatchingButPast() {
        // Thursday repeat, 7:00 already passed at 8:00 -> next Thursday 2026-11-05.
        val now = millis(2026, 10, 29, 8, 0)
        assertEquals(millis(2026, 11, 5, 7, 0), nextAlarmTimeMillis(7, 0, setOf(4), now))
    }

    @Test
    fun repeatDescriptionDescribes() {
        assertEquals("仅一次", repeatDescription(emptySet()))
        assertEquals("每天", repeatDescription(setOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals("周一 周三", repeatDescription(setOf(3, 1)))
    }

    @Test
    fun isValidAlarmTimeValidates() {
        assertTrue(isValidAlarmTime(0, 0))
        assertTrue(isValidAlarmTime(23, 59))
        assertFalse(isValidAlarmTime(24, 0))
        assertFalse(isValidAlarmTime(7, 60))
        assertFalse(isValidAlarmTime(-1, 0))
    }

    @Test
    fun formatAlarmTimePads() {
        assertEquals("07:05", formatAlarmTime(7, 5))
        assertEquals("23:59", formatAlarmTime(23, 59))
    }
}
