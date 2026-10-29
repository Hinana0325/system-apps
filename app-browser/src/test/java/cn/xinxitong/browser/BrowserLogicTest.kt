package cn.xinxitong.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLogicTest {
    @Test
    fun resolveInputKeepsScheme() {
        assertEquals("https://example.com/a", resolveInput("https://example.com/a"))
        assertEquals("http://example.com", resolveInput("http://example.com"))
    }

    @Test
    fun resolveInputAddsSchemeForDomains() {
        assertEquals("https://example.com", resolveInput("example.com"))
        assertEquals("https://www.example.com/x", resolveInput("www.example.com/x"))
        assertEquals("https://localhost:8080", resolveInput("localhost:8080"))
    }

    @Test
    fun resolveInputSearchesForPlainWords() {
        val url = resolveInput("安卓 内核")
        assertTrue(url.startsWith("https://www.bing.com/search?q="))
        assertFalse(url.contains(" "))
    }

    @Test
    fun resolveInputTrimsAndHandlesBlank() {
        assertEquals("", resolveInput("   "))
        assertEquals("https://example.com", resolveInput("  example.com  "))
    }

    @Test
    fun isProbablyUrlRejectsPhrases() {
        assertFalse(isProbablyUrl("hello world"))
        assertFalse(isProbablyUrl("example"))
        assertTrue(isProbablyUrl("example.com"))
    }

    @Test
    fun dedupHistoryKeepsNewestAndCaps() {
        val entries = listOf(
            HistoryEntry("旧", "https://a.com", 100),
            HistoryEntry("新", "https://a.com", 300),
            HistoryEntry("中", "https://b.com", 200),
        )
        val result = dedupHistory(entries)
        assertEquals(listOf("https://a.com", "https://b.com"), result.map { it.url })
        assertEquals("新", result[0].title)
        val many = (1..150).map { HistoryEntry("t$it", "https://x$it.com", it.toLong()) }
        assertEquals(100, dedupHistory(many).size)
    }

    @Test
    fun bookmarkAddRemoveDedupsByUrl() {
        val start = listOf(Bookmark("旧标题", "https://a.com"), Bookmark("B", "https://b.com"))
        val added = addBookmark(start, Bookmark("新标题", "https://a.com"))
        assertEquals(2, added.size)
        assertEquals("新标题", added.first { it.url == "https://a.com" }.title)
        assertEquals(1, removeBookmark(added, "https://a.com").size)
    }
}
