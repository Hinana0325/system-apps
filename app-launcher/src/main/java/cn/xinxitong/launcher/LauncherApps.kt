package cn.xinxitong.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一个可启动应用：包名 + 组件名 + 显示名 + 图标（加载失败时为 null，走首字兜底）。 */
data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    val iconBitmap: ImageBitmap?,
)

/** 按显示名排序（不区分大小写，中文按拼音首字母的系统排序结果为准，不自造排序）。纯函数，可单元测试。 */
fun sortEntries(entries: List<AppEntry>): List<AppEntry> =
    entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

/** 查询 PackageManager 中所有 MAIN/LAUNCHER 活动。图标加载失败时该项图标为 null，不丢弃该应用。 */
suspend fun loadApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm: PackageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    val entries = resolveInfos.mapNotNull { info ->
        val activityInfo = info.activityInfo ?: return@mapNotNull null
        val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(activityInfo.packageName)
        val iconBitmap = runCatching {
            val drawable: Drawable = info.loadIcon(pm)
            drawable.toBitmap(144, 144).asImageBitmap()
        }.getOrNull()
        AppEntry(
            packageName = activityInfo.packageName,
            className = activityInfo.name,
            label = label,
            iconBitmap = iconBitmap,
        )
    }
    sortEntries(entries.distinctBy { it.packageName + "/" + it.className })
}
