package com.rameshta.quietpdf

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.FavoriteToolStore
import com.rameshta.quietpdf.pdf.SmartTool
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteToolStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store by lazy { FavoriteToolStore(context) }

    @Before
    fun clearBefore() = clearPreferences()

    @After
    fun clearAfter() = clearPreferences()

    @Test
    fun firstRunUsesStableFourToolDefaults() {
        assertEquals(FavoriteToolStore.DEFAULT_TOOLS, store.load())
    }

    @Test
    fun togglePreservesOrderAndNeverAddsAFifthTool() {
        assertEquals(
            listOf(SmartTool.MergePdf, SmartTool.CompressPdf, SmartTool.SplitPdf),
            store.toggle(SmartTool.ImagesToPdf),
        )
        assertEquals(
            listOf(
                SmartTool.MergePdf,
                SmartTool.CompressPdf,
                SmartTool.SplitPdf,
                SmartTool.ProtectPdf,
            ),
            store.toggle(SmartTool.ProtectPdf),
        )
        assertEquals(store.load(), store.toggle(SmartTool.SignPdf))
    }

    @Test
    fun userCanRemoveEveryDefaultWithoutDefaultsReturning() {
        FavoriteToolStore.DEFAULT_TOOLS.forEach(store::toggle)
        assertEquals(emptyList<SmartTool>(), FavoriteToolStore(context).load())
    }

    private fun clearPreferences() {
        context.getSharedPreferences(FavoriteToolStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
