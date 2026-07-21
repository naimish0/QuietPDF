package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ScannerCaptureEngine
import com.rameshta.quietpdf.pdf.ScannerCropCorrectionEngine
import com.rameshta.quietpdf.pdf.ScannerCropPoint
import com.rameshta.quietpdf.pdf.ScannerCropResult
import com.rameshta.quietpdf.pdf.ScannerCropSelection
import com.rameshta.quietpdf.pdf.ScannerPdfResult
import com.rameshta.quietpdf.pdf.ScannerPreviewResult
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannerCaptureEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun capturedJpeg_isSampledAndSavedAsValidatedSinglePagePdf() {
        val capture = File(context.cacheDir, "scanner-engine-capture.jpg")
        val output = File(context.cacheDir, "scanner-engine-output.pdf")
        try {
            writeJpeg(capture, 1800, 1200)
            val engine = ScannerCaptureEngine(context)
            val preview = runBlocking { engine.preparePreview(capture) }
            assertTrue(preview is ScannerPreviewResult.Ready)
            preview as ScannerPreviewResult.Ready
            try {
                assertEquals(1800, preview.preview.sourceWidth)
                assertEquals(1200, preview.preview.sourceHeight)
                assertTrue(preview.preview.bitmap.width <= 1400)
                assertTrue(preview.preview.bitmap.height <= 1400)
            } finally {
                preview.preview.bitmap.recycle()
            }

            val result = runBlocking {
                engine.createSinglePagePdf(capture, Uri.fromFile(output))
            }
            assertEquals(ScannerPdfResult.Success, result)
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> assertEquals(1, renderer.pageCount) }
            }
            assertTrue(capture.exists())
        } finally {
            capture.delete()
            output.delete()
        }
    }

    @Test
    fun emptyCaptureIsRejectedAndTemporaryCaptureCanBeDiscarded() {
        val capture = File(context.cacheDir, "scanner-empty-capture.jpg")
        try {
            capture.writeBytes(byteArrayOf())
            val engine = ScannerCaptureEngine(context)
            assertEquals(
                ScannerPreviewResult.InvalidImage,
                runBlocking { engine.preparePreview(capture) },
            )
            engine.discard(capture)
            assertFalse(capture.exists())
        } finally {
            capture.delete()
        }
    }

    @Test
    fun trapezoidDocumentIsDetectedAndPerspectiveCorrected() {
        val capture = File(context.cacheDir, "scanner-trapezoid-capture.jpg")
        val output = File(context.cacheDir, "scanner-trapezoid-output.pdf")
        var corrected: File? = null
        try {
            writeTrapezoidJpeg(capture)
            val scanner = ScannerCaptureEngine(context)
            val preview = runBlocking { scanner.preparePreview(capture) }
            assertTrue(preview is ScannerPreviewResult.Ready)
            preview as ScannerPreviewResult.Ready
            try {
                assertTrue(preview.preview.automaticCropDetected)
            } finally {
                preview.preview.bitmap.recycle()
            }

            val crop = ScannerCropSelection(
                topLeft = ScannerCropPoint(0.20f, 0.10f),
                topRight = ScannerCropPoint(0.80f, 0.18f),
                bottomRight = ScannerCropPoint(0.90f, 0.90f),
                bottomLeft = ScannerCropPoint(0.10f, 0.82f),
            )
            val result = runBlocking { ScannerCropCorrectionEngine(context.cacheDir).correct(capture, crop) }
            assertTrue(result is ScannerCropResult.Ready)
            corrected = (result as ScannerCropResult.Ready).file
            val bitmap = BitmapFactory.decodeFile(corrected.absolutePath)
            try {
                assertTrue(bitmap.width > 500)
                assertTrue(bitmap.height > 500)
                listOf(
                    bitmap.getPixel(12, 12),
                    bitmap.getPixel(bitmap.width - 13, 12),
                    bitmap.getPixel(12, bitmap.height - 13),
                    bitmap.getPixel(bitmap.width - 13, bitmap.height - 13),
                ).forEach { color ->
                    assertTrue(Color.red(color) > 205)
                    assertTrue(Color.green(color) > 205)
                    assertTrue(Color.blue(color) > 205)
                }
            } finally {
                bitmap.recycle()
            }
            assertEquals(
                ScannerPdfResult.Success,
                runBlocking { scanner.createSinglePagePdf(capture, Uri.fromFile(output), crop) },
            )
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    val rendered = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                    try {
                        renderer.openPage(0).use { page ->
                            page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                        val leftCenter = blueBandCenter(rendered, 100)
                        val rightCenter = blueBandCenter(rendered, rendered.width - 101)
                        assertTrue(kotlin.math.abs(leftCenter - rightCenter) <= 2)
                    } finally {
                        rendered.recycle()
                    }
                }
            }
        } finally {
            capture.delete()
            output.delete()
            corrected?.delete()
        }
    }

    private fun writeJpeg(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(235, 225, 205))
            file.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeTrapezoidJpeg(file: File) {
        val bitmap = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(32, 42, 50))
            val document = Path().apply {
                moveTo(200f, 80f)
                lineTo(800f, 144f)
                lineTo(900f, 720f)
                lineTo(100f, 656f)
                close()
            }
            canvas.drawPath(document, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            val printedLine = Path().apply {
                moveTo(165f, 282f)
                lineTo(835f, 346f)
                lineTo(843f, 392f)
                lineTo(157f, 328f)
                close()
            }
            canvas.drawPath(printedLine, Paint().apply { color = Color.rgb(40, 80, 160) })
            file.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun blueBandCenter(bitmap: Bitmap, x: Int): Int {
        val matchingRows = (0 until bitmap.height).filter { y ->
            val color = bitmap.getPixel(x, y)
            Color.blue(color) > 120 && Color.blue(color) > Color.red(color) * 2
        }
        assertTrue(matchingRows.isNotEmpty())
        return matchingRows.average().roundToInt()
    }
}
