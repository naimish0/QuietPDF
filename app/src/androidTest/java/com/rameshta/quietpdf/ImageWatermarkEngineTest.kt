package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ImageWatermarkAnalysis
import com.rameshta.quietpdf.pdf.ImageWatermarkAnalysisResult
import com.rameshta.quietpdf.pdf.ImageWatermarkEngine
import com.rameshta.quietpdf.pdf.ImageWatermarkPosition
import com.rameshta.quietpdf.pdf.ImageWatermarkPreviewResult
import com.rameshta.quietpdf.pdf.ImageWatermarkResult
import com.rameshta.quietpdf.pdf.ImageWatermarkSettings
import com.rameshta.quietpdf.pdf.WatermarkImageAnalysisResult
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
class ImageWatermarkEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun apply_addsTransparentImageOnlyToSelectedPages_andPreservesSourceAndGeometry() {
        val source = File(context.cacheDir, "image-watermark-source.pdf")
        val image = File(context.cacheDir, "image-watermark-transparent.png")
        val output = File(context.cacheDir, "image-watermark-output.pdf")
        val settings = ImageWatermarkSettings(
            pageIndices = setOf(0, 2),
            position = ImageWatermarkPosition.TopRight,
            opacity = 0.65f,
            rotationDegrees = 45,
            scale = 0.3f,
        )
        try {
            writeThreePagePdf(source)
            writeTransparentPng(image)
            val sourceBytes = source.readBytes()
            val engine = ImageWatermarkEngine(context)
            val before = (0..2).map { renderPage(source, it) }
            assertEquals(
                ImageWatermarkAnalysisResult.Ready(ImageWatermarkAnalysis(3)),
                runBlocking { engine.analyzePdf(Uri.fromFile(source)) },
            )
            assertTrue(runBlocking { engine.analyzeImage(Uri.fromFile(image)) } is WatermarkImageAnalysisResult.Ready)
            val preview = runBlocking {
                engine.preview(Uri.fromFile(source), Uri.fromFile(image), 0, settings, 500)
            }
            assertTrue(preview is ImageWatermarkPreviewResult.Ready)
            assertEquals(
                ImageWatermarkResult.Success(3, 2),
                runBlocking {
                    engine.apply(Uri.fromFile(source), Uri.fromFile(image), Uri.fromFile(output), settings, 3)
                },
            )
            assertEquals(sourceBytes.asList(), source.readBytes().asList())
            PDDocument.load(output).use { document ->
                assertEquals(3, document.numberOfPages)
                assertEquals(500f, document.getPage(0).cropBox.width)
                assertEquals(700f, document.getPage(0).cropBox.height)
                assertEquals(90, document.getPage(2).rotation)
            }
            val after = (0..2).map { renderPage(output, it) }
            assertFalse(bitmapsMatch(before[0], after[0]))
            assertTrue(bitmapsMatch(before[1], after[1]))
            assertFalse(bitmapsMatch(before[2], after[2]))
            val previewBitmap = (preview as ImageWatermarkPreviewResult.Ready).bitmap
            context.getExternalFilesDir(null)?.let { artifacts ->
                File(artifacts, "image-watermark-render.png").outputStream().use {
                    assertTrue(previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                File(artifacts, "image-watermark-rotated-render.png").outputStream().use {
                    assertTrue(after[2].compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                output.copyTo(File(artifacts, "image-watermark-output.pdf"), overwrite = true)
            }
            before.forEach(Bitmap::recycle)
            after.forEach(Bitmap::recycle)
            previewBitmap.recycle()
        } finally {
            source.delete(); image.delete(); output.delete()
        }
    }

    @Test
    fun invalidImageIsRejectedAndPlaceholderOutputIsRemoved() {
        val source = File(context.cacheDir, "image-watermark-invalid-source.pdf")
        val image = File(context.cacheDir, "image-watermark-invalid.bin")
        val output = File(context.cacheDir, "image-watermark-invalid-output.pdf")
        try {
            writeThreePagePdf(source)
            image.writeText("not an image")
            output.writeText("placeholder")
            assertEquals(
                WatermarkImageAnalysisResult.InvalidImage,
                runBlocking { ImageWatermarkEngine(context).analyzeImage(Uri.fromFile(image)) },
            )
            assertEquals(
                ImageWatermarkResult.InvalidImage,
                runBlocking {
                    ImageWatermarkEngine(context).apply(
                        Uri.fromFile(source), Uri.fromFile(image), Uri.fromFile(output),
                        ImageWatermarkSettings(setOf(0)), 3,
                    )
                },
            )
            assertFalse(output.exists())
        } finally {
            source.delete(); image.delete(); output.delete()
        }
    }

    private fun writeTransparentPng(file: File) {
        val bitmap = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawCircle(80f, 80f, 65f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED })
        canvas.drawRect(160f, 20f, 300f, 140f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x880000FF.toInt() })
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun writeThreePagePdf(file: File) {
        val document = PdfDocument()
        try {
            listOf(500 to 700, 600 to 600, 700 to 500).forEachIndexed { index, size ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(size.first, size.second, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("Original page ${index + 1}", 40f, 100f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f })
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        PDDocument.load(file).use { document -> document.getPage(2).rotation = 90; document.save(file) }
    }

    private fun renderPage(file: File, page: Int): Bitmap =
        PDDocument.load(file).use { PDFRenderer(it).renderImage(page, 1f) }

    private fun bitmapsMatch(first: Bitmap, second: Bitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val a = IntArray(first.width * first.height)
        val b = IntArray(second.width * second.height)
        first.getPixels(a, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(b, 0, second.width, 0, 0, second.width, second.height)
        return a.contentEquals(b)
    }
}
