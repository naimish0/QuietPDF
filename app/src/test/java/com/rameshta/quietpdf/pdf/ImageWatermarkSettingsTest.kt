package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageWatermarkSettingsTest {
    @Test
    fun blankPageSelectionMeansAllPages() {
        assertEquals(setOf(0, 1, 2), ImageWatermarkPageSelection.parse("", 3))
    }

    @Test
    fun pageSelectionParsesRangesAndRejectsOutOfBoundsPages() {
        assertEquals(setOf(0, 2, 3), ImageWatermarkPageSelection.parse("1, 3-4", 5))
        assertNull(ImageWatermarkPageSelection.parse("1, 6", 5))
    }

    @Test
    fun settingsRequireSelectedPagesAndBoundedAppearance() {
        assertTrue(ImageWatermarkSettings(setOf(0)).isValid(1))
        assertTrue(!ImageWatermarkSettings(emptySet()).isValid(1))
        assertTrue(!ImageWatermarkSettings(setOf(1)).isValid(1))
        assertTrue(!ImageWatermarkSettings(setOf(0), scale = 0.51f).isValid(1))
    }
}
