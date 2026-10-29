package cn.xinxitong.phone

/** 一条通话记录。数据全部来自系统 CallLog，不编造。 */
data class CallLogEntry(
    val id: Long,
    val number: String,
    val displayName: String?,
    val type: Int,
    val dateMillis: Long,
    val durationSeconds: Long,
)

object CallTypes {
    const val INCOMING = android.provider.CallLog.Calls.INCOMING_TYPE
    const val OUTGOING = android.provider.CallLog.Calls.OUTGOING_TYPE
    const val MISSED = android.provider.CallLog.Calls.MISSED_TYPE
}

/** 拨号盘输入规范化：去掉空格、短横、括号；保留开头的 +。纯函数。 */
fun normalizeDialNumber(raw: String): String {
    val cleaned = raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    if (cleaned.startsWith("+")) {
        return "+" + cleaned.drop(1).filter { it.isDigit() }
    }
    return cleaned
}

/** 长按 0 时输入 +：空输入时为 +，否则追加 0。纯函数。 */
fun longPressZero(current: String): String = if (current.isEmpty()) "+" else current + "0"

/** 通话记录按号码/姓名过滤（忽略大小写与号码中的空格短横）。纯函数。 */
fun filterCallLog(entries: List<CallLogEntry>, query: String): List<CallLogEntry> {
    val q = query.trim()
    if (q.isEmpty()) return entries
    val qDigits = normalizeDialNumber(q)
    return entries.filter { entry ->
        (entry.displayName?.contains(q, ignoreCase = true) == true) ||
            entry.number.contains(q) ||
            (qDigits.isNotEmpty() && normalizeDialNumber(entry.number).contains(qDigits))
    }
}

fun callTypeLabel(type: Int): String = when (type) {
    CallTypes.INCOMING -> "来电"
    CallTypes.OUTGOING -> "去电"
    CallTypes.MISSED -> "未接"
    else -> "通话"
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0 秒"
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes > 0) "${minutes} 分 ${rest} 秒" else "${rest} 秒"
}
