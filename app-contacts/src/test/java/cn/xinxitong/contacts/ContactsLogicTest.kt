package cn.xinxitong.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactsLogicTest {
    private fun contact(id: Long, name: String, vararg numbers: String) =
        Contact(id, name, null, numbers.toList(), emptyList())

    @Test
    fun initialKeyUsesFirstLetterUppercasedAndHashForOthers() {
        assertEquals("张", initialKey("张三"))
        assertEquals("A", initialKey("alice"))
        assertEquals("#", initialKey("123"))
        assertEquals("#", initialKey(""))
    }

    @Test
    fun groupContactsSortsGroupsWithHashLast() {
        val contacts = listOf(
            contact(1, "张三"),
            contact(2, "alice"),
            contact(3, "Bob"),
            contact(4, "123"),
        )
        val grouped = groupContacts(contacts)
        assertEquals(listOf("A", "B", "张", "#"), grouped.map { it.first })
        assertEquals(listOf("alice"), grouped[0].second.map { it.displayName })
    }

    @Test
    fun filterContactsMatchesNameNumberAndEmail() {
        val contacts = listOf(
            contact(1, "张三", "13800138000"),
            Contact(2, "李四", null, listOf("13900139000"), listOf("lisi@example.com")),
        )
        assertEquals(listOf(1L), filterContacts(contacts, "张").map { it.id })
        assertEquals(listOf(1L), filterContacts(contacts, "1380013").map { it.id })
        assertEquals(listOf(2L), filterContacts(contacts, "example.com").map { it.id })
        assertEquals(2, filterContacts(contacts, "").size)
    }

    @Test
    fun fallbackInitialReturnsFirstNonBlankCharacter() {
        assertEquals("张", fallbackInitial("张三"))
        assertEquals("?", fallbackInitial("   "))
    }
}
