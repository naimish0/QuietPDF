package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RearrangePageOrderTest {
    @Test
    fun move_repositionsOnePageWithoutChangingTheOthers() {
        assertEquals(listOf(1, 2, 0, 3), RearrangePageOrder.move(listOf(0, 1, 2, 3), 0, 2))
        assertEquals(listOf(0, 3, 1, 2), RearrangePageOrder.move(listOf(0, 1, 2, 3), 3, 1))
    }

    @Test
    fun move_ignoresInvalidPositions() {
        val original = listOf(0, 1, 2)
        assertEquals(original, RearrangePageOrder.move(original, -1, 1))
        assertEquals(original, RearrangePageOrder.move(original, 1, 3))
    }

    @Test
    fun completePermutation_requiresEveryPageExactlyOnce() {
        assertTrue(RearrangePageOrder.isCompletePermutation(intArrayOf(2, 0, 3, 1), 4))
        assertFalse(RearrangePageOrder.isCompletePermutation(intArrayOf(0, 1, 1, 3), 4))
        assertFalse(RearrangePageOrder.isCompletePermutation(intArrayOf(0, 1, 2), 4))
        assertFalse(RearrangePageOrder.isCompletePermutation(intArrayOf(0), 1))
    }
}
