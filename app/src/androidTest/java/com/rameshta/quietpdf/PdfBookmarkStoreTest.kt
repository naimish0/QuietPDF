package com.rameshta.quietpdf

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.PdfBookmarkStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfBookmarkStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(
        PdfBookmarkStore.PreferencesName,
        Context.MODE_PRIVATE,
    )
    private val store = PdfBookmarkStore(context)

    @Before
    @After
    fun clearBookmarks() {
        preferences.edit().clear().commit()
    }

    @Test
    fun togglesValidPagesWithoutPersistingDocumentUri() {
        val uri = Uri.parse("content://private-provider/sensitive-document-name.pdf")

        assertEquals(setOf(3), store.toggle(uri, pageIndex = 3, pageCount = 5))
        assertEquals(emptySet<Int>(), store.toggle(uri, pageIndex = 3, pageCount = 5))
        store.toggle(uri, pageIndex = 4, pageCount = 5)

        assertEquals(emptySet<Int>(), store.restore(uri, pageCount = 4))
        assertFalse(preferences.all.keys.any { it.contains(uri.toString()) })
        assertFalse(preferences.all.values.any { it.toString().contains(uri.toString()) })
    }
}
