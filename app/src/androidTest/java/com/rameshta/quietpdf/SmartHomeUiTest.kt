package com.rameshta.quietpdf

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.ContinueReadingPdf
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.SmartTool
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartHomeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newUserHomeShowsTrustPrimaryActionsAndAllFunctionalTools() {
        val opens = AtomicInteger()
        val scans = AtomicInteger()
        setSmartHome(onOpenPdf = { opens.incrementAndGet() }, onScanDocument = { scans.incrementAndGet() })

        composeRule.onNodeWithTag("smart_home_privacy").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Privacy shield. PDF processing stays on this device.",
        ).assertExists()
        composeRule.onNodeWithTag("open_pdf_button").assertTextEquals("Open PDF")
        composeRule.onNodeWithTag("scan_document_button").assertTextEquals("Scan document")
            .performClick()
        assertEquals(0, opens.get())
        assertEquals(1, scans.get())
        composeRule.onNodeWithTag("protect_pdf_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun returningUserCanResumeAtPersistedReadingPosition() {
        val uri = Uri.parse("content://documents/report")
        val resumed = AtomicReference<Uri>()
        setSmartHome(
            continueReading = ContinueReadingPdf(uri, "Report.pdf", 5, 12),
            onOpenRecentPdf = resumed::set,
        )

        composeRule.onNodeWithTag("continue_reading_card").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_reading_resume").performClick()
        assertEquals(uri, resumed.get())
    }

    @Test
    fun favoriteToolsUsePersistedOrderAndTopSearchOpensFiles() {
        setSmartHome(favoriteTools = listOf(SmartTool.ProtectPdf, SmartTool.MergePdf))

        composeRule.onNodeWithTag("favorite_protect_pdf_button").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite_merge_pdf_button").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_home_search_action").performClick()
        composeRule.onNodeWithTag("smart_files_title").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_files_empty").assertIsDisplayed()
    }

    private fun setSmartHome(
        continueReading: ContinueReadingPdf? = null,
        favoriteTools: List<SmartTool> = emptyList(),
        onOpenPdf: () -> Unit = {},
        onScanDocument: () -> Unit = {},
        onOpenRecentPdf: (Uri) -> Unit = {},
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    continueReading = continueReading,
                    favoriteTools = favoriteTools,
                    legacyHomeSections = false,
                    onOpenPdf = onOpenPdf,
                    onScanDocument = onScanDocument,
                    onOpenRecentPdf = onOpenRecentPdf,
                    renderPage = { _, _ ->
                        PageRenderResult.Failed(PageRenderFailure.UnableToRender)
                    },
                )
            }
        }
    }
}
