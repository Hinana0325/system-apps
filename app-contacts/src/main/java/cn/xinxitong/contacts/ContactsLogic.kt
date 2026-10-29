package cn.xinxitong.contacts

import android.net.Uri

/** 联系人：号码/邮箱按联系人聚合后得到。照片 URI 可能为空。 */
data class Contact(
    val id: Long,
    val displayName: String,
    val photoUri: Uri?,
    val numbers: List<String>,
    val emails: List<String>,
)

/** 首字母分组键：汉字与字母取首字符大写，其余归入 #。纯函数。 */
fun initialKey(displayName: String): String {
    val first = displayName.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

/** 按姓名/号码过滤。纯函数。 */
fun filterContacts(contacts: List<Contact>, query: String): List<Contact> {
    val q = query.trim()
    if (q.isEmpty()) return contacts
    return contacts.filter { contact ->
        contact.displayName.contains(q, ignoreCase = true) ||
            contact.numbers.any { it.contains(q) } ||
            contact.emails.any { it.contains(q, ignoreCase = true) }
    }
}

/** 联系人按首字母分组，组内按姓名排序，# 组排最后。纯函数。 */
fun groupContacts(contacts: List<Contact>): List<Pair<String, List<Contact>>> {
    val grouped = contacts.groupBy { initialKey(it.displayName) }
    val sortedKeys = grouped.keys.sortedWith(compareBy({ it == "#" }, { it }))
    return sortedKeys.map { key ->
        key to (grouped[key] ?: emptyList()).sortedBy { it.displayName }
    }
}

/** 无照片时的兜底首字：取姓名第一个非空白字符。纯函数。 */
fun fallbackInitial(displayName: String): String =
    displayName.trim().firstOrNull()?.toString() ?: "?"
