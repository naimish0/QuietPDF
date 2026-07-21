package com.rameshta.quietpdf

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.PdfSearchEngine
import com.rameshta.quietpdf.pdf.PdfSearchResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfSearchEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun search_returnsNormalizedBoundsForMatchingText() = runBlocking {
        val file = File(context.cacheDir, "searchable.pdf")
        try {
            createSearchablePdf(file)
            PdfSearchEngine(context) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }.use { engine ->
                val result = engine.search(Uri.parse("content://test/searchable"), 2, "QuietPDF")

                assertTrue(result is PdfSearchResult.Matches)
                val matches = (result as PdfSearchResult.Matches).matches
                assertEquals(1, matches.size)
                assertEquals(1, matches.single().pageIndex)
                assertTrue(matches.single().bounds.all { bounds ->
                    bounds.left in 0f..1f && bounds.top in 0f..1f &&
                        bounds.right in 0f..1f && bounds.bottom in 0f..1f
                })
            }
        } finally {
            file.delete()
        }
    }

    private fun createSearchablePdf(file: File) {
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(300, 400, index + 1).create(),
                )
                if (index == 1) {
                    page.canvas.drawText("Find QuietPDF here", 40f, 100f, Paint().apply {
                        textSize = 24f
                    })
                }
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
