package cn.xinxitong.files

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 列表项：真实 java.io.File 的快照。大小/时间在列出时读取，之后文件变化需刷新。 */
data class FileEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isHidden: Boolean,
)

enum class SortMode { NAME, DATE, SIZE }

/** 把 File 转成 FileEntry；读取失败时大小/时间为 0，不抛异常。 */
fun File.toEntry(): FileEntry = FileEntry(
    file = this,
    name = name,
    isDirectory = isDirectory,
    size = if (isFile) runCatching { length() }.getOrDefault(0L) else 0L,
    lastModified = runCatching { lastModified() }.getOrDefault(0L),
    isHidden = isHidden,
)

/**
 * 排序：文件夹始终在前；同类内按模式排序。
 * NAME 按名称不区分大小写；DATE 按修改时间新的在前；SIZE 按大小大的在前。
 * 纯函数，可单元测试。
 */
fun sortEntries(entries: List<FileEntry>, mode: SortMode): List<FileEntry> {
    val dirsFirst = compareByDescending<FileEntry> { it.isDirectory }
    val byMode: Comparator<FileEntry> = when (mode) {
        SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortMode.DATE -> compareByDescending { it.lastModified }
        SortMode.SIZE -> compareByDescending { it.size }
    }
    return entries.sortedWith(dirsFirst.then(byMode))
}

/** 格式化字节数，与 Settings 的 formatBytes 保持同一口径（各模块独立，避免跨模块重复依赖）。 */
fun formatSize(bytes: Long): String {
    if (bytes < 0) return "不可用"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> "%.1f GB".format(bytes / gb)
        bytes >= mb -> "%.1f MB".format(bytes / mb)
        bytes >= kb -> "%.1f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))

/** 列出目录的直接子项（不含隐藏文件）；目录不可读时返回空列表。 */
fun listDirectory(dir: File): List<FileEntry> {
    val children = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
    return children.filter { !it.isHidden }.map { it.toEntry() }
}

/**
 * 按名称递归搜索 [root] 下的文件/文件夹（不跟随隐藏目录）。
 * [maxResults] 封顶，避免大目录搜索失控；返回按路径排序的结果。
 */
fun searchFiles(root: File, query: String, maxResults: Int = 200): List<FileEntry> {
    val results = mutableListOf<FileEntry>()
    val needle = query.lowercase(Locale.getDefault())
    fun walk(dir: File, depth: Int) {
        if (results.size >= maxResults || depth > 8) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (results.size >= maxResults) return
            if (child.isHidden) continue
            if (child.name.lowercase(Locale.getDefault()).contains(needle)) {
                results += child.toEntry()
            }
            if (child.isDirectory) walk(child, depth + 1)
        }
    }
    walk(root, 0)
    return results.sortedBy { it.file.absolutePath }
}

/** 复制文件或目录。目标已存在同名项时返回失败，不覆盖。 */
fun copyRecursively(source: File, target: File): Result<Unit> = runCatching {
    require(!target.exists()) { "目标已存在：${target.name}" }
    if (source.isDirectory) {
        if (!target.mkdirs()) throw IllegalStateException("无法创建目录：${target.absolutePath}")
        val children = source.listFiles() ?: emptyArray()
        for (child in children) {
            copyRecursively(child, File(target, child.name)).getOrThrow()
        }
    } else {
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }
}

/** 移动：先 renameTo，跨分区失败时回退为复制 + 删除。 */
fun moveRecursively(source: File, target: File): Result<Unit> = runCatching {
    require(!target.exists()) { "目标已存在：${target.name}" }
    if (source.renameTo(target)) return@runCatching
    copyRecursively(source, target).getOrThrow()
    if (!source.deleteRecursively()) throw IllegalStateException("复制后删除源文件失败：${source.absolutePath}")
}

/** 删除文件或目录（含内容）。失败返回 Result 失败，由调用方提示。 */
fun deleteRecursively(file: File): Result<Unit> = runCatching {
    if (!file.deleteRecursively()) throw IllegalStateException("删除失败：${file.absolutePath}")
}
