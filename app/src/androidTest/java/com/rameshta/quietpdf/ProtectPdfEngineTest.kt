package com.rameshta.quietpdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ProtectPdfAnalysisResult
import com.rameshta.quietpdf.pdf.ProtectPdfEngine
import com.rameshta.quietpdf.pdf.ProtectPdfResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectPdfEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun protect_usesAes256_requiresPassword_andPreservesRenderablePagesAndSource() {
        val source = File(context.cacheDir, "protect-source.pdf")
        val output = File(context.cacheDir, "protect-output.pdf")
        val password = "correct horse".toCharArray()
        try {
            writeTwoPagePdf(source)
            val originalBytes = source.readBytes()
            val engine = ProtectPdfEngine(context)
            val analysis = runBlocking { engine.analyze(Uri.fromFile(source)) }
            assertEquals(ProtectPdfAnalysisResult.Ready(2), analysis)

            val result = runBlocking {
                engine.protect(Uri.fromFile(source), Uri.fromFile(output), password, 2)
            }

            assertEquals(ProtectPdfResult.Success(2), result)
            assertTrue(password.all { it == '\u0000' })
            assertEquals(originalBytes.asList(), source.readBytes().asList())
            assertPasswordRequired(output)
            assertWrongPasswordRejected(output)
            PDDocument.load(output, "correct horse").use { document ->
                assertTrue(document.isEncrypted)
                assertEquals(256, document.encryption.length)
                assertEquals(2, document.numberOfPages)
                val rendered = PDFRenderer(document).renderImage(0, 1f)
                assertTrue(rendered.width > 0)
                assertTrue(rendered.height > 0)
                assertFalse(rendered.getPixel(rendered.width / 2, rendered.height / 2) == Color.TRANSPARENT)
                context.getExternalFilesDir(null)?.let { artifactDirectory ->
                    File(artifactDirectory, "protect-pdf-render.png").outputStream().use { stream ->
                        assertTrue(rendered.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    }
                    output.copyTo(File(artifactDirectory, "protect-pdf-output.pdf"), overwrite = true)
                }
                rendered.recycle()
            }
            PDDocument.load(source).use { assertFalse(it.isEncrypted) }
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun analyze_rejectsAnAlreadyProtectedPdf() {
        val source = File(context.cacheDir, "protect-again-source.pdf")
        val protected = File(context.cacheDir, "protect-again-output.pdf")
        try {
            writeTwoPagePdf(source)
            val engine = ProtectPdfEngine(context)
            val result = runBlocking {
                engine.protect(Uri.fromFile(source), Uri.fromFile(protected), "secret!".toCharArray(), 2)
            }
            assertTrue(result is ProtectPdfResult.Success)
            assertEquals(
                ProtectPdfAnalysisResult.AlreadyProtected,
                runBlocking { engine.analyze(Uri.fromFile(protected)) },
            )
        } finally {
            source.delete()
            protected.delete()
        }
    }

    private fun assertPasswordRequired(file: File) {
        try {
            PDDocument.load(file).close()
            fail("Protected PDF opened without a password")
        } catch (_: InvalidPasswordException) {
            // Expected.
        }
    }

    private fun assertWrongPasswordRejected(file: File) {
        try {
            PDDocument.load(file, "wrong password").close()
            fail("Protected PDF opened with the wrong password")
        } catch (_: InvalidPasswordException) {
            // Expected.
        }
    }

    private fun writeTwoPagePdf(file: File) {
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(500, 700, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(
                    "QuietPDF protected page ${index + 1}",
                    48f,
                    120f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f },
                )
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
