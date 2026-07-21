package com.rameshta.quietpdf

import android.content.Context
import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.RecentPdfStore
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.PdfOpenViewModel
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentPdfStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = AtomicLong(1_000L)
    private val store by lazy { RecentPdfStore(context) { clock.getAndIncrement() } }

    @Before
    fun clearBefore() = clearPreferences()

    @After
    fun clearAfter() = clearPreferences()

    @Test
    fun recordKeepsNewestFirstDeduplicatesAndBoundsLocalMetadata() {
        repeat(RecentPdfStore.MAX_ENTRIES + 3) { index ->
            store.record(Uri.parse("content://documents/$index"), " Document $index.pdf ", index + 1)
        }
        val entries = store.load()
        assertEquals(RecentPdfStore.MAX_ENTRIES, entries.size)
        assertEquals("content://documents/12", entries.first().uri.toString())
        assertEquals("Document 12.pdf", entries.first().displayName)
        assertEquals(13, entries.first().pageCount)

        clock.set(9_000L)
        store.record(Uri.parse("content://documents/5"), "Renamed.pdf", 42)
        val updated = store.load()
        assertEquals("content://documents/5", updated.first().uri.toString())
        assertEquals("Renamed.pdf", updated.first().displayName)
        assertEquals(42, updated.first().pageCount)
        assertEquals(1, updated.count { it.uri.toString() == "content://documents/5" })
    }

    @Test
    fun invalidSchemesAndInvalidDocumentsAreNotPersisted() {
        store.record(Uri.parse("file:///private/document.pdf"), "Private.pdf", 1)
        store.record(Uri.parse("https://example.com/document.pdf"), "Remote.pdf", 1)
        store.record(Uri.parse("content://documents/empty"), "Empty.pdf", 0)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun removeAndClearOnlyChangeRecentMetadata() {
        val first = Uri.parse("content://documents/first")
        val second = Uri.parse("content://documents/second")
        store.record(first, "First.pdf", 1)
        store.record(second, "Second.pdf", 2)
        assertEquals(listOf(second), store.remove(first).map { it.uri })
        store.clear()
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun corruptStoredDataRecoversAsAnEmptyList() {
        context.getSharedPreferences(RecentPdfStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString("entries", "not-json").commit()
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun unavailableRecentDocumentIsRemovedAfterFailedOpen() {
        val missing = Uri.parse("content://com.rameshta.quietpdf.fileprovider/not-available.pdf")
        store.record(missing, "Missing.pdf", 2)
        val viewModel = PdfOpenViewModel(context.applicationContext as Application)
        assertEquals(listOf(missing), viewModel.recentPdfs.map { it.uri })
        viewModel.openRecentPdf(missing)
        val deadline = SystemClock.uptimeMillis() + 3_000L
        while (viewModel.state is PdfOpenState.Opening && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(20L)
        }
        assertTrue(viewModel.state is PdfOpenState.Failed)
        assertTrue(viewModel.recentPdfs.isEmpty())
    }

    private fun clearPreferences() {
        context.getSharedPreferences(RecentPdfStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
