package com.rameshta.quietpdf

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.DuplicatePagesEngine
import com.rameshta.quietpdf.pdf.DuplicatePagesResult
import com.rameshta.quietpdf.pdf.PdfSearchEngine
import com.rameshta.quietpdf.pdf.PdfSearchResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuplicatePagesEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun duplicate_preservesPageObjectsAndPlacesCopiesAfterOriginals() {
        val source = File(context.cacheDir, "duplicate-source.pdf")
        val output = File(context.cacheDir, "duplicate-output.pdf")
        try {
            writePdf(source)
            val result = runBlocking {
                DuplicatePagesEngine(context).duplicate(
                    Uri.fromFile(source),
                    Uri.fromFile(output),
                    selectedPageIndices = intArrayOf(0, 2),
                    expectedSourcePageCount = 3,
                )
            }

            assertEquals(DuplicatePagesResult.Success(pageCount = 5, duplicatedPageCount = 2), result)
            assertPageSizes(output, listOf(300 to 400, 300 to 400, 310 to 420, 320 to 440, 320 to 440))
            assertPageSizes(source, listOf(300 to 400, 310 to 420, 320 to 440))
            val searchEngine = PdfSearchEngine(context)
            try {
                val firstMatches = runBlocking { searchEngine.search(Uri.fromFile(output), 5, "FIRST") }
                assertTrue(firstMatches is PdfSearchResult.Matches)
                assertEquals(listOf(0, 1), (firstMatches as PdfSearchResult.Matches).matches.map { it.pageIndex })
                val thirdMatches = runBlocking { searchEngine.search(Uri.fromFile(output), 5, "THIRD") }
                assertTrue(thirdMatches is PdfSearchResult.Matches)
                assertEquals(listOf(3, 4), (thirdMatches as PdfSearchResult.Matches).matches.map { it.pageIndex })
            } finally {
                searchEngine.close()
            }
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun duplicate_rejectsInvalidOrChangedSelectionAndNeverOverwritesSource() {
        val source = File(context.cacheDir, "duplicate-protected-source.pdf")
        val output = File(context.cacheDir, "duplicate-invalid-output.pdf")
        try {
            writePdf(source)
            output.writeText("destination placeholder")
            val invalid = runBlocking {
                DuplicatePagesEngine(context).duplicate(
                    Uri.fromFile(source), Uri.fromFile(output), intArrayOf(1, 1), 3,
                )
            }
            assertEquals(DuplicatePagesResult.InvalidSelection, invalid)
            assertFalse(output.exists())

            val changed = runBlocking {
                DuplicatePagesEngine(context).duplicate(
                    Uri.fromFile(source), Uri.fromFile(output), intArrayOf(0), 4,
                )
            }
            assertEquals(DuplicatePagesResult.InvalidDocument, changed)
            assertFalse(output.exists())

            val protected = runBlocking {
                DuplicatePagesEngine(context).duplicate(
                    Uri.fromFile(source), Uri.fromFile(source), intArrayOf(0), 3,
                )
            }
            assertEquals(DuplicatePagesResult.InvalidSelection, protected)
            assertPageSizes(source, listOf(300 to 400, 310 to 420, 320 to 440))
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun writePdf(file: File) {
        val fixtures = listOf(
            Triple(300, 400, "FIRST"),
            Triple(310, 420, "SECOND"),
            Triple(320, 440, "THIRD"),
        )
        val document = PdfDocument()
        try {
            fixtures.forEachIndexed { index, (width, height, label) ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                page.canvas.drawText(label, 24f, 72f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 24f
                })
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun assertPageSizes(file: File, expected: List<Pair<Int, Int>>) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(expected.size, renderer.pageCount)
                expected.forEachIndexed { index, size ->
                    renderer.openPage(index).use { page ->
                        assertEquals(size.first, page.width)
                        assertEquals(size.second, page.height)
                    }
                }
            }
        }
    }
}
