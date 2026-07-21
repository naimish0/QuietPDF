package com.rameshta.quietpdf

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfHistoryEntry
import com.rameshta.quietpdf.pdf.PdfHistoryOperation
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun historyShowsCompletedOperationWithoutDocumentMetadata() {
        setContent(listOf(PdfHistoryEntry(PdfHistoryOperation.CompressPdf, 1_000L)))
        composeRule.onNodeWithTag("history_title").assertIsDisplayed()
        composeRule.onNodeWithText("Compressed PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Completed", substring = true).assertIsDisplayed()
    }

    @Test
    fun historyDefaultsToFiveEntriesAndCanExpand() {
        val entries = PdfHistoryOperation.entries.take(7).mapIndexed { index, operation ->
            PdfHistoryEntry(operation, 10_000L - index)
        }
        setContent(entries)
        composeRule.onAllNodesWithTag("history_item_0").assertCountEquals(1)
        composeRule.onAllNodesWithTag("history_item_4").assertCountEquals(1)
        composeRule.onAllNodesWithTag("history_item_5").assertCountEquals(0)
        composeRule.onNodeWithTag("history_expand").performScrollTo().performClick()
        composeRule.onNodeWithTag("history_item_6").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Show less").assertIsDisplayed()
    }

    @Test
    fun clearHistoryRequiresConfirmationAndExplainsFilesAreNotDeleted() {
        val clears = AtomicInteger()
        setContent(
            listOf(PdfHistoryEntry(PdfHistoryOperation.MergePdf, 1_000L)),
            onClear = { clears.incrementAndGet() },
        )
        composeRule.onNodeWithTag("history_clear").performClick()
        composeRule.onNodeWithTag("history_clear_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("will not be deleted", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("history_clear_confirm").performClick()
        assertEquals(1, clears.get())
    }

    @Test
    fun emptyHistoryDoesNotAddAnEmptySection() {
        setContent(emptyList())
        composeRule.onAllNodesWithTag("history_title").assertCountEquals(0)
    }

    private fun setContent(
        history: List<PdfHistoryEntry>,
        onClear: () -> Unit = {},
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    history = history,
                    onClearHistory = onClear,
                    onOpenPdf = {},
                    renderPage = { _, _ -> PageRenderResult.Failed(PageRenderFailure.UnableToRender) },
                )
            }
        }
    }
}
