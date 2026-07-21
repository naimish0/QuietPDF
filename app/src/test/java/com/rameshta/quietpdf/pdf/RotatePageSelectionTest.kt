package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotatePageSelectionTest {
    @Test
    fun validSelection_acceptsOrderedUniqueSubset() {
        assertTrue(RotatePageSelection.isValid(intArrayOf(0, 2, 4), pageCount = 5))
        assertTrue(RotatePageSelection.isValid(intArrayOf(0), pageCount = 1))
    }

    @Test
    fun validSelection_rejectsMissingDuplicateUnorderedAndOutOfBoundsPages() {
        assertFalse(RotatePageSelection.isValid(intArrayOf(), pageCount = 5))
        assertFalse(RotatePageSelection.isValid(intArrayOf(1, 1), pageCount = 5))
        assertFalse(RotatePageSelection.isValid(intArrayOf(2, 1), pageCount = 5))
        assertFalse(RotatePageSelection.isValid(intArrayOf(5), pageCount = 5))
    }

    @Test
    fun rotationValues_mapToRelativePdfQuarterTurns() {
        assertTrue(PageRotation.Clockwise90.quarterTurnsClockwise == 1)
        assertTrue(PageRotation.HalfTurn.quarterTurnsClockwise == 2)
        assertTrue(PageRotation.CounterClockwise90.quarterTurnsClockwise == 3)
    }
}
