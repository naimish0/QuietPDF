package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.AnnotatePdfAnalysis
import com.rameshta.quietpdf.pdf.AnnotatePdfAnalysisResult
import com.rameshta.quietpdf.pdf.AnnotatePdfEngine
import com.rameshta.quietpdf.pdf.AnnotatePdfPreviewResult
import com.rameshta.quietpdf.pdf.AnnotatePdfResult
import com.rameshta.quietpdf.pdf.AnnotationPoint
import com.rameshta.quietpdf.pdf.PdfAnnotationItem
import com.rameshta.quietpdf.pdf.ProtectPdfEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotatePdfEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun annotate_addsStandardAnnotations_preservesSourceAndRotatedGeometry() {
        val source = File(context.cacheDir, "annotate-source.pdf")
        val output = File(context.cacheDir, "annotate-output.pdf")
        val annotations = listOf(
            PdfAnnotationItem.Highlight(0, centerX = 0.45f, centerY = 0.78f),
            PdfAnnotationItem.FreeText(1, "Review this section", centerX = 0.5f, centerY = 0.65f),
            PdfAnnotationItem.Ink(2, listOf(listOf(
                AnnotationPoint(0.15f, 0.2f), AnnotationPoint(0.35f, 0.35f),
                AnnotationPoint(0.55f, 0.22f), AnnotationPoint(0.8f, 0.4f),
            ))),
        )
        try {
            writeThreePagePdf(source)
            val sourceBytes = source.readBytes()
            val before = (0..2).map { renderPage(source, it) }
            val engine = AnnotatePdfEngine(context)
            assertEquals(
                AnnotatePdfAnalysisResult.Ready(AnnotatePdfAnalysis(3)),
                runBlocking { engine.analyze(Uri.fromFile(source)) },
            )
            val preview = runBlocking { engine.preview(Uri.fromFile(source), annotations, 2, 640) }
            assertTrue(preview is AnnotatePdfPreviewResult.Ready)
            assertEquals(
                AnnotatePdfResult.Success(3, 3),
                runBlocking { engine.annotate(Uri.fromFile(source), Uri.fromFile(output), annotations, 3) },
            )
            assertEquals(sourceBytes.asList(), source.readBytes().asList())
            PDDocument.load(output).use { document ->
                assertEquals(3, document.numberOfPages)
                assertEquals(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT, document.getPage(0).annotations.single().subtype)
                assertEquals(PDAnnotationMarkup.SUB_TYPE_FREETEXT, document.getPage(1).annotations.single().subtype)
                assertEquals(PDAnnotationMarkup.SUB_TYPE_INK, document.getPage(2).annotations.single().subtype)
                assertEquals(90, document.getPage(2).rotation)
                assertEquals(700f, document.getPage(2).cropBox.width)
                assertEquals(500f, document.getPage(2).cropBox.height)
            }
            val after = (0..2).map { renderPage(output, it) }
            assertFalse(bitmapsMatch(before[0], after[0]))
            assertFalse(bitmapsMatch(before[1], after[1]))
            assertFalse(bitmapsMatch(before[2], after[2]))
            val previewBitmap = (preview as AnnotatePdfPreviewResult.Ready).bitmap
            context.getExternalFilesDir(null)?.let { artifacts ->
                File(artifacts, "annotation-preview.png").outputStream().use {
                    assertTrue(previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                File(artifacts, "annotation-page-1.png").outputStream().use {
                    assertTrue(after[0].compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                File(artifacts, "annotation-page-2.png").outputStream().use {
                    assertTrue(after[1].compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                File(artifacts, "annotation-page-3-rotated.png").outputStream().use {
                    assertTrue(after[2].compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                output.copyTo(File(artifacts, "annotation-output.pdf"), overwrite = true)
            }
            before.forEach(Bitmap::recycle)
            after.forEach(Bitmap::recycle)
            previewBitmap.recycle()
        } finally {
            source.delete(); output.delete()
        }
    }

    @Test
    fun annotate_rejectsEmptyAnnotationsAndCleansPlaceholder() {
        val source = File(context.cacheDir, "annotate-invalid-source.pdf")
        val output = File(context.cacheDir, "annotate-invalid-output.pdf")
        try {
            writeThreePagePdf(source)
            output.writeText("placeholder")
            assertEquals(
                AnnotatePdfResult.InvalidAnnotations,
                runBlocking {
                    AnnotatePdfEngine(context).annotate(
                        Uri.fromFile(source), Uri.fromFile(output), emptyList(), 3,
                    )
                },
            )
            assertFalse(output.exists())
        } finally { source.delete(); output.delete() }
    }

    @Test
    fun analyze_rejectsPasswordProtectedPdf() {
        val source = File(context.cacheDir, "annotate-protect-source.pdf")
        val protected = File(context.cacheDir, "annotate-protected.pdf")
        try {
            writeThreePagePdf(source)
            runBlocking {
                ProtectPdfEngine(context).protect(
                    Uri.fromFile(source), Uri.fromFile(protected), "secret!".toCharArray(), 3,
                )
            }
            assertEquals(
                AnnotatePdfAnalysisResult.PasswordProtected,
                runBlocking { AnnotatePdfEngine(context).analyze(Uri.fromFile(protected)) },
            )
        } finally { source.delete(); protected.delete() }
    }

    private fun writeThreePagePdf(file: File) {
        val document = PdfDocument()
        try {
            listOf(500 to 700, 600 to 600, 700 to 500).forEachIndexed { index, size ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(size.first, size.second, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("Original page ${index + 1}", 40f, 100f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f })
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        PDDocument.load(file).use { document -> document.getPage(2).rotation = 90; document.save(file) }
    }

    private fun renderPage(file: File, page: Int): Bitmap =
        PDDocument.load(file).use { PDFRenderer(it).renderImage(page, 1f) }

    private fun bitmapsMatch(first: Bitmap, second: Bitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val a = IntArray(first.width * first.height)
        val b = IntArray(second.width * second.height)
        first.getPixels(a, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(b, 0, second.width, 0, 0, second.width, second.height)
        return a.contentEquals(b)
    }
}
