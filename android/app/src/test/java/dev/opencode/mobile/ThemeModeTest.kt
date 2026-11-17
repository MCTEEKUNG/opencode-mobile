package dev.opencode.mobile

import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test fun followsChangingSystemAppearance() {
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
    }

    @Test fun explicitChoicesOverrideSystemAppearance() {
        for (systemDark in listOf(false, true)) {
            assertFalse(ThemeMode.LIGHT.isDark(systemDark))
            assertTrue(ThemeMode.DARK.isDark(systemDark))
        }
    }

    @Test fun migratesExistingPreferencesAndDefaultsNewInstallsToSystem() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored(null, true))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored(null, false))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null, null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("unknown", true))
    }

    @Test fun savedSystemChoiceSurvivesLegacyOverrideAndAllChoicesRoundTrip() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.fromStored(mode.preference, null))
            assertEquals(mode, ThemeMode.fromStored(mode.preference, true))
            assertEquals(mode, ThemeMode.fromStored(mode.preference, false))
        }
    }
}
