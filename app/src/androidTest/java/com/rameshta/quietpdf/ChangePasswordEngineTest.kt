package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ChangePasswordEngine
import com.rameshta.quietpdf.pdf.ChangePasswordResult
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
class ChangePasswordEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun changePassword_replacesOldPasswordWithAes256_andPreservesSourceAndPages() {
        val plain = File(context.cacheDir, "change-password-plain.pdf")
        val source = File(context.cacheDir, "change-password-source.pdf")
        val output = File(context.cacheDir, "change-password-output.pdf")
        val currentPassword = "old secret".toCharArray()
        val newPassword = "new secret".toCharArray()
        try {
            writeTwoPagePdf(plain)
            assertEquals(
                ProtectPdfResult.Success(2),
                runBlocking {
                    ProtectPdfEngine(context).protect(
                        Uri.fromFile(plain), Uri.fromFile(source), "old secret".toCharArray(), 2,
                    )
                },
            )
            val sourceBytes = source.readBytes()

            val result = runBlocking {
                ChangePasswordEngine(context).change(
                    Uri.fromFile(source), Uri.fromFile(output), currentPassword, newPassword,
                )
            }

            assertEquals(ChangePasswordResult.Success(2), result)
            assertTrue(currentPassword.all { it == '\u0000' })
            assertTrue(newPassword.all { it == '\u0000' })
            assertEquals(sourceBytes.asList(), source.readBytes().asList())
            assertPasswordRejected(output, "")
            assertPasswordRejected(output, "old secret")
            PDDocument.load(output, "new secret").use { document ->
                assertTrue(document.isEncrypted)
                assertEquals(256, document.encryption.length)
                assertEquals(2, document.numberOfPages)
                val rendered = PDFRenderer(document).renderImage(0, 1f)
                assertEquals(500, rendered.width)
                assertEquals(700, rendered.height)
                context.getExternalFilesDir(null)?.let { artifactDirectory ->
                    File(artifactDirectory, "change-password-render.png").outputStream().use { stream ->
                        assertTrue(rendered.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    }
                    output.copyTo(File(artifactDirectory, "change-password-output.pdf"), overwrite = true)
                }
                rendered.recycle()
            }
        } finally {
            plain.delete()
            source.delete()
            output.delete()
        }
    }

    @Test
    fun changePassword_rejectsIncorrectCurrentPassword_andCleansOutput() {
        val plain = File(context.cacheDir, "change-wrong-plain.pdf")
        val source = File(context.cacheDir, "change-wrong-source.pdf")
        val output = File(context.cacheDir, "change-wrong-output.pdf")
        try {
            writeTwoPagePdf(plain)
            runBlocking {
                ProtectPdfEngine(context).protect(
                    Uri.fromFile(plain), Uri.fromFile(source), "correct!".toCharArray(), 2,
                )
            }
            output.writeText("placeholder")

            val result = runBlocking {
                ChangePasswordEngine(context).change(
                    Uri.fromFile(source), Uri.fromFile(output),
                    "wrong!".toCharArray(), "new pass!".toCharArray(),
                )
            }

            assertEquals(ChangePasswordResult.IncorrectCurrentPassword, result)
            assertFalse(output.exists())
        } finally {
            plain.delete()
            source.delete()
            output.delete()
        }
    }

    @Test
    fun changePassword_rejectsSameOrWeakNewPassword_beforeWriting() {
        val source = File(context.cacheDir, "change-invalid-source.pdf")
        val output = File(context.cacheDir, "change-invalid-output.pdf")
        try {
            writeTwoPagePdf(source)
            output.writeText("placeholder")
            assertEquals(
                ChangePasswordResult.InvalidNewPassword,
                runBlocking {
                    ChangePasswordEngine(context).change(
                        Uri.fromFile(source), Uri.fromFile(output),
                        "secret!".toCharArray(), "secret!".toCharArray(),
                    )
                },
            )
            assertFalse(output.exists())
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun assertPasswordRejected(file: File, password: String) {
        try {
            PDDocument.load(file, password).close()
            fail("Changed PDF accepted an obsolete password")
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
                    "QuietPDF changed password page ${index + 1}",
                    32f,
                    120f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 22f },
                )
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
