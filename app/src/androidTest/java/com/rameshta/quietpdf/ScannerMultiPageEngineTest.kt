package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ScannerCaptureEngine
import com.rameshta.quietpdf.pdf.ScannerCropSelection
import com.rameshta.quietpdf.pdf.ScannerEnhancementSettings
import com.rameshta.quietpdf.pdf.ScannerPdfPage
import com.rameshta.quietpdf.pdf.ScannerPdfResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ScannerMultiPageEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pagesAreExportedInRequestedOrderAndValidated() {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val inputs = colors.mapIndexed { index, color ->
            File(context.cacheDir, "scanner-multi-$index.png").also { writePage(it, color) }
        }
        val output = File(context.cacheDir, "scanner-multi-output.pdf")
        try {
            val order = listOf(2, 0, 1)
            val result = runBlocking {
                ScannerCaptureEngine(context).createMultiPagePdf(
                    order.map { index ->
                        ScannerPdfPage(
                            inputs[index],
                            ScannerCropSelection.fullImage(),
                            ScannerEnhancementSettings(shadowReduction = false),
                        )
                    },
                    Uri.fromFile(output),
                )
            }
            assertEquals(ScannerPdfResult.Success, result)
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(3, renderer.pageCount)
                    order.forEachIndexed { pageIndex, sourceIndex ->
                        val rendered = Bitmap.createBitmap(120, 170, Bitmap.Config.ARGB_8888)
                        try {
                            renderer.openPage(pageIndex).use { page ->
                                page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                            val actual = rendered.getPixel(rendered.width / 2, rendered.height / 2)
                            val expected = colors[sourceIndex]
                            assertTrue(abs(Color.red(actual) - Color.red(expected)) <= 30)
                            assertTrue(abs(Color.green(actual) - Color.green(expected)) <= 30)
                            assertTrue(abs(Color.blue(actual) - Color.blue(expected)) <= 30)
                        } finally {
                            rendered.recycle()
                        }
                    }
                }
            }
        } finally {
            inputs.forEach(File::delete)
            output.delete()
        }
    }

    private fun writePage(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { stream ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
