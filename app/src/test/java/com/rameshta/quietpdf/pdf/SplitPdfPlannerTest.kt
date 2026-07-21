package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitPdfPlannerTest {
    @Test
    fun eachPage_createsOneCompletePartPerPage() {
        assertEquals(
            listOf(
                SplitPageRange(0, 0),
                SplitPageRange(1, 1),
                SplitPageRange(2, 2),
            ),
            SplitPdfPlanner.createPlan(SplitPdfMode.EachPage, pageCount = 3),
        )
    }

    @Test
    fun customRanges_requireCompleteOrderedCoverage() {
        assertEquals(
            listOf(SplitPageRange(0, 2), SplitPageRange(3, 5), SplitPageRange(6, 7)),
            SplitPdfPlanner.createPlan(
                mode = SplitPdfMode.CustomRanges,
                pageCount = 8,
                customRanges = "1-3, 4-6, 7-8",
            ),
        )
        assertNull(
            SplitPdfPlanner.createPlan(
                mode = SplitPdfMode.CustomRanges,
                pageCount = 8,
                customRanges = "1-3, 5-8",
            ),
        )
        assertNull(
            SplitPdfPlanner.createPlan(
                mode = SplitPdfMode.CustomRanges,
                pageCount = 8,
                customRanges = "1-4, 4-8",
            ),
        )
    }

    @Test
    fun everyPages_includesFinalShortPart() {
        assertEquals(
            listOf(SplitPageRange(0, 2), SplitPageRange(3, 5), SplitPageRange(6, 7)),
            SplitPdfPlanner.createPlan(
                mode = SplitPdfMode.EveryPages,
                pageCount = 8,
                everyPages = "3",
            ),
        )
        assertNull(
            SplitPdfPlanner.createPlan(
                mode = SplitPdfMode.EveryPages,
                pageCount = 8,
                everyPages = "8",
            ),
        )
    }

    @Test
    fun onePageDocument_cannotProduceARealSplit() {
        assertNull(SplitPdfPlanner.createPlan(SplitPdfMode.EachPage, pageCount = 1))
    }
}
