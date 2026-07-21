package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ProtectPdfEngine
import com.rameshta.quietpdf.pdf.ProtectPdfResult
import com.rameshta.quietpdf.pdf.RemovePasswordAnalysisResult
import com.rameshta.quietpdf.pdf.RemovePasswordEngine
import com.rameshta.quietpdf.pdf.RemovePasswordResult
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
class RemovePasswordEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun removePassword_createsValidatedRenderableCopy_andPreservesProtectedSource() {
        val plain = File(context.cacheDir, "remove-password-plain.pdf")
        val protected = File(context.cacheDir, "remove-password-protected.pdf")
        val output = File(context.cacheDir, "remove-password-output.pdf")
        try {
            writeTwoPagePdf(plain)
            val protection = runBlocking {
                ProtectPdfEngine(context).protect(
                    Uri.fromFile(plain), Uri.fromFile(protected), "secret!".toCharArray(), 2,
                )
            }
            assertEquals(ProtectPdfResult.Success(2), protection)
            val protectedBytes = protected.readBytes()
            val engine = RemovePasswordEngine(context)
            assertEquals(
                RemovePasswordAnalysisResult.Protected,
                runBlocking { engine.analyze(Uri.fromFile(protected)) },
            )
            val password = "secret!".toCharArray()

            val result = runBlocking {
                engine.remove(Uri.fromFile(protected), Uri.fromFile(output), password)
            }

            assertEquals(RemovePasswordResult.Success(2), result)
            assertTrue(password.all { it == '\u0000' })
            assertEquals(protectedBytes.asList(), protected.readBytes().asList())
            PDDocument.load(output).use { document ->
                assertFalse(document.isEncrypted)
                assertEquals(2, document.numberOfPages)
                val rendered = PDFRenderer(document).renderImage(0, 1f)
                assertEquals(500, rendered.width)
                assertEquals(700, rendered.height)
                context.getExternalFilesDir(null)?.let { artifactDirectory ->
                    File(artifactDirectory, "remove-password-render.png").outputStream().use { stream ->
                        assertTrue(rendered.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    }
                    output.copyTo(File(artifactDirectory, "remove-password-output.pdf"), overwrite = true)
                }
                rendered.recycle()
            }
        } finally {
            plain.delete()
            protected.delete()
            output.delete()
        }
    }

    @Test
    fun removePassword_rejectsWrongPassword_andCleansOutput() {
        val plain = File(context.cacheDir, "remove-wrong-plain.pdf")
        val protected = File(context.cacheDir, "remove-wrong-protected.pdf")
        val output = File(context.cacheDir, "remove-wrong-output.pdf")
        try {
            writeTwoPagePdf(plain)
            runBlocking {
                ProtectPdfEngine(context).protect(
                    Uri.fromFile(plain), Uri.fromFile(protected), "correct!".toCharArray(), 2,
                )
            }
            output.writeText("placeholder")

            val result = runBlocking {
                RemovePasswordEngine(context).remove(
                    Uri.fromFile(protected), Uri.fromFile(output), "wrong!".toCharArray(),
                )
            }

            assertEquals(RemovePasswordResult.IncorrectPassword, result)
            assertFalse(output.exists())
        } finally {
            plain.delete()
            protected.delete()
            output.delete()
        }
    }

    @Test
    fun analyze_rejectsPdfWithoutProtection() {
        val plain = File(context.cacheDir, "remove-unprotected.pdf")
        try {
            writeTwoPagePdf(plain)
            assertEquals(
                RemovePasswordAnalysisResult.NotProtected,
                runBlocking { RemovePasswordEngine(context).analyze(Uri.fromFile(plain)) },
            )
        } finally {
            plain.delete()
        }
    }

    private fun writeTwoPagePdf(file: File) {
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(500, 700, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(
                    "QuietPDF unlocked page ${index + 1}",
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
