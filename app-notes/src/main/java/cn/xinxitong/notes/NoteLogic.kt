package cn.xinxitong.notes

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 一条本地备忘录：标题取自正文首行，置顶、创建/更新时间均为真实值。 */
@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
)

private val noteJson = Json { ignoreUnknownKeys = true }

/**
 * 由正文派生标题：取第一个非空行，去掉首尾空白，最多保留 30 个字符；
 * 正文全空时返回“无标题”。纯函数，可单元测试。
 */
fun noteTitleFromBody(body: String, maxLength: Int = 30): String {
    val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (firstLine.isEmpty()) return "无标题"
    return if (firstLine.length <= maxLength) firstLine else firstLine.take(maxLength)
}

/** 字符数：去掉空白字符后的实际字符数（中文按字计）。纯函数。 */
fun countCharacters(body: String): Int = body.count { !it.isWhitespace() }

/** 行数：按换行切分，空正文计 0 行。纯函数。 */
fun countLines(body: String): Int = if (body.isEmpty()) 0 else body.count { it == '\n' } + 1

/** 排序：置顶在前，再按更新时间倒序，最后按创建时间倒序。纯函数。 */
fun sortNotes(notes: List<Note>): List<Note> =
    notes.sortedWith(
        compareByDescending<Note> { it.pinned }
            .thenByDescending { it.updatedAt }
            .thenByDescending { it.createdAt },
    )

/** 按标题/正文搜索（忽略大小写）；空查询返回全部（已排序）。纯函数。 */
fun searchNotes(notes: List<Note>, query: String): List<Note> {
    val q = query.trim()
    val sorted = sortNotes(notes)
    if (q.isEmpty()) return sorted
    return sorted.filter {
        it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true)
    }
}

/** 新建一条笔记的工厂：id 用 UUID，标题由正文派生。纯函数（时间由调用方传入）。 */
fun createNote(body: String, nowMillis: Long): Note {
    val trimmed = body
    return Note(
        id = UUID.randomUUID().toString(),
        title = noteTitleFromBody(trimmed),
        body = trimmed,
        createdAt = nowMillis,
        updatedAt = nowMillis,
        pinned = false,
    )
}

/** 编辑正文后生成更新版：标题重新派生，更新时间刷新，置顶与创建时间不变。纯函数。 */
fun updateNoteBody(note: Note, newBody: String, nowMillis: Long): Note =
    note.copy(
        title = noteTitleFromBody(newBody),
        body = newBody,
        updatedAt = nowMillis,
    )

/**
 * 本地文件存储：全部笔记以 JSON 数组形式存在应用专属 files 目录下的
 * notes.json 中，不联网、不离开本机。读写均在调用方的 IO 线程完成。
 */
class NoteStore(private val file: File) {
    fun loadAll(): List<Note> {
        if (!file.exists()) return emptyList()
        val raw = runCatching { file.readText() }.getOrDefault("")
        if (raw.isBlank()) return emptyList()
        return runCatching { noteJson.decodeFromString<List<Note>>(raw) }.getOrDefault(emptyList())
    }

    fun saveAll(notes: List<Note>) {
        file.parentFile?.mkdirs()
        file.writeText(noteJson.encodeToString(notes))
    }

    fun upsert(note: Note) {
        val current = loadAll().filterNot { it.id == note.id } + note
        saveAll(current)
    }

    fun delete(id: String) {
        saveAll(loadAll().filterNot { it.id == id })
    }

    fun togglePin(id: String, nowMillis: Long): Note? {
        val current = loadAll()
        val target = current.firstOrNull { it.id == id } ?: return null
        val updated = target.copy(pinned = !target.pinned, updatedAt = nowMillis)
        upsert(updated)
        return updated
    }
}
