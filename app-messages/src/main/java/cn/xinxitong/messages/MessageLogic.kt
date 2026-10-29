package cn.xinxitong.messages

/** 一条短信。数据全部来自系统短信数据库，不编造。 */
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
    val type: Int, // 1 = 收到的，2 = 发出的（Telephony.Sms.MESSAGE_TYPE_*）
    val read: Boolean,
)

/** 一个会话：同一地址的短信聚合，取最新一条作摘要。 */
data class Conversation(
    val address: String,
    val displayAddress: String,
    val snippet: String,
    val dateMillis: Long,
    val messageCount: Int,
    val unreadCount: Int,
)

/** 号码规范化：去空格短横括号，保留开头 +，用于按地址聚合。纯函数。 */
fun normalizeAddress(raw: String): String {
    val cleaned = raw.filter { it.isDigit() || it == '+' }
    return if (cleaned.startsWith("+")) "+" + cleaned.drop(1).filter { it.isDigit() } else cleaned
}

/** 按地址聚合短信为会话列表，按最新时间倒序。纯函数。 */
fun groupConversations(messages: List<SmsMessage>): List<Conversation> {
    return messages
        .groupBy { normalizeAddress(it.address) }
        .map { (normalized, group) ->
            val latest = group.maxByOrNull { it.dateMillis } ?: return@map null
            Conversation(
                address = normalized,
                displayAddress = latest.address,
                snippet = latest.body,
                dateMillis = latest.dateMillis,
                messageCount = group.size,
                unreadCount = group.count { it.type == 1 && !it.read },
            )
        }
        .filterNotNull()
        .sortedByDescending { it.dateMillis }
}

/** 会话按地址/摘要过滤。纯函数。 */
fun filterConversations(conversations: List<Conversation>, query: String): List<Conversation> {
    val q = query.trim()
    if (q.isEmpty()) return conversations
    return conversations.filter {
        it.displayAddress.contains(q, ignoreCase = true) ||
            it.snippet.contains(q, ignoreCase = true) ||
            normalizeAddress(it.displayAddress).contains(normalizeAddress(q))
    }
}

/** 取某地址的全部短信，按时间升序。纯函数。 */
fun messagesForAddress(messages: List<SmsMessage>, address: String): List<SmsMessage> {
    val normalized = normalizeAddress(address)
    return messages
        .filter { normalizeAddress(it.address) == normalized }
        .sortedBy { it.dateMillis }
}

fun formatMessageDate(millis: Long): String {
    if (millis <= 0) return "未知时间"
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
