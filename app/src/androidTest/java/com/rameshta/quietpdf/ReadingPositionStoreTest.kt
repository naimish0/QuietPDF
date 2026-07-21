package com.rameshta.quietpdf

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.ReadingPositionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPositionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(
        ReadingPositionStore.PreferencesName,
        Context.MODE_PRIVATE,
    )
    private val store = ReadingPositionStore(context)

    @Before
    @After
    fun clearPositions() {
        preferences.edit().clear().commit()
    }

    @Test
    fun remembersClampedPositionWithoutPersistingDocumentUri() {
        val uri = Uri.parse("content://private-provider/sensitive-document-name.pdf")

        store.remember(uri, pageIndex = 7, pageCount = 10)

        assertEquals(7, store.restore(uri, pageCount = 10))
        assertEquals(4, store.restore(uri, pageCount = 5))
        assertFalse(preferences.all.keys.any { it.contains(uri.toString()) })
        assertFalse(preferences.all.values.any { it.toString().contains(uri.toString()) })
    }
}
