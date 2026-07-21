package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerPreviewSamplingTest {
    @Test
    fun keepsCaptureAtOrBelowPreviewBoundUnchanged() {
        assertEquals(1, ScannerPreviewSampling.sampleSize(1200, 900, 1400))
    }

    @Test
    fun samplesLargeCaptureWithoutExceedingMemoryBound() {
        assertEquals(3, ScannerPreviewSampling.sampleSize(4032, 3024, 1400))
    }

    @Test
    fun invalidDimensionsFallBackToSafeSampleSize() {
        assertEquals(1, ScannerPreviewSampling.sampleSize(0, 3000, 1400))
        assertEquals(1, ScannerPreviewSampling.sampleSize(3000, 2000, 0))
    }
}
