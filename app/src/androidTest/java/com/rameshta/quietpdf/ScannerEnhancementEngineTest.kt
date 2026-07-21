package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ScannerCaptureEngine
import com.rameshta.quietpdf.pdf.ScannerColorMode
import com.rameshta.quietpdf.pdf.ScannerEnhancementEngine
import com.rameshta.quietpdf.pdf.ScannerEnhancementFileResult
import com.rameshta.quietpdf.pdf.ScannerEnhancementSettings
import com.rameshta.quietpdf.pdf.ScannerPdfResult
import com.rameshta.quietpdf.pdf.ScannerCropSelection
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ScannerEnhancementEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun grayscaleAndBlackWhiteModesProduceExpectedPixels() {
        val input = File(context.cacheDir, "scanner-enhancement-input.png")
        val outputs = mutableListOf<File>()
        try {
            writeShadedDocument(input)
            val engine = ScannerEnhancementEngine(context.cacheDir)
            val grayscale = requireReady(
                runBlocking {
                    engine.enhanceFile(
                        input,
                        ScannerEnhancementSettings(
                            mode = ScannerColorMode.Grayscale,
                            shadowReduction = false,
                        ),
                    )
                },
            ).also(outputs::add)
            BitmapFactory.decodeFile(grayscale.absolutePath).useBitmap { bitmap ->
                val color = bitmap.getPixel(bitmap.width / 3, bitmap.height / 3)
                assertTrue(abs(Color.red(color) - Color.green(color)) <= 3)
                assertTrue(abs(Color.green(color) - Color.blue(color)) <= 3)
            }

            val blackAndWhite = requireReady(
                runBlocking {
                    engine.enhanceFile(
                        input,
                        ScannerEnhancementSettings(
                            mode = ScannerColorMode.BlackAndWhite,
                            contrast = 1.2f,
                            shadowReduction = true,
                        ),
                    )
                },
            ).also(outputs::add)
            BitmapFactory.decodeFile(blackAndWhite.absolutePath).useBitmap { bitmap ->
                var black = 0
                var white = 0
                for (y in 0 until bitmap.height step 8) {
                    for (x in 0 until bitmap.width step 8) {
                        when (Color.red(bitmap.getPixel(x, y))) {
                            0 -> black++
                            255 -> white++
                            else -> throw AssertionError("Black-and-white output contained an intermediate tone")
                        }
                    }
                }
                assertTrue(black > 0)
                assertTrue(white > black)
            }
        } finally {
            input.delete()
            outputs.forEach(File::delete)
        }
    }

    @Test
    fun shadowReductionEvensUnevenPageLighting() {
        val input = File(context.cacheDir, "scanner-shadow-input.png")
        var output: File? = null
        try {
            writeShadedDocument(input)
            val original = BitmapFactory.decodeFile(input.absolutePath)
            val beforeDifference = original.useBitmap { bitmap ->
                abs(regionLightness(bitmap, 30, 60) - regionLightness(bitmap, 390, 420))
            }
            output = requireReady(
                runBlocking {
                    ScannerEnhancementEngine(context.cacheDir).enhanceFile(
                        input,
                        ScannerEnhancementSettings(shadowReduction = true),
                    )
                },
            )
            val after = BitmapFactory.decodeFile(output.absolutePath)
            val afterDifference = after.useBitmap { bitmap ->
                abs(regionLightness(bitmap, 30, 60) - regionLightness(bitmap, 390, 420))
            }
            assertTrue(afterDifference < beforeDifference * 0.6)
        } finally {
            input.delete()
            output?.delete()
        }
    }

    @Test
    fun selectedEnhancementIsAppliedToValidatedPdf() {
        val input = File(context.cacheDir, "scanner-enhancement-pdf-input.png")
        val output = File(context.cacheDir, "scanner-enhancement-output.pdf")
        try {
            writeShadedDocument(input)
            val result = runBlocking {
                ScannerCaptureEngine(context).createSinglePagePdf(
                    input,
                    Uri.fromFile(output),
                    ScannerCropSelection.fullImage(),
                    ScannerEnhancementSettings(
                        mode = ScannerColorMode.Grayscale,
                        brightness = 0.1f,
                        contrast = 1.2f,
                        shadowReduction = true,
                    ),
                )
            }
            assertEquals(ScannerPdfResult.Success, result)
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    val rendered = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888)
                    try {
                        renderer.openPage(0).use { page ->
                            page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                        for (y in 60 until 360 step 30) {
                            for (x in 20 until 280 step 30) {
                                val color = rendered.getPixel(x, y)
                                assertTrue(abs(Color.red(color) - Color.green(color)) <= 4)
                                assertTrue(abs(Color.green(color) - Color.blue(color)) <= 4)
                            }
                        }
                    } finally {
                        rendered.recycle()
                    }
                }
            }
        } finally {
            input.delete()
            output.delete()
        }
    }

    private fun requireReady(result: ScannerEnhancementFileResult): File {
        assertTrue(result is ScannerEnhancementFileResult.Ready)
        return (result as ScannerEnhancementFileResult.Ready).file
    }

    private fun writeShadedDocument(file: File) {
        val bitmap = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint()
            for (x in 0 until bitmap.width) {
                val light = 125 + (110f * x / (bitmap.width - 1)).toInt()
                paint.color = Color.rgb(light, (light + 5).coerceAtMost(255), (light + 10).coerceAtMost(255))
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), bitmap.height.toFloat(), paint)
            }
            paint.color = Color.rgb(25, 35, 50)
            canvas.drawRect(60f, 90f, 420f, 112f, paint)
            canvas.drawRect(60f, 150f, 350f, 172f, paint)
            file.outputStream().use { stream ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun regionLightness(bitmap: Bitmap, startX: Int, endX: Int): Float {
        var sum = 0L
        var count = 0
        for (y in 20 until 70 step 4) {
            for (x in startX until endX step 3) {
                sum += Color.red(bitmap.getPixel(x, y))
                count++
            }
        }
        return sum.toFloat() / count
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }
}
