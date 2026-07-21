package com.rameshta.quietpdf

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.PdfShareIntentFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfShareIntentFactoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Suppress("DEPRECATION")
    @Test
    fun contentUriCreatesPdfSendIntentWithTemporaryReadGrantAndClipData() {
        val uri = Uri.parse("content://documents/report.pdf")
        val intent = PdfShareIntentFactory.create(context, uri)
        assertNotNull(intent)
        requireNotNull(intent)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(PdfShareIntentFactory.PDF_MIME_TYPE, intent.type)
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun fileAndNetworkUrisAreRejectedWithoutExposingPaths() {
        assertNull(PdfShareIntentFactory.create(context, Uri.parse("file:///private/report.pdf")))
        assertNull(PdfShareIntentFactory.create(context, Uri.parse("https://example.com/report.pdf")))
    }
}
