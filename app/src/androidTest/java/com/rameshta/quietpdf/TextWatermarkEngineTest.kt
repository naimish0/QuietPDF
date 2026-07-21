package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ProtectPdfEngine
import com.rameshta.quietpdf.pdf.TextWatermarkAnalysisResult
import com.rameshta.quietpdf.pdf.TextWatermarkEngine
import com.rameshta.quietpdf.pdf.TextWatermarkPosition
import com.rameshta.quietpdf.pdf.TextWatermarkPreviewResult
import com.rameshta.quietpdf.pdf.TextWatermarkResult
import com.rameshta.quietpdf.pdf.TextWatermarkSettings
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextWatermarkEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun apply_addsTextOnlyToSelectedPages_andPreservesDocumentGeometryAndSource() {
        val source = File(context.cacheDir, "text-watermark-source.pdf")
        val output = File(context.cacheDir, "text-watermark-output.pdf")
        val settings = TextWatermarkSettings(
            text = "CONFIDENTIAL 2026",
            pageIndices = setOf(0, 2),
            position = TextWatermarkPosition.TopRight,
            opacity = 0.5f,
            rotationDegrees = 45,
            scale = 0.1f,
        )
        try {
            writeThreePagePdf(source)
            val sourceBytes = source.readBytes()
            val engine = TextWatermarkEngine(context)
            val sourceFirst = renderPage(source, 0)
            val sourceMiddle = renderPage(source, 1)
            val sourceLast = renderPage(source, 2)
            val analysis = runBlocking { engine.analyze(Uri.fromFile(source)) }
            assertEquals(TextWatermarkAnalysisResult.Ready(com.rameshta.quietpdf.pdf.TextWatermarkAnalysis(3)), analysis)

            val preview = runBlocking {
                engine.preview(Uri.fromFile(source), 0, settings, 500)
            }
            assertTrue(preview is TextWatermarkPreviewResult.Ready)
            val result = runBlocking {
                engine.apply(Uri.fromFile(source), Uri.fromFile(output), settings, 3)
            }

            assertEquals(TextWatermarkResult.Success(3, 2), result)
            assertEquals(sourceBytes.asList(), source.readBytes().asList())
            PDDocument.load(output).use { document ->
                assertEquals(3, document.numberOfPages)
                assertEquals(500f, document.getPage(0).cropBox.width)
                assertEquals(700f, document.getPage(0).cropBox.height)
                assertEquals(90, document.getPage(2).rotation)
            }
            val outputFirst = renderPage(output, 0)
            val outputMiddle = renderPage(output, 1)
            val outputLast = renderPage(output, 2)
            assertFalse(bitmapsMatch(sourceFirst, outputFirst))
            assertTrue(bitmapsMatch(sourceMiddle, outputMiddle))
            assertFalse(bitmapsMatch(sourceLast, outputLast))
            val previewBitmap = (preview as TextWatermarkPreviewResult.Ready).bitmap
            context.getExternalFilesDir(null)?.let { artifactDirectory ->
                File(artifactDirectory, "text-watermark-render.png").outputStream().use { stream ->
                    assertTrue(previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                output.copyTo(File(artifactDirectory, "text-watermark-output.pdf"), overwrite = true)
                File(artifactDirectory, "text-watermark-rotated-render.png").outputStream().use { stream ->
                    assertTrue(outputLast.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
            }
            sourceFirst.recycle()
            sourceMiddle.recycle()
            sourceLast.recycle()
            outputFirst.recycle()
            outputMiddle.recycle()
            outputLast.recycle()
            previewBitmap.recycle()
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun apply_rejectsInvalidSettingsAndRemovesPlaceholder() {
        val source = File(context.cacheDir, "text-watermark-invalid-source.pdf")
        val output = File(context.cacheDir, "text-watermark-invalid-output.pdf")
        try {
            writeThreePagePdf(source)
            output.writeText("placeholder")
            val result = runBlocking {
                TextWatermarkEngine(context).apply(
                    Uri.fromFile(source),
                    Uri.fromFile(output),
                    TextWatermarkSettings("", setOf(0)),
                    3,
                )
            }
            assertEquals(TextWatermarkResult.InvalidSettings, result)
            assertFalse(output.exists())
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun analyze_rejectsPasswordProtectedPdf() {
        val source = File(context.cacheDir, "text-watermark-protected-source.pdf")
        val protected = File(context.cacheDir, "text-watermark-protected.pdf")
        try {
            writeThreePagePdf(source)
            runBlocking {
                ProtectPdfEngine(context).protect(
                    Uri.fromFile(source), Uri.fromFile(protected), "secret!".toCharArray(), 3,
                )
            }
            assertEquals(
                TextWatermarkAnalysisResult.PasswordProtected,
                runBlocking { TextWatermarkEngine(context).analyze(Uri.fromFile(protected)) },
            )
        } finally {
            source.delete()
            protected.delete()
        }
    }

    private fun writeThreePagePdf(file: File) {
        val document = PdfDocument()
        try {
            listOf(500 to 700, 600 to 600, 700 to 500).forEachIndexed { index, size ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(size.first, size.second, index + 1).create(),
                )
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(
                    "Original page ${index + 1}",
                    40f,
                    100f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f },
                )
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        PDDocument.load(file).use { document ->
            document.getPage(2).rotation = 90
            document.save(file)
        }
    }

    private fun renderPage(file: File, pageIndex: Int): Bitmap =
        PDDocument.load(file).use { document -> PDFRenderer(document).renderImage(pageIndex, 1f) }

    private fun bitmapsMatch(first: Bitmap, second: Bitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val firstPixels = IntArray(first.width * first.height)
        val secondPixels = IntArray(second.width * second.height)
        first.getPixels(firstPixels, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(secondPixels, 0, second.width, 0, 0, second.width, second.height)
        return firstPixels.contentEquals(secondPixels)
    }
}
