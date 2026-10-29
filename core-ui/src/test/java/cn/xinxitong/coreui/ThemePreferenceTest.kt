package cn.xinxitong.coreui

import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.resolveDarkTheme
import cn.xinxitong.coreui.theme.themeModeFromStored
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {

    @Test
    fun storedLightMapsToLight() {
        assertEquals(ThemeMode.LIGHT, themeModeFromStored("LIGHT"))
    }

    @Test
    fun storedDarkMapsToDark() {
        assertEquals(ThemeMode.DARK, themeModeFromStored("DARK"))
    }

    @Test
    fun unknownStoredValueFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, themeModeFromStored("SYSTEM"))
        assertEquals(ThemeMode.SYSTEM, themeModeFromStored(null))
        assertEquals(ThemeMode.SYSTEM, themeModeFromStored(""))
        assertEquals(ThemeMode.SYSTEM, themeModeFromStored("bogus"))
    }

    @Test
    fun resolveDarkThemeHonoursExplicitModes() {
        assertEquals(true, resolveDarkTheme(ThemeMode.DARK, systemDark = false))
        assertEquals(false, resolveDarkTheme(ThemeMode.LIGHT, systemDark = true))
    }

    @Test
    fun resolveDarkThemeFollowsSystemInSystemMode() {
        assertEquals(true, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(false, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }
}
