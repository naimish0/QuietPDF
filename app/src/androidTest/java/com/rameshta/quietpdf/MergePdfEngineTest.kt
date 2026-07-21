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
import com.rameshta.quietpdf.pdf.MergePdfEngine
import com.rameshta.quietpdf.pdf.MergePdfResult
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
class MergePdfEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun merge_preservesEveryPageInSourceOrder() {
        val first = File(context.cacheDir, "merge-first.pdf")
        val second = File(context.cacheDir, "merge-second.pdf")
        val output = File(context.cacheDir, "merge-output.pdf")
        try {
            writePdf(first, listOf(PageFixture(300, 400, Color.RED, "FIRST")))
            writePdf(
                second,
                listOf(
                    PageFixture(410, 500, Color.GREEN, "SECOND A"),
                    PageFixture(420, 510, Color.BLUE, "SECOND B"),
                ),
            )

            val result = runBlocking {
                MergePdfEngine(context).merge(
                    sourceUris = listOf(Uri.fromFile(second), Uri.fromFile(first)),
                    outputUri = Uri.fromFile(output),
                )
            }

            assertEquals(MergePdfResult.Success(pageCount = 3), result)
            val descriptor = ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(3, renderer.pageCount)
                assertPage(renderer, 0, expectedWidth = 410, expectedColor = Color.GREEN)
                assertPage(renderer, 1, expectedWidth = 420, expectedColor = Color.BLUE)
                assertPage(renderer, 2, expectedWidth = 300, expectedColor = Color.RED)
            }
            val searchEngine = PdfSearchEngine(context)
            try {
                val searchResult = runBlocking {
                    searchEngine.search(Uri.fromFile(output), pageCount = 3, query = "SECOND")
                }
                assertTrue(searchResult is PdfSearchResult.Matches)
                assertEquals(
                    listOf(0, 1),
                    (searchResult as PdfSearchResult.Matches).matches.map { it.pageIndex }.distinct(),
                )
            } finally {
                searchEngine.close()
            }
        } finally {
            first.delete()
            second.delete()
            output.delete()
        }
    }

    @Test
    fun merge_rejectsCorruptedInputWithoutPublishingOutput() {
        val valid = File(context.cacheDir, "merge-valid.pdf")
        val corrupted = File(context.cacheDir, "merge-corrupted.pdf")
        val output = File(context.cacheDir, "merge-invalid-output.pdf")
        try {
            writePdf(valid, listOf(PageFixture(300, 400, Color.RED, "VALID")))
            corrupted.writeText("not a PDF")

            val result = runBlocking {
                MergePdfEngine(context).merge(
                    sourceUris = listOf(Uri.fromFile(valid), Uri.fromFile(corrupted)),
                    outputUri = Uri.fromFile(output),
                )
            }

            assertEquals(MergePdfResult.InvalidDocument(documentIndex = 1), result)
            assertFalse(output.exists())
        } finally {
            valid.delete()
            corrupted.delete()
            output.delete()
        }
    }

    @Test
    fun merge_neverOverwritesAnInputDocument() {
        val first = File(context.cacheDir, "merge-protected-first.pdf")
        val second = File(context.cacheDir, "merge-protected-second.pdf")
        try {
            writePdf(first, listOf(PageFixture(300, 400, Color.RED, "FIRST")))
            writePdf(second, listOf(PageFixture(400, 500, Color.BLUE, "SECOND")))

            val result = runBlocking {
                MergePdfEngine(context).merge(
                    sourceUris = listOf(Uri.fromFile(first), Uri.fromFile(second)),
                    outputUri = Uri.fromFile(first),
                )
            }

            assertEquals(MergePdfResult.Failed, result)
            val descriptor = ParcelFileDescriptor.open(first, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { assertEquals(1, it.pageCount) }
        } finally {
            first.delete()
            second.delete()
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

    private fun assertPage(
        renderer: PdfRenderer,
        pageIndex: Int,
        expectedWidth: Int,
        expectedColor: Int,
    ) {
        renderer.openPage(pageIndex).use { page ->
            assertEquals(expectedWidth, page.width)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            try {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val actual = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                assertTrue(kotlin.math.abs(Color.red(expectedColor) - Color.red(actual)) < 10)
                assertTrue(kotlin.math.abs(Color.green(expectedColor) - Color.green(actual)) < 10)
                assertTrue(kotlin.math.abs(Color.blue(expectedColor) - Color.blue(actual)) < 10)
            } finally {
                bitmap.recycle()
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
