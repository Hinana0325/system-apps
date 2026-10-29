package cn.xinxitong.settings

import java.io.File

/** 从 /proc/meminfo 文本中解析 MemTotal（kB）。找不到或格式不对时返回 null。纯函数，可单元测试。 */
fun parseMemTotalKb(meminfoText: String): Long? {
    for (line in meminfoText.lineSequence()) {
        if (line.startsWith("MemTotal:")) {
            val number = line.removePrefix("MemTotal:").trim().removeSuffix("kB").trim()
            return number.toLongOrNull()
        }
    }
    return null
}

/** 读取文本文件首行，失败返回 null（文件不存在 / 无权限时不抛异常）。 */
fun readFirstLineOrNull(file: File): String? = runCatching {
    file.bufferedReader().use { it.readLine() }
}.getOrNull()

/** 格式化字节数为 “xx.x GB / MB / KB / B”。 */
fun formatBytes(bytes: Long): String {
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

data class AboutInfo(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val kernelVersion: String,
    val totalRam: String,
)
