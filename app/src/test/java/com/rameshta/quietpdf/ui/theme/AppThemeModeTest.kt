package com.rameshta.quietpdf.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun storedValuesRoundTrip() {
        AppThemeMode.entries.forEach { mode ->
            assertEquals(mode, AppThemeMode.fromStoredValue(mode.storedValue, AppThemeMode.Light))
        }
    }

    @Test
    fun missingOrUnknownValueUsesCurrentSystemFallback() {
        assertEquals(AppThemeMode.Dark, AppThemeMode.fromStoredValue(null, AppThemeMode.Dark))
        assertEquals(AppThemeMode.Light, AppThemeMode.fromStoredValue("unknown", AppThemeMode.Light))
    }
}
