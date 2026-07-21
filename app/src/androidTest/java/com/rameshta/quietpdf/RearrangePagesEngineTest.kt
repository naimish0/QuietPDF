package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.PdfSearchEngine
import com.rameshta.quietpdf.pdf.PdfSearchResult
import com.rameshta.quietpdf.pdf.RearrangePagesEngine
import com.rameshta.quietpdf.pdf.RearrangePagesResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RearrangePagesEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rearrange_preservesPageObjectsInRequestedOrderAndSourceDocument() {
        val source = File(context.cacheDir, "rearrange-source.pdf")
        val output = File(context.cacheDir, "rearrange-output.pdf")
        try {
            writePdf(
                source,
                listOf(
                    PageFixture(300, 400, Color.RED, "FIRST"),
                    PageFixture(310, 410, Color.GREEN, "SECOND"),
                    PageFixture(320, 420, Color.BLUE, "THIRD"),
                    PageFixture(330, 430, Color.YELLOW, "FOURTH"),
                ),
            )

            val result = runBlocking {
                RearrangePagesEngine(context).rearrange(
                    Uri.fromFile(source),
                    Uri.fromFile(output),
                    pageOrder = intArrayOf(2, 0, 3, 1),
                    expectedSourcePageCount = 4,
                )
            }

            assertEquals(RearrangePagesResult.Success(4), result)
            assertPdf(output, listOf(320 to Color.BLUE, 300 to Color.RED, 330 to Color.YELLOW, 310 to Color.GREEN))
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { assertEquals(4, it.pageCount) }
            }
            val searchEngine = PdfSearchEngine(context)
            try {
                val matches = runBlocking { searchEngine.search(Uri.fromFile(output), 4, "THIRD") }
                assertTrue(matches is PdfSearchResult.Matches)
                assertEquals(0, (matches as PdfSearchResult.Matches).matches.first().pageIndex)
            } finally {
                searchEngine.close()
            }
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun rearrange_rejectsDuplicateOrMissingPagesAndCleansOutput() {
        val source = File(context.cacheDir, "rearrange-invalid-source.pdf")
        val output = File(context.cacheDir, "rearrange-invalid-output.pdf")
        try {
            writePdf(source, listOf(PageFixture(300, 400, Color.RED, "ONE"), PageFixture(310, 410, Color.BLUE, "TWO")))
            output.writeText("destination placeholder")
            val result = runBlocking {
                RearrangePagesEngine(context).rearrange(
                    Uri.fromFile(source),
                    Uri.fromFile(output),
                    intArrayOf(0, 0),
                    expectedSourcePageCount = 2,
                )
            }
            assertEquals(RearrangePagesResult.InvalidOrder, result)
            assertFalse(output.exists())
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun rearrange_rejectsChangedSourceAndNeverOverwritesSource() {
        val source = File(context.cacheDir, "rearrange-protected-source.pdf")
        val output = File(context.cacheDir, "rearrange-changed-output.pdf")
        try {
            writePdf(source, listOf(PageFixture(300, 400, Color.RED, "ONE"), PageFixture(310, 410, Color.BLUE, "TWO")))
            val changed = runBlocking {
                RearrangePagesEngine(context).rearrange(
                    Uri.fromFile(source), Uri.fromFile(output), intArrayOf(1, 0, 2), 3,
                )
            }
            assertEquals(RearrangePagesResult.InvalidDocument, changed)
            assertFalse(output.exists())

            val protected = runBlocking {
                RearrangePagesEngine(context).rearrange(
                    Uri.fromFile(source), Uri.fromFile(source), intArrayOf(1, 0), 2,
                )
            }
            assertEquals(RearrangePagesResult.InvalidOrder, protected)
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { assertEquals(2, it.pageCount) }
            }
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun writePdf(file: File, pages: List<PageFixture>) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, fixture ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(fixture.width, fixture.height, index + 1).create())
                page.canvas.drawColor(fixture.color)
                page.canvas.drawText(fixture.label, 24f, 72f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 24f
                })
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun assertPdf(file: File, expectedPages: List<Pair<Int, Int>>) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(expectedPages.size, renderer.pageCount)
                expectedPages.forEachIndexed { index, (width, color) ->
                    renderer.openPage(index).use { page ->
                        assertEquals(width, page.width)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val actual = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                            assertTrue(kotlin.math.abs(Color.red(color) - Color.red(actual)) < 10)
                            assertTrue(kotlin.math.abs(Color.green(color) - Color.green(actual)) < 10)
                            assertTrue(kotlin.math.abs(Color.blue(color) - Color.blue(actual)) < 10)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    private data class PageFixture(val width: Int, val height: Int, val color: Int, val label: String)
}
