package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
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
import java.security.MessageDigest
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

    @Test
    fun approximatelyTenMegabytePdf_compressesWithEveryCategoryAndPreservesContent() {
        val artifactDirectory = File(
            requireNotNull(context.getExternalFilesDir(null)),
            "compression-10mb-categories",
        ).apply { mkdirs() }
        val source = File(artifactDirectory, "source.pdf")
        val categoryOutputs = linkedMapOf(
            "high-quality" to PdfCompressionRequest.Quality(PdfCompressionMode.HighQuality),
            "balanced" to PdfCompressionRequest.Quality(PdfCompressionMode.Balanced),
            "maximum-compression" to PdfCompressionRequest.Quality(
                PdfCompressionMode.MaximumCompression,
            ),
            "target-3mb" to PdfCompressionRequest.TargetSize(3_000_000L),
        )
        val outputs = categoryOutputs.keys.associateWith { File(artifactDirectory, "$it.pdf") }
        val keepArtifacts = InstrumentationRegistry.getArguments()
            .getString("keepCompressionArtifacts") == "true"
        try {
            (listOf(source) + outputs.values).forEach { it.delete() }
            writeImagePdf(source, imagePageCount = 7, pageDimension = 1800)
            assertTrue(
                "Expected an approximately 10 MB fixture but was ${source.length()} bytes",
                source.length() in 8_000_000L..14_000_000L,
            )
            val sourceDigest = sha256(source)
            val engine = CompressPdfEngine(context)
            val analysis = runBlocking { engine.analyze(Uri.fromFile(source)) }
            assertTrue(analysis.toString(), analysis is CompressPdfAnalysisResult.Ready)
            val ready = analysis as CompressPdfAnalysisResult.Ready
            assertTrue(ready.analysis.compressibleImages.isNotEmpty())

            val successes = linkedMapOf<String, CompressPdfResult.Success>()
            categoryOutputs.forEach { (category, request) ->
                val output = requireNotNull(outputs[category])
                val result = runBlocking {
                    engine.compress(
                        sourceUri = Uri.fromFile(source),
                        outputUri = Uri.fromFile(output),
                        request = request,
                        expectedPageCount = ready.analysis.pageCount,
                        expectedOriginalSizeBytes = ready.analysis.originalSizeBytes,
                    )
                }
                assertTrue("$category: $result", result is CompressPdfResult.Success)
                val success = result as CompressPdfResult.Success
                assertTrue("$category did not reduce the PDF", success.outputSizeBytes < source.length())
                successes[category] = success
                assertEquals(sourceDigest, sha256(source))
                assertPageCount(output, ready.analysis.pageCount)
                assertFirstPageRenders(output)
                assertSearchableText(output, ready.analysis.pageCount)
                Log.i(
                    MetricsLogTag,
                    "$category input=${success.originalSizeBytes} output=${success.outputSizeBytes} " +
                        "images=${success.recompressedImageCount} target=${success.targetSizeBytes} " +
                        "reached=${success.targetReached}",
                )
            }
            assertTrue(
                "Balanced should not be larger than High Quality",
                requireNotNull(successes["balanced"]).outputSizeBytes <=
                    requireNotNull(successes["high-quality"]).outputSizeBytes,
            )
            assertTrue(
                "Maximum Compression should not be larger than Balanced",
                requireNotNull(successes["maximum-compression"]).outputSizeBytes <=
                    requireNotNull(successes["balanced"]).outputSizeBytes,
            )
        } finally {
            if (!keepArtifacts) {
                (listOf(source) + outputs.values).forEach { it.delete() }
                artifactDirectory.delete()
            }
        }
    }

    private fun writeImagePdf(
        file: File,
        imagePageCount: Int = 1,
        pageDimension: Int = 900,
    ) {
        val document = PdfDocument()
        try {
            repeat(imagePageCount) { pageIndex ->
                val bitmap = Bitmap.createBitmap(1800, 1800, Bitmap.Config.ARGB_8888)
                try {
                    val pixels = IntArray(1800 * 1800) { index ->
                        val x = index % 1800
                        val y = index / 1800
                        Color.rgb(
                            (x * 31 + y * 17 + pageIndex * 43) and 0xff,
                            (x * 7 + y * 29 + pageIndex * 71) and 0xff,
                            (x + y * 13 + pageIndex * 97) and 0xff,
                        )
                    }
                    bitmap.setPixels(pixels, 0, 1800, 0, 0, 1800, 1800)
                    val imagePage = document.startPage(
                        PdfDocument.PageInfo.Builder(
                            pageDimension,
                            pageDimension,
                            pageIndex + 1,
                        ).create(),
                    )
                    imagePage.canvas.drawBitmap(
                        bitmap,
                        null,
                        android.graphics.Rect(0, 0, pageDimension, pageDimension),
                        null,
                    )
                    document.finishPage(imagePage)
                } finally {
                    bitmap.recycle()
                }
            }
            val textPage = document.startPage(
                PdfDocument.PageInfo.Builder(900, 300, imagePageCount + 1).create(),
            )
            textPage.canvas.drawText("SEARCHABLE", 40f, 160f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 28f
            })
            document.finishPage(textPage)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
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

    private fun assertSearchableText(file: File, pageCount: Int) {
        val searchEngine = PdfSearchEngine(context)
        try {
            val search = runBlocking {
                searchEngine.search(Uri.fromFile(file), pageCount, "SEARCHABLE")
            }
            assertTrue(search.toString(), search is PdfSearchResult.Matches)
            assertEquals(pageCount - 1, (search as PdfSearchResult.Matches).matches.single().pageIndex)
        } finally {
            searchEngine.close()
        }
    }

    private fun assertFirstPageRenders(file: File) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val rendered = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                    try {
                        rendered.eraseColor(Color.WHITE)
                        page.render(
                            rendered,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )
                        val sampledColors = mutableSetOf<Int>()
                        for (y in 0 until rendered.height step 16) {
                            for (x in 0 until rendered.width step 16) {
                                sampledColors += rendered.getPixel(x, y)
                            }
                        }
                        assertTrue("Rendered first page was blank", sampledColors.size > 16)
                    } finally {
                        rendered.recycle()
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MetricsLogTag = "CompressPdf10Mb"
    }
}
