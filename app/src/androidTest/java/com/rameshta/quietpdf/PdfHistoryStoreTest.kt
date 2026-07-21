package com.rameshta.quietpdf

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.PdfHistoryOperation
import com.rameshta.quietpdf.pdf.PdfHistoryStore
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
class PdfHistoryStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = AtomicLong(1_000L)
    private val store by lazy { PdfHistoryStore(context) { clock.getAndIncrement() } }

    @Before
    fun clearBefore() = clearPreferences()

    @After
    fun clearAfter() = clearPreferences()

    @Test
    fun recordKeepsEveryCompletionNewestFirstAndBoundsPrivateMetadata() {
        repeat(PdfHistoryStore.MAX_ENTRIES + 3) { index ->
            store.record(
                if (index % 2 == 0) PdfHistoryOperation.CompressPdf
                else PdfHistoryOperation.MergePdf,
            )
        }
        val entries = store.load()
        assertEquals(PdfHistoryStore.MAX_ENTRIES, entries.size)
        assertEquals(1_052L, entries.first().completedEpochMillis)
        assertEquals(PdfHistoryOperation.CompressPdf, entries.first().operation)
        assertEquals(1_003L, entries.last().completedEpochMillis)
    }

    @Test
    fun repeatedOperationIsRecordedAsSeparateHistoryEvents() {
        store.record(PdfHistoryOperation.RotatePages)
        store.record(PdfHistoryOperation.RotatePages)
        assertEquals(2, store.load().size)
        assertEquals(listOf(1_001L, 1_000L), store.load().map { it.completedEpochMillis })
    }

    @Test
    fun clearRemovesOnlyHistoryMetadata() {
        store.record(PdfHistoryOperation.ProtectPdf)
        store.clear()
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun removeDeletesOnlyTheSelectedHistoryEvent() {
        store.record(PdfHistoryOperation.MergePdf)
        store.record(PdfHistoryOperation.ProtectPdf)
        val selected = store.load().first()

        val remaining = store.remove(selected)

        assertEquals(listOf(PdfHistoryOperation.MergePdf), remaining.map { it.operation })
        assertEquals(remaining, store.load())
    }

    @Test
    fun corruptStoredDataRecoversAsAnEmptyList() {
        context.getSharedPreferences(PdfHistoryStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString("entries", "not-json").commit()
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun failuresAndCancellationsDoNotCreateHistory() {
        val viewModel = PdfOpenViewModel(context.applicationContext as Application)
        viewModel.selectPdfsForMerge(emptyList())
        viewModel.cancelCompressPdf()
        viewModel.cancelSplitPdf()
        viewModel.open(Uri.parse("content://com.rameshta.quietpdf.fileprovider/not-available.pdf"))
        val deadline = SystemClock.uptimeMillis() + 3_000L
        while (viewModel.state is PdfOpenState.Opening && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(20L)
        }
        assertTrue(viewModel.state is PdfOpenState.Failed)
        assertTrue(viewModel.history.isEmpty())
        assertTrue(store.load().isEmpty())
    }

    private fun clearPreferences() {
        context.getSharedPreferences(PdfHistoryStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
