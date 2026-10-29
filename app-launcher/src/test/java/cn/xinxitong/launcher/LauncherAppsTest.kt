package cn.xinxitong.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherAppsTest {

    @Test
    fun sortEntriesIsCaseInsensitive() {
        val entries = listOf(
            AppEntry("com.b", "B", "banana", null),
            AppEntry("com.a", "A", "Apple", null),
            AppEntry("com.c", "C", "cherry", null),
        )
        assertEquals(listOf("Apple", "banana", "cherry"), sortEntries(entries).map { it.label })
    }

    @Test
    fun sortEntriesKeepsAllEntries() {
        val entries = listOf(
            AppEntry("com.a", "A", "设置", null),
            AppEntry("com.b", "B", "文件管理", null),
        )
        assertEquals(2, sortEntries(entries).size)
    }
}
