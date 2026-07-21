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
import com.rameshta.quietpdf.pdf.SplitPageRange
import com.rameshta.quietpdf.pdf.SplitPdfEngine
import com.rameshta.quietpdf.pdf.SplitPdfOutput
import com.rameshta.quietpdf.pdf.SplitPdfResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitPdfEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun split_preservesPagesTextAndSourceOrderWithoutChangingSource() {
        val source = File(context.cacheDir, "split-source.pdf")
        val firstOutput = File(context.cacheDir, "split-output-1.pdf")
        val secondOutput = File(context.cacheDir, "split-output-2.pdf")
        val progress = mutableListOf<Pair<Int, Int>>()
        try {
            writePdf(
                source,
                listOf(
                    PageFixture(300, 400, Color.RED, "ALPHA ONE"),
                    PageFixture(310, 410, Color.GREEN, "ALPHA TWO"),
                    PageFixture(320, 420, Color.BLUE, "BETA THREE"),
                    PageFixture(330, 430, Color.YELLOW, "BETA FOUR"),
                    PageFixture(340, 440, Color.MAGENTA, "BETA FIVE"),
                ),
            )

            val result = runBlocking {
                SplitPdfEngine(context).splitToFiles(
                    sourceUri = Uri.fromFile(source),
                    outputFiles = listOf(firstOutput, secondOutput),
                    ranges = listOf(SplitPageRange(0, 1), SplitPageRange(2, 4)),
                    onProgress = { completed, total -> progress += completed to total },
                )
            }

            assertEquals(
                SplitPdfResult.Success(
                    listOf(
                        SplitPdfOutput(Uri.fromFile(firstOutput), 2),
                        SplitPdfOutput(Uri.fromFile(secondOutput), 3),
                    ),
                ),
                result,
            )
            assertEquals(listOf(1 to 2, 2 to 2), progress)
            assertPdf(
                firstOutput,
                listOf(300 to Color.RED, 310 to Color.GREEN),
            )
            assertPdf(
                secondOutput,
                listOf(320 to Color.BLUE, 330 to Color.YELLOW, 340 to Color.MAGENTA),
            )
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { assertEquals(5, it.pageCount) }
            }
            val searchEngine = PdfSearchEngine(context)
            try {
                val firstSearch = runBlocking {
                    searchEngine.search(Uri.fromFile(firstOutput), pageCount = 2, query = "ALPHA")
                }
                val secondSearch = runBlocking {
                    searchEngine.search(Uri.fromFile(secondOutput), pageCount = 3, query = "BETA")
                }
                assertTrue(firstSearch is PdfSearchResult.Matches)
                assertTrue(secondSearch is PdfSearchResult.Matches)
            } finally {
                searchEngine.close()
            }
        } finally {
            source.delete()
            firstOutput.delete()
            secondOutput.delete()
        }
    }

    @Test
    fun split_rejectsIncompletePlanWithoutPublishingOutput() {
        val source = File(context.cacheDir, "split-plan-source.pdf")
        val output = File(context.cacheDir, "split-plan-output.pdf")
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
                SplitPdfEngine(context).splitToFiles(
                    sourceUri = Uri.fromFile(source),
                    outputFiles = listOf(output),
                    ranges = listOf(SplitPageRange(0, 1)),
                )
            }

            assertEquals(SplitPdfResult.InvalidPlan, result)
            assertFalse(output.exists())
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun split_rejectsCorruptedInputWithoutPublishingOutputs() {
        val source = File(context.cacheDir, "split-corrupted.pdf")
        val firstOutput = File(context.cacheDir, "split-corrupted-output-1.pdf")
        val secondOutput = File(context.cacheDir, "split-corrupted-output-2.pdf")
        try {
            source.writeText("not a PDF")
            val result = runBlocking {
                SplitPdfEngine(context).splitToFiles(
                    sourceUri = Uri.fromFile(source),
                    outputFiles = listOf(firstOutput, secondOutput),
                    ranges = listOf(SplitPageRange(0, 0), SplitPageRange(1, 1)),
                )
            }
            assertEquals(SplitPdfResult.InvalidDocument, result)
            assertFalse(firstOutput.exists())
            assertFalse(secondOutput.exists())
        } finally {
            source.delete()
            firstOutput.delete()
            secondOutput.delete()
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
