package cn.xinxitong.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneLogicTest {
    @Test
    fun normalizeDialNumberStripsSpacesAndDashesKeepsPlus() {
        assertEquals("+8613800138000", normalizeDialNumber("+86 138-0013-8000"))
        assertEquals("13800138000", normalizeDialNumber("138 0013 8000"))
        assertEquals("*#06#", normalizeDialNumber("*#06#"))
    }

    @Test
    fun longPressZeroInsertsPlusWhenEmptyOtherwiseZero() {
        assertEquals("+", longPressZero(""))
        assertEquals("10", longPressZero("1"))
    }

    @Test
    fun filterCallLogMatchesNameAndNormalizedNumber() {
        val entries = listOf(
            CallLogEntry(1, "13800138000", "张三", CallTypes.OUTGOING, 100, 30),
            CallLogEntry(2, "13900139000", null, CallTypes.MISSED, 200, 0),
        )
        assertEquals(listOf(1L), filterCallLog(entries, "张").map { it.id })
        assertEquals(listOf(1L), filterCallLog(entries, "138 0013").map { it.id })
        assertEquals(entries, filterCallLog(entries, ""))
    }

    @Test
    fun formatDurationFormatsMinutesAndSeconds() {
        assertEquals("0 秒", formatDuration(0))
        assertEquals("45 秒", formatDuration(45))
        assertEquals("2 分 5 秒", formatDuration(125))
    }
}
