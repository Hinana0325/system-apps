package cn.xinxitong.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 一条日历事件：全部来自系统日历提供方（CalendarContract），不编造。 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val calendarName: String,
    val allDay: Boolean,
)

/**
 * 某个月的月视图网格：42 个日期（6 行 × 7 列），周一起始，
 * 首行用上个月末尾的日期补齐，末行用下个月开头的日期补齐。
 * 纯函数，可单元测试。
 */
fun monthGrid(year: Int, month: Int): List<LocalDate> {
    val first = LocalDate.of(year, month, 1)
    val leadDays = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val start = first.minusDays(leadDays.toLong())
    return (0 until 42).map { start.plusDays(it.toLong()) }
}

/** 月份标题：2026年10月。纯函数。 */
fun formatMonthTitle(yearMonth: YearMonth): String =
    "%d年%d月".format(yearMonth.year, yearMonth.monthValue)

/** 选中日期的中文格式：2026年10月29日 星期四。纯函数。 */
fun formatChineseDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE)
    return date.format(formatter)
}

/** 事件时间段显示：全天事件显示“全天”，否则显示 HH:mm–HH:mm。纯函数。 */
fun formatEventTime(event: CalendarEvent): String {
    if (event.allDay) return "全天"
    val zone = ZoneId.systemDefault()
    val start = java.time.Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalTime()
    val end = java.time.Instant.ofEpochMilli(event.endMillis).atZone(zone).toLocalTime()
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return "${start.format(formatter)}–${end.format(formatter)}"
}

/**
 * 取某一天的事件：事件时间段与当天 [00:00, 次日 00:00) 有交集即算在内，
 * 因此跨天事件会在每一天都出现。全天事件的 DTEND 在 CalendarContract 中
 * 是排他的（结束日当天不包含），按区间交集处理后行为一致。
 * 纯函数，可单元测试。
 */
fun eventsForDay(events: List<CalendarEvent>, date: LocalDate): List<CalendarEvent> {
    val zone = ZoneId.systemDefault()
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return events
        .filter { it.startMillis < dayEnd && it.endMillis > dayStart }
        .sortedBy { it.startMillis }
}

/** 某个月内有事件的日期集合（用于月视图打点）。纯函数。 */
fun eventDaysInMonth(events: List<CalendarEvent>, yearMonth: YearMonth): Set<LocalDate> {
    val zone = ZoneId.systemDefault()
    val result = HashSet<LocalDate>()
    for (event in events) {
        var date = java.time.Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDate()
        val endDate = java.time.Instant.ofEpochMilli(event.endMillis).atZone(zone).toLocalDate()
        // 结束时刻恰为某日 00:00 时，该日不计入（排他结束）。
        val lastIncluded = if (event.endMillis % 86_400_000L == 0L && event.allDay) {
            endDate.minusDays(1)
        } else {
            endDate
        }
        var guard = 0
        while (!date.isAfter(lastIncluded) && guard < 366) {
            if (YearMonth.from(date) == yearMonth) result.add(date)
            date = date.plusDays(1)
            guard++
        }
    }
    return result
}

/** 判断两个日期是否为同一天。纯函数。 */
fun isSameDay(a: LocalDate, b: LocalDate): Boolean = a == b
