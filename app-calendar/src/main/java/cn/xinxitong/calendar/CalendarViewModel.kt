package cn.xinxitong.calendar

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _selectedDate = MutableStateFlow(java.time.LocalDate.now())
    val selectedDate: StateFlow<java.time.LocalDate> = _selectedDate.asStateFlow()

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
        loadEvents()
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
        loadEvents()
    }

    fun goToToday() {
        val today = java.time.LocalDate.now()
        _month.value = YearMonth.from(today)
        _selectedDate.value = today
        loadEvents()
    }

    fun selectDate(date: java.time.LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _month.value) {
            _month.value = YearMonth.from(date)
            loadEvents()
        }
    }

    /** 从系统日历提供方读取当前月前后各 15 天的事件（覆盖网格补齐的日期）。 */
    fun loadEvents() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                withContext(Dispatchers.IO) { queryEvents(_month.value) }
            }.onSuccess { _events.value = it }
                .onFailure { _error.value = "读取日历失败：${it.message}" }
            _loading.value = false
        }
    }

    private fun queryEvents(yearMonth: YearMonth): List<CalendarEvent> {
        val resolver = getApplication<Application>().contentResolver
        val zone = ZoneId.systemDefault()
        val rangeStart = yearMonth.atDay(1).minusDays(15).atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = yearMonth.atEndOfMonth().plusDays(15).atStartOfDay(zone).toInstant().toEpochMilli()

        // 日历 id -> 显示名。
        val calendarNames = HashMap<Long, String>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (idIndex >= 0) {
                    calendarNames[cursor.getLong(idIndex)] =
                        if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                }
            }
        }

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY,
        )
        val selection = "${CalendarContract.Events.DTSTART} < ? AND ${CalendarContract.Events.DTEND} > ?"
        val result = ArrayList<CalendarEvent>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf(rangeEnd.toString(), rangeStart.toString()),
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
            val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val startIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND)
            val calendarIndex = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
            val allDayIndex = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
            while (cursor.moveToNext()) {
                if (idIndex < 0 || startIndex < 0 || endIndex < 0) continue
                val calendarId = if (calendarIndex >= 0) cursor.getLong(calendarIndex) else -1L
                result.add(
                    CalendarEvent(
                        id = cursor.getLong(idIndex),
                        title = if (titleIndex >= 0) cursor.getString(titleIndex).orEmpty() else "",
                        startMillis = cursor.getLong(startIndex),
                        endMillis = cursor.getLong(endIndex),
                        calendarName = calendarNames[calendarId].orEmpty(),
                        allDay = allDayIndex >= 0 && cursor.getInt(allDayIndex) != 0,
                    ),
                )
            }
        }
        return result
    }

    fun eventUri(event: CalendarEvent): Uri =
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)

    fun consumeError() {
        _error.value = null
    }
}
