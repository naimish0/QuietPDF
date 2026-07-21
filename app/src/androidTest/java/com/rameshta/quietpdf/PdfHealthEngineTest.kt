package com.rameshta.quietpdf

import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.PdfHealthEngine
import com.rameshta.quietpdf.pdf.PdfHealthResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfHealthEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun inspect_verifiesEveryPageAndReportsLocalProperties() {
        val file = File(context.cacheDir, "health-fixture.pdf")
        try {
            val document = PdfDocument()
            try {
                repeat(2) { index ->
                    val page = document.startPage(
                        PdfDocument.PageInfo.Builder(200 + index, 300, index + 1).create(),
                    )
                    document.finishPage(page)
                }
                file.outputStream().use(document::writeTo)
            } finally {
                document.close()
            }

            val result = runBlocking {
                PdfHealthEngine(context).inspect(Uri.fromFile(file), expectedPageCount = 2)
            }

            assertTrue(result is PdfHealthResult.Healthy)
            val report = (result as PdfHealthResult.Healthy).report
            assertEquals(2, report.pageCount)
            assertNotNull(report.fileSizeBytes)
            assertTrue(report.fileSizeBytes!! > 0)
            assertFalse(report.hasSearchableText)
            assertFalse(report.hasTableOfContents)
        } finally {
            file.delete()
        }
    }
}
