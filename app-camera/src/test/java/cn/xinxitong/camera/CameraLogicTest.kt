package cn.xinxitong.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLogicTest {
    @Test
    fun captureFileNameMatchesTimestampPattern() {
        val name = captureFileName(1793239445000L)
        assertTrue(name.matches(Regex("IMG_\\d{8}_\\d{6}\\.jpg")))
    }

    @Test
    fun nextFlashModeCyclesOffAutoOn() {
        assertEquals(FlashMode.AUTO, nextFlashMode(FlashMode.OFF))
        assertEquals(FlashMode.ON, nextFlashMode(FlashMode.AUTO))
        assertEquals(FlashMode.OFF, nextFlashMode(FlashMode.ON))
    }

    @Test
    fun flashModeLabelCoversAllModes() {
        assertEquals("闪光灯：关", flashModeLabel(FlashMode.OFF))
        assertEquals("闪光灯：自动", flashModeLabel(FlashMode.AUTO))
        assertEquals("闪光灯：开", flashModeLabel(FlashMode.ON))
    }
}
