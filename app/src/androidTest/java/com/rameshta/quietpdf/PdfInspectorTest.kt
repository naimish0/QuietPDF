package com.rameshta.quietpdf

import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.PdfInspector
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfInspectorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pageCount_readsGeneratedPdf() {
        val file = File(context.cacheDir, "pdf-inspector-valid.pdf")
        try {
            val document = PdfDocument()
            try {
                repeat(2) { index ->
                    val page = document.startPage(
                        PdfDocument.PageInfo.Builder(200, 300, index + 1).create(),
                    )
                    document.finishPage(page)
                }
                file.outputStream().use(document::writeTo)
            } finally {
                document.close()
            }

            val count = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use(PdfInspector::pageCount)

            assertEquals(2, count)
        } finally {
            file.delete()
        }
    }

    @Test
    fun pageCount_rejectsCorruptedPdf() {
        val file = File(context.cacheDir, "pdf-inspector-corrupt.pdf")
        try {
            file.writeText("not a PDF")
            try {
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                ).use(PdfInspector::pageCount)
                fail("Expected a corrupt PDF to be rejected")
            } catch (_: IOException) {
                // Expected: PdfRenderer validates the file before it is accepted.
            }
        } finally {
            file.delete()
        }
    }
}
