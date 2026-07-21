package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextWatermarkSettingsTest {
    @Test
    fun blankPageSelectionMeansAllPages() {
        assertEquals(setOf(0, 1, 2), TextWatermarkPageSelection.parse("", 3))
    }

    @Test
    fun pageSelectionParsesRangesAndRejectsOutOfBoundsPages() {
        assertEquals(setOf(0, 2, 3), TextWatermarkPageSelection.parse("1, 3-4", 5))
        assertNull(TextWatermarkPageSelection.parse("1, 6", 5))
    }

    @Test
    fun settingsRequireExplicitTextAndSelectedPages() {
        assertTrue(TextWatermarkSettings("Private", setOf(0)).isValid(1))
        assertTrue(!TextWatermarkSettings(" ", setOf(0)).isValid(1))
        assertTrue(!TextWatermarkSettings("Private", emptySet()).isValid(1))
    }
}
