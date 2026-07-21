package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeletePageSelectionPlannerTest {
    @Test
    fun deletionProducesOrderedComplement() {
        assertArrayEquals(
            intArrayOf(0, 2, 4),
            DeletePageSelectionPlanner.keptPageIndices(
                pageCount = 5,
                deletedPageIndices = intArrayOf(1, 3),
            ),
        )
    }

    @Test
    fun deletingEveryPageOrNothingIsRejected() {
        assertNull(
            DeletePageSelectionPlanner.keptPageIndices(
                pageCount = 3,
                deletedPageIndices = intArrayOf(0, 1, 2),
            ),
        )
        assertNull(
            DeletePageSelectionPlanner.keptPageIndices(
                pageCount = 3,
                deletedPageIndices = intArrayOf(),
            ),
        )
    }

    @Test
    fun duplicateReorderedOrOutOfBoundsPagesAreRejected() {
        assertNull(
            DeletePageSelectionPlanner.keptPageIndices(4, intArrayOf(1, 1)),
        )
        assertNull(
            DeletePageSelectionPlanner.keptPageIndices(4, intArrayOf(2, 1)),
        )
        assertNull(
            DeletePageSelectionPlanner.keptPageIndices(4, intArrayOf(4)),
        )
    }

    @Test
    fun onePageDocumentCannotDeleteAPage() {
        assertNull(
            DeletePageSelectionPlanner.keptPageIndices(1, intArrayOf(0)),
        )
    }
}
