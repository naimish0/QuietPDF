package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionEstimateTest {
    private val images = listOf(
        CompressibleImage(encodedSizeBytes = 800_000, width = 4000, height = 3000),
        CompressibleImage(encodedSizeBytes = 200_000, width = 1200, height = 900),
    )

    @Test
    fun strongerModesProduceSmallerContentAwareEstimates() {
        val high = CompressionEstimate.outputSize(1_500_000, images, PdfCompressionMode.HighQuality)
        val balanced = CompressionEstimate.outputSize(1_500_000, images, PdfCompressionMode.Balanced)
        val maximum = CompressionEstimate.outputSize(
            1_500_000,
            images,
            PdfCompressionMode.MaximumCompression,
        )

        assertTrue(maximum < balanced)
        assertTrue(balanced < high)
        assertTrue(high < 1_500_000)
    }

    @Test
    fun documentsWithoutEligibleImagesAreNotPromisedSavings() {
        assertEquals(
            1_500_000,
            CompressionEstimate.outputSize(1_500_000, emptyList(), PdfCompressionMode.Balanced),
        )
    }

    @Test
    fun estimateNeverExceedsOriginalOrFallsBelowOneByte() {
        val oversizedMetadata = listOf(CompressibleImage(Long.MAX_VALUE, 10_000, 10_000))
        val estimate = CompressionEstimate.outputSize(
            10_000,
            oversizedMetadata,
            PdfCompressionMode.MaximumCompression,
        )
        assertTrue(estimate in 1..10_000)
    }
}
