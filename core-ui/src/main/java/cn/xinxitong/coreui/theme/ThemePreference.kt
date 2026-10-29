package cn.xinxitong.coreui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 主题模式：跟随系统 / 浅色 / 深色。存在 DataStore，供三个应用共用。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "system_apps_prefs")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

/** 把持久化的字符串还原成 [ThemeMode]；未知或空值一律回到跟随系统。纯函数，可单元测试。 */
fun themeModeFromStored(value: String?): ThemeMode = when (value) {
    "LIGHT" -> ThemeMode.LIGHT
    "DARK" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}

/** 根据模式和系统当前明暗决定最终是否用深色主题。纯函数，可单元测试。 */
fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> systemDark
}

class ThemePreference(private val context: Context) {
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        themeModeFromStored(prefs[THEME_MODE_KEY])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }
}
