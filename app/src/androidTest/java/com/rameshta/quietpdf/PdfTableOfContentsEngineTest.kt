package com.rameshta.quietpdf

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.PdfTableOfContentsEngine
import com.rameshta.quietpdf.pdf.PdfTableOfContentsResult
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfTableOfContentsEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun load_readsHierarchicalEmbeddedOutline() {
        val file = File(context.cacheDir, "outline-fixture.pdf")
        try {
            file.writeBytes(outlinePdf())

            val result = kotlinx.coroutines.runBlocking {
                PdfTableOfContentsEngine(context).load(Uri.fromFile(file), pageCount = 2)
            }

            assertTrue(result is PdfTableOfContentsResult.Entries)
            val entries = (result as PdfTableOfContentsResult.Entries).entries
            assertEquals(listOf("Chapter One", "Section A"), entries.map { it.title })
            assertEquals(listOf(0, 1), entries.map { it.pageIndex })
            assertEquals(listOf(0, 1), entries.map { it.depth })
        } finally {
            file.delete()
        }
    }

    private fun outlinePdf(): ByteArray {
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R /Outlines 5 0 R /PageMode /UseOutlines >>",
            "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] >>",
            "<< /Type /Outlines /First 6 0 R /Last 6 0 R /Count 2 >>",
            "<< /Title (Chapter One) /Parent 5 0 R /Dest [3 0 R /Fit] " +
                "/First 7 0 R /Last 7 0 R /Count 1 >>",
            "<< /Title (Section A) /Parent 6 0 R /Dest [4 0 R /Fit] >>",
        )
        val output = ByteArrayOutputStream()
        output.write("%PDF-1.4\n".toByteArray())
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { index, body ->
            offsets += output.size()
            output.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray())
        }
        val xrefOffset = output.size()
        output.write("xref\n0 ${objects.size + 1}\n".toByteArray())
        output.write("0000000000 65535 f \n".toByteArray())
        offsets.forEach { output.write("%010d 00000 n \n".format(it).toByteArray()) }
        output.write(
            ("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n" +
                "startxref\n$xrefOffset\n%%EOF\n").toByteArray(),
        )
        return output.toByteArray()
    }
}
