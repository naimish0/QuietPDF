package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractPageSelectionParserTest {
    @Test
    fun pagesAndRanges_expandInOriginalOrder() {
        assertArrayEquals(
            intArrayOf(0, 2, 4, 5, 6),
            ExtractPageSelectionParser.parse("1, 3, 5-7", pageCount = 8),
        )
    }

    @Test
    fun whitespaceIsAcceptedWithoutChangingSelection() {
        assertArrayEquals(
            intArrayOf(0, 1, 3),
            ExtractPageSelectionParser.parse(" 1 - 2 , 4 ", pageCount = 4),
        )
    }

    @Test
    fun duplicatesAndReorderingAreRejected() {
        assertNull(ExtractPageSelectionParser.parse("1-3, 3-4", pageCount = 5))
        assertNull(ExtractPageSelectionParser.parse("4, 2", pageCount = 5))
    }

    @Test
    fun malformedOrOutOfBoundsSelectionsAreRejected() {
        assertNull(ExtractPageSelectionParser.parse("", pageCount = 5))
        assertNull(ExtractPageSelectionParser.parse("0, 2", pageCount = 5))
        assertNull(ExtractPageSelectionParser.parse("2-6", pageCount = 5))
        assertNull(ExtractPageSelectionParser.parse("3-2", pageCount = 5))
        assertNull(ExtractPageSelectionParser.parse("1,,3", pageCount = 5))
    }
}
