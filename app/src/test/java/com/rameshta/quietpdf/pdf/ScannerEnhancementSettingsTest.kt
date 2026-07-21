package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerEnhancementSettingsTest {
    @Test
    fun valuesAreClampedToSafeRanges() {
        val normalized = ScannerEnhancementSettings(
            brightness = 3f,
            contrast = -2f,
        ).normalized()

        assertEquals(ScannerEnhancementSettings.MaxBrightness, normalized.brightness)
        assertEquals(ScannerEnhancementSettings.MinContrast, normalized.contrast)
    }

    @Test
    fun validSettingsRemainUnchanged() {
        val settings = ScannerEnhancementSettings(
            mode = ScannerColorMode.Grayscale,
            brightness = 0.15f,
            contrast = 1.25f,
            shadowReduction = false,
        )

        assertEquals(settings, settings.normalized())
    }
}
