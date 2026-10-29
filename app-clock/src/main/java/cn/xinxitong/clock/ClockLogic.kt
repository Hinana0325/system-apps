package cn.xinxitong.clock

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 格式化当前时间 HH:mm:ss。纯函数。 */
fun formatClockTime(timeMillis: Long): String {
    val time = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return time.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}

/** 中文日期：2026年10月29日 星期四。纯函数。 */
fun formatChineseDate(timeMillis: Long): String {
    val date = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE)
    return date.format(formatter)
}

/** 秒表显示：MM:SS.cc（不足 1 小时）或 H:MM:SS.cc。纯函数。 */
fun formatStopwatch(elapsedMillis: Long): String {
    val totalCentis = (elapsedMillis.coerceAtLeast(0) / 10).toInt()
    val centis = totalCentis % 100
    val totalSeconds = totalCentis / 100
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centis)
    } else {
        "%02d:%02d.%02d".format(minutes, seconds, centis)
    }
}

/** 倒计时显示：HH:MM:SS（不足 1 小时则 MM:SS）。纯函数。 */
fun formatCountdown(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis.coerceAtLeast(0) + 999) / 1000
    val seconds = (totalSeconds % 60).toInt()
    val totalMinutes = totalSeconds / 60
    val minutes = (totalMinutes % 60).toInt()
    val hours = (totalMinutes / 60).toInt()
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/** 把分钟/秒输入换算成总毫秒，负数按 0 处理。纯函数。 */
fun timerInputToMillis(minutes: Int, seconds: Int): Long {
    val totalSeconds = minutes.coerceAtLeast(0).toLong() * 60 + seconds.coerceAtLeast(0)
    return totalSeconds * 1000
}

/**
 * 计算闹钟下一次触发时间（毫秒）。
 *
 * @param repeatDays 重复的星期，1=周一 … 7=周日（[DayOfWeek] 的 value）；空集表示仅一次。
 * @param nowMillis “现在”，调用方传入当前时间；单元测试用它固定时间。
 *
 * 规则：今天该时刻在未来且（不重复，或今天在重复日内）则取今天；否则取之后第一个
 * 匹配的日期；不重复且今天已过则取明天。纯函数。
 */
fun nextAlarmTimeMillis(
    hour: Int,
    minute: Int,
    repeatDays: Set<Int>,
    nowMillis: Long,
): Long {
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val today = now.toLocalDate()
    val alarmTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    val todayCandidate = ZonedDateTime.of(today, alarmTime, zone)
    val todayMatches = repeatDays.isEmpty() || repeatDays.contains(today.dayOfWeek.value)
    if (todayMatches && !todayCandidate.isBefore(now)) {
        return todayCandidate.toInstant().toEpochMilli()
    }
    var date = today.plusDays(1)
    while (true) {
        if (repeatDays.isEmpty() || repeatDays.contains(date.dayOfWeek.value)) {
            return ZonedDateTime.of(date, alarmTime, zone).toInstant().toEpochMilli()
        }
        date = date.plusDays(1)
    }
}

/** 星期几中文简称：1=周一 … 7=周日。纯函数。 */
fun weekdayShortName(dayOfWeekValue: Int): String = when (dayOfWeekValue) {
    DayOfWeek.MONDAY.value -> "周一"
    DayOfWeek.TUESDAY.value -> "周二"
    DayOfWeek.WEDNESDAY.value -> "周三"
    DayOfWeek.THURSDAY.value -> "周四"
    DayOfWeek.FRIDAY.value -> "周五"
    DayOfWeek.SATURDAY.value -> "周六"
    DayOfWeek.SUNDAY.value -> "周日"
    else -> ""
}

/** 闹钟重复说明：空集为“仅一次”，全选为“每天”，否则列出星期。纯函数。 */
fun repeatDescription(repeatDays: Set<Int>): String {
    if (repeatDays.isEmpty()) return "仅一次"
    if (repeatDays.size == 7) return "每天"
    return repeatDays.sorted().joinToString(" ") { weekdayShortName(it) }
}

/** “HH:mm” 形式的闹钟时间。纯函数。 */
fun formatAlarmTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** 检查新建/编辑闹钟的小时/分钟输入是否合法。纯函数。 */
fun isValidAlarmTime(hour: Int, minute: Int): Boolean = hour in 0..23 && minute in 0..59

/** 当前日期（用于日历式展示的辅助）：返回今天的 [LocalDate]。 */
fun todayDate(): LocalDate = LocalDate.now()

/** 把毫秒时间戳换算成本地日期时间（供界面显示下次触发）。纯函数。 */
fun formatDateTime(timeMillis: Long): String {
    val dateTime = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return dateTime.format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINESE))
}
