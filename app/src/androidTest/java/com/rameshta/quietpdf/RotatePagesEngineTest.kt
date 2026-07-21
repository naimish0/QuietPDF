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
import com.rameshta.quietpdf.pdf.PageRotation
import com.rameshta.quietpdf.pdf.PdfSearchEngine
import com.rameshta.quietpdf.pdf.PdfSearchResult
import com.rameshta.quietpdf.pdf.RotatePagesEngine
import com.rameshta.quietpdf.pdf.RotatePagesResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RotatePagesEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rotate_preservesPageObjectsAndRotatesOnlySelectedPages() {
        val source = File(context.cacheDir, "rotate-source.pdf")
        val output = File(context.cacheDir, "rotate-output.pdf")
        try {
            writePdf(source)
            val result = runBlocking {
                RotatePagesEngine(context).rotate(
                    Uri.fromFile(source),
                    Uri.fromFile(output),
                    selectedPageIndices = intArrayOf(1),
                    rotation = PageRotation.Clockwise90,
                    expectedSourcePageCount = 3,
                )
            }

            assertEquals(RotatePagesResult.Success(pageCount = 3, rotatedPageCount = 1), result)
            assertPageSizes(output, listOf(300 to 400, 420 to 310, 320 to 440))
            assertPageSizes(source, listOf(300 to 400, 310 to 420, 320 to 440))
            val searchEngine = PdfSearchEngine(context)
            try {
                val match = runBlocking { searchEngine.search(Uri.fromFile(output), 3, "SECOND") }
                assertTrue(match is PdfSearchResult.Matches)
                assertEquals(1, (match as PdfSearchResult.Matches).matches.first().pageIndex)
            } finally {
                searchEngine.close()
            }
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun rotate_composesWithExistingPageRotation() {
        val source = File(context.cacheDir, "rotate-compose-source.pdf")
        val once = File(context.cacheDir, "rotate-compose-once.pdf")
        val twice = File(context.cacheDir, "rotate-compose-twice.pdf")
        try {
            writePdf(source)
            val engine = RotatePagesEngine(context)
            val first = runBlocking {
                engine.rotate(Uri.fromFile(source), Uri.fromFile(once), intArrayOf(0), PageRotation.Clockwise90, 3)
            }
            val second = runBlocking {
                engine.rotate(Uri.fromFile(once), Uri.fromFile(twice), intArrayOf(0), PageRotation.Clockwise90, 3)
            }
            assertTrue(first is RotatePagesResult.Success)
            assertTrue(second is RotatePagesResult.Success)
            assertPageSizes(once, listOf(400 to 300, 310 to 420, 320 to 440))
            assertPageSizes(twice, listOf(300 to 400, 310 to 420, 320 to 440))
        } finally {
            source.delete()
            once.delete()
            twice.delete()
        }
    }

    @Test
    fun rotate_rejectsInvalidOrChangedSelectionAndNeverOverwritesSource() {
        val source = File(context.cacheDir, "rotate-protected-source.pdf")
        val output = File(context.cacheDir, "rotate-invalid-output.pdf")
        try {
            writePdf(source)
            output.writeText("destination placeholder")
            val invalid = runBlocking {
                RotatePagesEngine(context).rotate(
                    Uri.fromFile(source), Uri.fromFile(output), intArrayOf(1, 1),
                    PageRotation.HalfTurn, 3,
                )
            }
            assertEquals(RotatePagesResult.InvalidSelection, invalid)
            assertFalse(output.exists())

            val changed = runBlocking {
                RotatePagesEngine(context).rotate(
                    Uri.fromFile(source), Uri.fromFile(output), intArrayOf(0),
                    PageRotation.HalfTurn, 4,
                )
            }
            assertEquals(RotatePagesResult.InvalidDocument, changed)
            assertFalse(output.exists())

            val protected = runBlocking {
                RotatePagesEngine(context).rotate(
                    Uri.fromFile(source), Uri.fromFile(source), intArrayOf(0),
                    PageRotation.Clockwise90, 3,
                )
            }
            assertEquals(RotatePagesResult.InvalidSelection, protected)
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
                page.canvas.drawColor(listOf(Color.RED, Color.GREEN, Color.BLUE)[index])
                page.canvas.drawText(label, 24f, 72f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    private fun assertPageSizes(file: File, expected: List<Pair<Int, Int>>) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(expected.size, renderer.pageCount)
                expected.forEachIndexed { index, size ->
                    renderer.openPage(index).use { page ->
                        assertEquals(size.first, page.width)
                        assertEquals(size.second, page.height)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }
}
