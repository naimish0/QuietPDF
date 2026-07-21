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
import com.rameshta.quietpdf.pdf.DeletePagesEngine
import com.rameshta.quietpdf.pdf.DeletePagesResult
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
class DeletePagesEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deletePages_preservesRemainingPageObjectsAndSourceDocument() {
        val source = File(context.cacheDir, "delete-source.pdf")
        val output = File(context.cacheDir, "delete-output.pdf")
        try {
            writePdf(
                source,
                listOf(
                    PageFixture(300, 400, Color.RED, "KEEP ALPHA"),
                    PageFixture(310, 410, Color.GREEN, "REMOVE"),
                    PageFixture(320, 420, Color.BLUE, "KEEP BETA"),
                    PageFixture(330, 430, Color.YELLOW, "REMOVE"),
                    PageFixture(340, 440, Color.MAGENTA, "KEEP GAMMA"),
                ),
            )

            val result = runBlocking {
                DeletePagesEngine(context).deletePages(
                    sourceUri = Uri.fromFile(source),
                    outputUri = Uri.fromFile(output),
                    sourcePageCount = 5,
                    deletedPageIndices = intArrayOf(1, 3),
                )
            }

            assertEquals(DeletePagesResult.Success(pageCount = 3), result)
            assertPdf(
                output,
                listOf(300 to Color.RED, 320 to Color.BLUE, 340 to Color.MAGENTA),
            )
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { assertEquals(5, it.pageCount) }
            }
            val searchEngine = PdfSearchEngine(context)
            try {
                val searchResult = runBlocking {
                    searchEngine.search(Uri.fromFile(output), pageCount = 3, query = "KEEP")
                }
                assertTrue(searchResult is PdfSearchResult.Matches)
                assertEquals(
                    listOf(0, 1, 2),
                    (searchResult as PdfSearchResult.Matches).matches
                        .map { it.pageIndex }
                        .distinct(),
                )
            } finally {
                searchEngine.close()
            }
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun deletePages_rejectsDeletingEveryPageAndCleansOutput() {
        val source = File(context.cacheDir, "delete-all-source.pdf")
        val output = File(context.cacheDir, "delete-all-output.pdf")
        try {
            writePdf(
                source,
                listOf(
                    PageFixture(300, 400, Color.RED, "ONE"),
                    PageFixture(310, 410, Color.BLUE, "TWO"),
                ),
            )
            output.writeText("empty destination placeholder")
            val result = runBlocking {
                DeletePagesEngine(context).deletePages(
                    Uri.fromFile(source),
                    Uri.fromFile(output),
                    sourcePageCount = 2,
                    deletedPageIndices = intArrayOf(0, 1),
                )
            }
            assertEquals(DeletePagesResult.InvalidSelection, result)
            assertFalse(output.exists())
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun deletePages_rejectsChangedSourceWithoutPublishingOutput() {
        val source = File(context.cacheDir, "delete-changed-source.pdf")
        val output = File(context.cacheDir, "delete-changed-output.pdf")
        try {
            writePdf(
                source,
                listOf(
                    PageFixture(300, 400, Color.RED, "ONE"),
                    PageFixture(310, 410, Color.BLUE, "TWO"),
                    PageFixture(320, 420, Color.GREEN, "THREE"),
                ),
            )
            val result = runBlocking {
                DeletePagesEngine(context).deletePages(
                    Uri.fromFile(source),
                    Uri.fromFile(output),
                    sourcePageCount = 4,
                    deletedPageIndices = intArrayOf(1),
                )
            }
            assertEquals(DeletePagesResult.InvalidDocument, result)
            assertFalse(output.exists())
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun deletePages_neverOverwritesOrDeletesSourceDocument() {
        val source = File(context.cacheDir, "delete-protected-source.pdf")
        try {
            writePdf(
                source,
                listOf(
                    PageFixture(300, 400, Color.RED, "ONE"),
                    PageFixture(310, 410, Color.BLUE, "TWO"),
                ),
            )
            val result = runBlocking {
                DeletePagesEngine(context).deletePages(
                    Uri.fromFile(source),
                    Uri.fromFile(source),
                    sourcePageCount = 2,
                    deletedPageIndices = intArrayOf(0),
                )
            }
            assertEquals(DeletePagesResult.InvalidSelection, result)
            assertTrue(source.exists())
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { assertEquals(2, it.pageCount) }
            }
        } finally {
            source.delete()
        }
    }

    private fun writePdf(file: File, pages: List<PageFixture>) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, fixture ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(fixture.width, fixture.height, index + 1).create(),
                )
                page.canvas.drawColor(fixture.color)
                page.canvas.drawText(
                    fixture.label,
                    24f,
                    72f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 24f
                    },
                )
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
                        val bitmap = Bitmap.createBitmap(
                            page.width,
                            page.height,
                            Bitmap.Config.ARGB_8888,
                        )
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

    private data class PageFixture(
        val width: Int,
        val height: Int,
        val color: Int,
        val label: String,
    )
}
