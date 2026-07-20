package com.rameshta.quietpdf

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.PdfPageRenderer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfPageRendererTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun render_preservesPageAspectRatioAndOpaqueBackground() = runBlocking {
        val file = File(context.cacheDir, "page-renderer.pdf")
        try {
            createPdf(file, width = 200, height = 300)
            val renderer = PdfPageRenderer {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            val bitmap = renderer.render(Uri.EMPTY, pageIndex = 0, requestedWidth = 400)
            try {
                assertEquals(400, bitmap.width)
                assertEquals(600, bitmap.height)
                assertEquals(Color.WHITE, bitmap.getPixel(399, 599))
            } finally {
                bitmap.recycle()
            }
        } finally {
            file.delete()
        }
    }

    private fun createPdf(file: File, width: Int, height: Int) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
