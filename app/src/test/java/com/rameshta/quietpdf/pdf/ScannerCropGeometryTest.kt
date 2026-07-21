package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerCropGeometryTest {
    @Test
    fun fullImageCropIsValidAndBoundedToOutputLimit() {
        val crop = ScannerCropSelection.fullImage()

        assertTrue(ScannerCropGeometry.isValid(crop))
        assertEquals(
            ScannerCropOutputSize(3000, 2250),
            ScannerCropGeometry.outputSize(4000, 3000, crop),
        )
    }

    @Test
    fun crossingACornerIsRejected() {
        val crop = ScannerCropSelection.fullImage(inset = 0.05f)

        val moved = crop.moveCorner(0, ScannerCropPoint(0.95f, 0.95f))

        assertEquals(crop, moved)
        assertFalse(
            ScannerCropGeometry.isValid(
                crop.copy(topLeft = ScannerCropPoint(0.95f, 0.95f)),
            ),
        )
    }

    @Test
    fun perspectiveCropUsesLongestOpposingEdges() {
        val crop = ScannerCropSelection(
            topLeft = ScannerCropPoint(0.2f, 0.1f),
            topRight = ScannerCropPoint(0.8f, 0.2f),
            bottomRight = ScannerCropPoint(0.9f, 0.9f),
            bottomLeft = ScannerCropPoint(0.1f, 0.8f),
        )

        val size = requireNotNull(ScannerCropGeometry.outputSize(1000, 1000, crop))

        assertTrue(size.width in 805..807)
        assertTrue(size.height in 706..708)
    }
}
