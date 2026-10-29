package cn.xinxitong.clock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 一条闹钟：触发时刻、标签、重复星期（1=周一 … 7=周日，空集=仅一次）、是否启用。 */
@Serializable
data class AlarmItem(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    val repeatDays: List<Int> = emptyList(),
    val enabled: Boolean = true,
)

private val alarmJson = Json { ignoreUnknownKeys = true }

private val Context.alarmDataStore: DataStore<Preferences> by preferencesDataStore(name = "clock_prefs")
private val ALARMS_KEY = stringPreferencesKey("alarms_json")

class AlarmStore(private val context: Context) {
    val alarms: Flow<List<AlarmItem>> = context.alarmDataStore.data.map { prefs ->
        val raw = prefs[ALARMS_KEY].orEmpty()
        if (raw.isBlank()) emptyList() else runCatching {
            alarmJson.decodeFromString<List<AlarmItem>>(raw)
        }.getOrDefault(emptyList())
    }

    suspend fun getAlarm(id: Long): AlarmItem? = alarms.first().firstOrNull { it.id == id }

    suspend fun upsert(alarm: AlarmItem) {
        val current = alarms.first().filterNot { it.id == alarm.id } + alarm
        saveAll(current.sortedBy { it.hour * 60 + it.minute })
    }

    suspend fun delete(id: Long) {
        saveAll(alarms.first().filterNot { it.id == id })
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val current = alarms.first().map { if (it.id == id) it.copy(enabled = enabled) else it }
        saveAll(current)
    }

    private suspend fun saveAll(items: List<AlarmItem>) {
        context.alarmDataStore.edit { prefs ->
            prefs[ALARMS_KEY] = alarmJson.encodeToString(items)
        }
    }
}
