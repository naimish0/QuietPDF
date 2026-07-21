package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ProtectPdfEngine
import com.rameshta.quietpdf.pdf.SignPdfAnalysis
import com.rameshta.quietpdf.pdf.SignPdfAnalysisResult
import com.rameshta.quietpdf.pdf.SignPdfEngine
import com.rameshta.quietpdf.pdf.SignPdfPreviewResult
import com.rameshta.quietpdf.pdf.SignPdfResult
import com.rameshta.quietpdf.pdf.VisibleSignatureSettings
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignPdfEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sign_addsVisibleSignatureToRotatedPage_preservesSourceAndGeometry() {
        val source = File(context.cacheDir, "sign-source.pdf")
        val output = File(context.cacheDir, "sign-output.pdf")
        val signature = signatureBitmap()
        val settings = VisibleSignatureSettings(pageIndex = 2, centerX = 0.72f, centerY = 0.18f, widthFraction = 0.32f)
        try {
            writeThreePagePdf(source)
            val sourceBytes = source.readBytes()
            val before = (0..2).map { renderPage(source, it) }
            val engine = SignPdfEngine(context)
            assertEquals(
                SignPdfAnalysisResult.Ready(SignPdfAnalysis(3)),
                runBlocking { engine.analyze(Uri.fromFile(source)) },
            )
            val preview = runBlocking { engine.preview(Uri.fromFile(source), signature, settings, 640) }
            assertTrue(preview is SignPdfPreviewResult.Ready)
            assertEquals(
                SignPdfResult.Success(3),
                runBlocking { engine.sign(Uri.fromFile(source), Uri.fromFile(output), signature, settings, 3) },
            )
            assertEquals(sourceBytes.asList(), source.readBytes().asList())
            PDDocument.load(output).use { document ->
                assertEquals(3, document.numberOfPages)
                assertEquals(500f, document.getPage(0).cropBox.width)
                assertEquals(700f, document.getPage(0).cropBox.height)
                assertEquals(90, document.getPage(2).rotation)
            }
            val after = (0..2).map { renderPage(output, it) }
            assertTrue(bitmapsMatch(before[0], after[0]))
            assertTrue(bitmapsMatch(before[1], after[1]))
            assertFalse(bitmapsMatch(before[2], after[2]))
            val previewBitmap = (preview as SignPdfPreviewResult.Ready).bitmap
            context.getExternalFilesDir(null)?.let { artifacts ->
                File(artifacts, "visible-signature-render.png").outputStream().use {
                    assertTrue(previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                File(artifacts, "visible-signature-rotated-render.png").outputStream().use {
                    assertTrue(after[2].compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                output.copyTo(File(artifacts, "visible-signature-output.pdf"), overwrite = true)
            }
            before.forEach(Bitmap::recycle)
            after.forEach(Bitmap::recycle)
            previewBitmap.recycle()
        } finally {
            signature.recycle()
            source.delete()
            output.delete()
        }
    }

    @Test
    fun sign_rejectsBlankSignatureAndRemovesPlaceholderOutput() {
        val source = File(context.cacheDir, "sign-invalid-source.pdf")
        val output = File(context.cacheDir, "sign-invalid-output.pdf")
        val blank = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888)
        try {
            writeThreePagePdf(source)
            output.writeText("placeholder")
            assertEquals(
                SignPdfResult.InvalidSignature,
                runBlocking {
                    SignPdfEngine(context).sign(
                        Uri.fromFile(source), Uri.fromFile(output), blank,
                        VisibleSignatureSettings(0), 3,
                    )
                },
            )
            assertFalse(output.exists())
        } finally {
            blank.recycle(); source.delete(); output.delete()
        }
    }

    @Test
    fun analyze_rejectsPasswordProtectedPdf() {
        val source = File(context.cacheDir, "sign-protection-source.pdf")
        val protected = File(context.cacheDir, "sign-protected.pdf")
        try {
            writeThreePagePdf(source)
            runBlocking {
                ProtectPdfEngine(context).protect(
                    Uri.fromFile(source), Uri.fromFile(protected), "secret!".toCharArray(), 3,
                )
            }
            assertEquals(
                SignPdfAnalysisResult.PasswordProtected,
                runBlocking { SignPdfEngine(context).analyze(Uri.fromFile(protected)) },
            )
        } finally {
            source.delete(); protected.delete()
        }
    }

    private fun signatureBitmap(): Bitmap = Bitmap.createBitmap(480, 160, Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(19, 47, 85)
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
        }
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawLine(20f, 120f, 130f, 35f, paint)
        canvas.drawLine(130f, 35f, 220f, 125f, paint)
        canvas.drawLine(210f, 115f, 450f, 60f, paint)
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
