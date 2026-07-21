package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicatePagePlanTest {
    @Test
    fun selectedPagesAreCopiedImmediatelyAfterTheirOriginals() {
        assertArrayEquals(
            intArrayOf(0, 0, 1, 2, 2, 3),
            DuplicatePagePlan.outputPageOrder(intArrayOf(0, 2), 4),
        )
    }

    @Test
    fun everyPageCanBeDuplicated() {
        assertArrayEquals(
            intArrayOf(0, 0, 1, 1, 2, 2),
            DuplicatePagePlan.outputPageOrder(intArrayOf(0, 1, 2), 3),
        )
    }

    @Test
    fun emptyDuplicateReorderedOrOutOfBoundsSelectionsAreRejected() {
        assertNull(DuplicatePagePlan.outputPageOrder(intArrayOf(), 3))
        assertNull(DuplicatePagePlan.outputPageOrder(intArrayOf(1, 1), 3))
        assertNull(DuplicatePagePlan.outputPageOrder(intArrayOf(2, 1), 3))
        assertNull(DuplicatePagePlan.outputPageOrder(intArrayOf(3), 3))
        assertNull(DuplicatePagePlan.outputPageOrder(intArrayOf(0), 0))
    }
}
