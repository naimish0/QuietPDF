package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ImagesToPdfEngine
import com.rameshta.quietpdf.pdf.ImagesToPdfResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagesToPdfEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun create_writesOneAspectFittedPagePerImage() {
        val portrait = File(context.cacheDir, "portrait-source.png")
        val landscape = File(context.cacheDir, "landscape-source.png")
        val output = File(context.cacheDir, "images-output.pdf")
        try {
            writeImage(portrait, width = 100, height = 200, color = Color.RED)
            writeImage(landscape, width = 200, height = 100, color = Color.BLUE)

            val result = runBlocking {
                ImagesToPdfEngine(context.contentResolver, context.cacheDir).create(
                    imageUris = listOf(Uri.fromFile(portrait), Uri.fromFile(landscape)),
                    outputUri = Uri.fromFile(output),
                )
            }

            assertEquals(ImagesToPdfResult.Success(2), result)
            val descriptor = ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(2, renderer.pageCount)
                renderer.openPage(0).use {
                    assertTrue(it.height > it.width)
                    assertPageCenterColor(it, Color.RED)
                }
                renderer.openPage(1).use {
                    assertTrue(it.width > it.height)
                    assertPageCenterColor(it, Color.BLUE)
                }
            }
        } finally {
            portrait.delete()
            landscape.delete()
            output.delete()
        }
    }

    private fun assertPageCenterColor(page: PdfRenderer.Page, expected: Int) {
        val rendered = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        try {
            page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val actual = rendered.getPixel(rendered.width / 2, rendered.height / 2)
            assertTrue(kotlin.math.abs(Color.red(expected) - Color.red(actual)) < 10)
            assertTrue(kotlin.math.abs(Color.green(expected) - Color.green(actual)) < 10)
            assertTrue(kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) < 10)
        } finally {
            rendered.recycle()
        }
    }

    private fun writeImage(file: File, width: Int, height: Int, color: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }
}
