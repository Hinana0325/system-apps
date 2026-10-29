package cn.xinxitong.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemInfoTest {

    @Test
    fun parseMemTotalKbReadsMemTotalLine() {
        val text = "MemTotal:       16234567 kB\nMemFree:         123456 kB\n"
        assertEquals(16234567L, parseMemTotalKb(text))
    }

    @Test
    fun parseMemTotalKbReturnsNullWhenMissing() {
        assertNull(parseMemTotalKb("MemFree: 123 kB\n"))
        assertNull(parseMemTotalKb(""))
    }

    @Test
    fun formatBytesFormatsUnits() {
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
        assertEquals("512 B", formatBytes(512))
        assertEquals("不可用", formatBytes(-1))
    }
}
