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

    @Test
    fun targetSizeParserAcceptsDecimalMegabytesBelowOriginal() {
        assertEquals(2_500_000L, TargetFileSize.parseMegabytes("2.5", 4_000_000L))
    }

    @Test
    fun targetSizeParserRejectsUnsafeOrUnreachableInputBounds() {
        assertEquals(null, TargetFileSize.parseMegabytes("", 4_000_000L))
        assertEquals(null, TargetFileSize.parseMegabytes("0.001", 4_000_000L))
        assertEquals(null, TargetFileSize.parseMegabytes("4", 4_000_000L))
        assertEquals(null, TargetFileSize.parseMegabytes("999999999999999999", 4_000_000L))
    }

    @Test
    fun targetCompressionAttemptsBecomeProgressivelyStrongerAndStayBounded() {
        val attempts = TargetCompressionPlanner.attempts
        assertEquals(7, attempts.size)
        attempts.zipWithNext().forEach { (first, second) ->
            assertTrue(second.maxImageDimension < first.maxImageDimension)
            assertTrue(second.jpegQuality < first.jpegQuality)
        }
        assertTrue(attempts.last().maxImageDimension >= 800)
        assertTrue(attempts.last().jpegQuality >= 35)
    }
}
