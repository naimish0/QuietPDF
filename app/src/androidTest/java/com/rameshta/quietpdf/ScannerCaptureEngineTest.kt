package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ScannerCaptureEngine
import com.rameshta.quietpdf.pdf.ScannerPdfResult
import com.rameshta.quietpdf.pdf.ScannerPreviewResult
import java.io.File
import kotlinx.coroutines.runBlocking
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
}
