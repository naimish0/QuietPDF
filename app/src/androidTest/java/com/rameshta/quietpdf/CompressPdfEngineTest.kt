package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.CompressPdfAnalysisResult
import com.rameshta.quietpdf.pdf.CompressPdfEngine
import com.rameshta.quietpdf.pdf.CompressPdfResult
import com.rameshta.quietpdf.pdf.PdfCompressionMode
import com.rameshta.quietpdf.pdf.PdfCompressionRequest
import com.rameshta.quietpdf.pdf.PdfSearchEngine
import com.rameshta.quietpdf.pdf.PdfSearchResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompressPdfEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun compress_reducesImagePdfWhilePreservingPagesAndSearchableText() {
        val source = File(context.cacheDir, "compress-image-source.pdf")
        val output = File(context.cacheDir, "compress-image-output.pdf")
        try {
            writeImagePdf(source)
            val originalBytes = source.readBytes()
            val engine = CompressPdfEngine(context)
            val analysis = runBlocking { engine.analyze(Uri.fromFile(source)) }
            assertTrue(analysis is CompressPdfAnalysisResult.Ready)
            val ready = analysis as CompressPdfAnalysisResult.Ready
            assertTrue(ready.analysis.compressibleImages.isNotEmpty())

            val progress = mutableListOf<Pair<Int, Int>>()
            val result = runBlocking {
                engine.compress(
                    Uri.fromFile(source),
                    Uri.fromFile(output),
                    PdfCompressionMode.MaximumCompression,
                    ready.analysis.pageCount,
                    ready.analysis.originalSizeBytes,
                ) { completed, total -> progress += completed to total }
            }

            assertTrue(result.toString(), result is CompressPdfResult.Success)
            val success = result as CompressPdfResult.Success
            assertTrue(success.outputSizeBytes < success.originalSizeBytes)
            assertTrue(success.recompressedImageCount > 0)
            assertEquals(listOf(0 to 2, 1 to 2, 2 to 2), progress)
            assertEquals(originalBytes.asList(), source.readBytes().asList())
            assertPageCount(output, 2)
            val searchEngine = PdfSearchEngine(context)
            try {
                val search = runBlocking { searchEngine.search(Uri.fromFile(output), 2, "SEARCHABLE") }
                assertTrue(search.toString(), search is PdfSearchResult.Matches)
                assertEquals(1, (search as PdfSearchResult.Matches).matches.single().pageIndex)
            } finally {
                searchEngine.close()
            }
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun compress_rejectsOutputThatIsNotSmallerAndRemovesPlaceholder() {
        val source = File(context.cacheDir, "compress-text-source.pdf")
        val output = File(context.cacheDir, "compress-text-output.pdf")
        try {
            writeMixedContentPdf(source)
            val engine = CompressPdfEngine(context)
            val analysis = runBlocking { engine.analyze(Uri.fromFile(source)) }
            assertTrue(analysis is CompressPdfAnalysisResult.Ready)
            val ready = analysis as CompressPdfAnalysisResult.Ready
            assertTrue(ready.analysis.compressibleImages.isEmpty())
            output.writeText("placeholder")

            val result = runBlocking {
                engine.compress(
                    Uri.fromFile(source),
                    Uri.fromFile(output),
                    PdfCompressionMode.Balanced,
                    ready.analysis.pageCount,
                    ready.analysis.originalSizeBytes,
                )
            }

            assertTrue(result is CompressPdfResult.NotSmaller)
            assertFalse(output.exists())
            assertPageCount(source, 1)
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun targetSize_savesBestValidatedResultWhenTargetCannotBeReached() {
        val source = File(context.cacheDir, "target-size-source.pdf")
        val output = File(context.cacheDir, "target-size-output.pdf")
        try {
            writeImagePdf(source)
            val originalBytes = source.readBytes()
            val engine = CompressPdfEngine(context)
            val analysis = runBlocking { engine.analyze(Uri.fromFile(source)) }
            assertTrue(analysis is CompressPdfAnalysisResult.Ready)
            val ready = analysis as CompressPdfAnalysisResult.Ready
            val attempts = mutableListOf<Int>()

            val result = runBlocking {
                engine.compress(
                    sourceUri = Uri.fromFile(source),
                    outputUri = Uri.fromFile(output),
                    request = PdfCompressionRequest.TargetSize(10_000),
                    expectedPageCount = ready.analysis.pageCount,
                    expectedOriginalSizeBytes = ready.analysis.originalSizeBytes,
                ) { progress -> attempts += progress.attempt }
            }

            assertTrue(result.toString(), result is CompressPdfResult.Success)
            val success = result as CompressPdfResult.Success
            assertEquals(10_000L, success.targetSizeBytes)
            assertFalse(success.targetReached)
            assertTrue(success.outputSizeBytes < success.originalSizeBytes)
            assertEquals(7, attempts.maxOrNull())
            assertEquals(originalBytes.asList(), source.readBytes().asList())
            assertPageCount(output, 2)
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun writeImagePdf(file: File) {
        val bitmap = Bitmap.createBitmap(1800, 1800, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(1800 * 1800) { index ->
            val x = index % 1800
            val y = index / 1800
            Color.rgb((x * 31 + y * 17) and 0xff, (x * 7 + y * 29) and 0xff, (x + y * 13) and 0xff)
        }
        bitmap.setPixels(pixels, 0, 1800, 0, 0, 1800, 1800)
        val document = PdfDocument()
        try {
            val imagePage = document.startPage(PdfDocument.PageInfo.Builder(900, 900, 1).create())
            imagePage.canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, 900, 900), null)
            document.finishPage(imagePage)
            val textPage = document.startPage(PdfDocument.PageInfo.Builder(900, 300, 2).create())
            textPage.canvas.drawText("SEARCHABLE", 40f, 160f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 28f
            })
            document.finishPage(textPage)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
            bitmap.recycle()
        }
    }

    private fun writeMixedContentPdf(file: File) {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(600, 700, 1).create())
            page.canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, 600, 600), null)
            page.canvas.drawText("TEXT ONLY", 30f, 80f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 24f
            })
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
            bitmap.recycle()
        }
    }

    private fun assertPageCount(file: File, expected: Int) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertEquals(expected, renderer.pageCount) }
        }
    }
}
