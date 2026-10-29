package cn.xinxitong.messages

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLogicTest {
    private fun message(id: Long, address: String, body: String, date: Long, type: Int, read: Boolean) =
        SmsMessage(id, 1, address, body, date, type, read)

    @Test
    fun groupConversationsAggregatesByNormalizedAddress() {
        val messages = listOf(
            message(1, "138 0013 8000", "你好", 100, 1, false),
            message(2, "13800138000", "在吗", 200, 1, true),
            message(3, "13900139000", "另一条", 150, 2, true),
        )
        val grouped = groupConversations(messages)
        assertEquals(2, grouped.size)
        assertEquals("在吗", grouped[0].snippet)
        assertEquals(2, grouped[0].messageCount)
        assertEquals(1, grouped[0].unreadCount)
    }

    @Test
    fun messagesForAddressReturnsChronologicalOrder() {
        val messages = listOf(
            message(1, "13800138000", "后", 200, 1, true),
            message(2, "13800138000", "先", 100, 1, true),
        )
        assertEquals(listOf("先", "后"), messagesForAddress(messages, "138 0013 8000").map { it.body })
    }

    @Test
    fun filterConversationsMatchesAddressAndSnippet() {
        val conversations = listOf(
            Conversation("13800138000", "13800138000", "会议改期", 100, 1, 0),
            Conversation("13900139000", "13900139000", "你好", 200, 1, 0),
        )
        assertEquals(1, filterConversations(conversations, "会议").size)
        assertEquals(1, filterConversations(conversations, "139").size)
        assertEquals(2, filterConversations(conversations, "").size)
    }
}
