package cn.xinxitong.gallery

import android.net.Uri

/** 一条媒体记录：图片或视频。数据全部来自 MediaStore，不编造。 */
data class MediaItem(
    val id: Long,
    /** MediaStore URI 字符串；UI 层用 [uri] 扩展属性还原为 Uri。 */
    val uriString: String,
    val bucket: String,
    val displayName: String,
    val dateMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val isVideo: Boolean,
    val relativePath: String,
)

/** 按拍摄/添加时间倒序（最新在前）。纯函数。 */
fun sortMediaNewestFirst(items: List<MediaItem>): List<MediaItem> =
    items.sortedByDescending { it.dateMillis }

/** 按相册（BUCKET_DISPLAY_NAME）分组；“全部”不在此列，由界面单独处理。纯函数。 */
fun groupByAlbum(items: List<MediaItem>): Map<String, List<MediaItem>> =
    items.groupBy { it.bucket.ifBlank { "未命名相册" } }

/** 按相册名与文件名过滤。纯函数。 */
fun filterMedia(items: List<MediaItem>, album: String?, query: String): List<MediaItem> {
    val q = query.trim()
    val byAlbum = if (album.isNullOrBlank()) items else items.filter { it.bucket == album }
    if (q.isEmpty()) return byAlbum
    return byAlbum.filter {
        it.displayName.contains(q, ignoreCase = true) || it.bucket.contains(q, ignoreCase = true)
    }
}

fun formatMediaSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format("%.1f %s", value, units[unit])
}

fun formatMediaDate(millis: Long): String {
    if (millis <= 0) return "未知时间"
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

/** 把 [MediaItem.uriString] 还原为 Uri（仅 UI 层调用，单元测试不调用）。 */
val MediaItem.uri: Uri
    get() = Uri.parse(uriString)
