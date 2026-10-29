package cn.xinxitong.browser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.net.URLEncoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 一条浏览历史：标题、地址、访问时间（毫秒）。全部来自真实浏览，不编造。 */
@Serializable
data class HistoryEntry(
    val title: String,
    val url: String,
    val timeMillis: Long,
)

/** 一条书签：标题、地址。 */
@Serializable
data class Bookmark(
    val title: String,
    val url: String,
)

private val browserJson = Json { ignoreUnknownKeys = true }

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(name = "browser_prefs")
private val HISTORY_KEY = stringPreferencesKey("history_json")
private val BOOKMARKS_KEY = stringPreferencesKey("bookmarks_json")

/**
 * 把地址栏输入解析成要加载的 URL。
 *
 * 看起来像地址（带协议、localhost、或无空格且含点）时补上 https://；
 * 其他输入一律当作搜索词，交给 Bing 搜索（本应用没有自研搜索引擎）。
 * 纯函数，可单元测试。
 */
fun resolveInput(input: String): String {
    val text = input.trim()
    if (text.isEmpty()) return ""
    if (text.startsWith("http://") || text.startsWith("https://")) return text
    if (isProbablyUrl(text)) return "https://$text"
    val encoded = URLEncoder.encode(text, "UTF-8")
    return "https://www.bing.com/search?q=$encoded"
}

/** 启发式判断：无空格，且含点或以 localhost 开头。纯函数。 */
fun isProbablyUrl(text: String): Boolean {
    if (text.isEmpty() || text.contains(' ')) return false
    if (text.startsWith("localhost", ignoreCase = true)) return true
    if (text.contains("://")) return false
    return text.contains('.') && !text.startsWith('.') && !text.endsWith('.')
}

/**
 * 历史去重：同一 URL 只保留最新一条，最新在前，最多保留 [MAX_HISTORY] 条。
 * 纯函数，可单元测试。
 */
fun dedupHistory(entries: List<HistoryEntry>, maxHistory: Int = MAX_HISTORY): List<HistoryEntry> {
    val seen = LinkedHashSet<String>()
    val result = ArrayList<HistoryEntry>()
    for (entry in entries.sortedByDescending { it.timeMillis }) {
        if (seen.add(entry.url)) result.add(entry)
        if (result.size >= maxHistory) break
    }
    return result
}

/** 书签去重：同一 URL 只保留一条（新标题覆盖旧标题），其余顺序不变。纯函数。 */
fun addBookmark(bookmarks: List<Bookmark>, bookmark: Bookmark): List<Bookmark> {
    val rest = bookmarks.filterNot { it.url == bookmark.url }
    return rest + bookmark
}

/** 按 URL 删除书签。纯函数。 */
fun removeBookmark(bookmarks: List<Bookmark>, url: String): List<Bookmark> =
    bookmarks.filterNot { it.url == url }

const val MAX_HISTORY: Int = 100

class BrowserStore(private val context: Context) {
    val history: Flow<List<HistoryEntry>> = context.browserDataStore.data.map { prefs ->
        val raw = prefs[HISTORY_KEY].orEmpty()
        if (raw.isBlank()) emptyList() else runCatching {
            browserJson.decodeFromString<List<HistoryEntry>>(raw)
        }.getOrDefault(emptyList())
    }

    val bookmarks: Flow<List<Bookmark>> = context.browserDataStore.data.map { prefs ->
        val raw = prefs[BOOKMARKS_KEY].orEmpty()
        if (raw.isBlank()) emptyList() else runCatching {
            browserJson.decodeFromString<List<Bookmark>>(raw)
        }.getOrDefault(emptyList())
    }

    suspend fun recordHistory(entry: HistoryEntry) {
        if (entry.url.isBlank()) return
        if (!entry.url.startsWith("http://") && !entry.url.startsWith("https://")) return
        val current = history.first()
        val updated = dedupHistory(listOf(entry) + current)
        context.browserDataStore.edit { prefs ->
            prefs[HISTORY_KEY] = browserJson.encodeToString(updated)
        }
    }

    suspend fun clearHistory() {
        context.browserDataStore.edit { prefs -> prefs.remove(HISTORY_KEY) }
    }

    suspend fun addBookmark(bookmark: Bookmark) {
        if (bookmark.url.isBlank()) return
        val current = bookmarks.first()
        val updated = addBookmark(current, bookmark)
        context.browserDataStore.edit { prefs ->
            prefs[BOOKMARKS_KEY] = browserJson.encodeToString(updated)
        }
    }

    suspend fun removeBookmark(url: String) {
        val updated = removeBookmark(bookmarks.first(), url)
        context.browserDataStore.edit { prefs ->
            prefs[BOOKMARKS_KEY] = browserJson.encodeToString(updated)
        }
    }
}
