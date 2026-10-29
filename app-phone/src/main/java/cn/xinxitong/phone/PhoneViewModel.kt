package cn.xinxitong.phone

import android.app.Application
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhoneViewModel(application: Application) : AndroidViewModel(application) {
    private val _entries = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val entries: StateFlow<List<CallLogEntry>> = _entries.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 从系统通话记录读取最近 200 条；姓名通过 PhoneLookup 逐条查，查不到留空。 */
    fun loadCallLog() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val projection = arrayOf(
                        CallLog.Calls._ID,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION,
                    )
                    val result = mutableListOf<CallLogEntry>()
                    resolver.query(
                        CallLog.Calls.CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${CallLog.Calls.DATE} DESC LIMIT 200",
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                        val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                        val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                        val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                        val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                        while (cursor.moveToNext()) {
                            val number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
                            result.add(
                                CallLogEntry(
                                    id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                                    number = number,
                                    displayName = lookupName(resolver, number),
                                    type = if (typeIndex >= 0) cursor.getInt(typeIndex) else 0,
                                    dateMillis = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L,
                                    durationSeconds = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L,
                                ),
                            )
                        }
                    }
                    result
                }
            }.onSuccess { _entries.value = it }
                .onFailure { _error.value = "读取通话记录失败：${it.message}" }
            _loading.value = false
        }
    }

    private fun lookupName(resolver: android.content.ContentResolver, number: String): String? {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    fun consumeError() {
        _error.value = null
    }
}

fun formatCallDate(millis: Long): String {
    if (millis <= 0) return "未知时间"
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
