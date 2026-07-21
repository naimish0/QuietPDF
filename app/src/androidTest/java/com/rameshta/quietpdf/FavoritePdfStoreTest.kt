package com.rameshta.quietpdf

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.FavoritePdfStore
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.PdfOpenViewModel
import com.rameshta.quietpdf.pdf.RecentPdfStore
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritePdfStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = AtomicLong(1_000L)
    private val store by lazy { FavoritePdfStore(context) { clock.getAndIncrement() } }

    @Before
    fun clearBefore() = clearPreferences()

    @After
    fun clearAfter() = clearPreferences()

    @Test
    fun addKeepsNewestFirstDeduplicatesAndBoundsLocalMetadata() {
        repeat(FavoritePdfStore.MAX_ENTRIES + 3) { index ->
            store.add(Uri.parse("content://documents/$index"), " Document $index.pdf ", index + 1)
        }
        val entries = store.load()
        assertEquals(FavoritePdfStore.MAX_ENTRIES, entries.size)
        assertEquals("content://documents/22", entries.first().uri.toString())
        assertEquals("Document 22.pdf", entries.first().displayName)

        val originalAdded = entries.first { it.uri.toString() == "content://documents/5" }
            .addedEpochMillis
        clock.set(9_000L)
        store.add(Uri.parse("content://documents/5"), "Renamed.pdf", 42)
        val refreshed = store.load().first { it.uri.toString() == "content://documents/5" }
        assertEquals(originalAdded, refreshed.addedEpochMillis)
        assertEquals("Renamed.pdf", refreshed.displayName)
        assertEquals(42, refreshed.pageCount)
        assertEquals(1, store.load().count { it.uri.toString() == "content://documents/5" })
    }

    @Test
    fun invalidSchemesAndInvalidDocumentsAreNotPersisted() {
        store.add(Uri.parse("file:///private/document.pdf"), "Private.pdf", 1)
        store.add(Uri.parse("https://example.com/document.pdf"), "Remote.pdf", 1)
        store.add(Uri.parse("content://documents/empty"), "Empty.pdf", 0)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun removeOnlyChangesFavoriteMetadata() {
        val first = Uri.parse("content://documents/first")
        val second = Uri.parse("content://documents/second")
        store.add(first, "First.pdf", 1)
        store.add(second, "Second.pdf", 2)
        assertEquals(listOf(second), store.remove(first).map { it.uri })
    }

    @Test
    fun corruptStoredDataRecoversAsAnEmptyList() {
        context.getSharedPreferences(FavoritePdfStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString("entries", "not-json").commit()
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun unavailableFavoriteIsRemovedFromFavoritesAndRecentsAfterFailedOpen() {
        val missing = Uri.parse("content://com.rameshta.quietpdf.fileprovider/not-available.pdf")
        store.add(missing, "Missing.pdf", 2)
        RecentPdfStore(context).record(missing, "Missing.pdf", 2)
        val viewModel = PdfOpenViewModel(context.applicationContext as Application)
        viewModel.openFavoritePdf(missing)
        val deadline = SystemClock.uptimeMillis() + 3_000L
        while (viewModel.state is PdfOpenState.Opening && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(20L)
        }
        assertTrue(viewModel.state is PdfOpenState.Failed)
        assertTrue(viewModel.favoritePdfs.isEmpty())
        assertTrue(viewModel.recentPdfs.isEmpty())
    }

    private fun clearPreferences() {
        context.getSharedPreferences(FavoritePdfStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(RecentPdfStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
