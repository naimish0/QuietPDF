package com.rameshta.quietpdf

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.RecentPdf
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentFilesUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun recentFilesShowMetadataAndOpenOrRemoveSelectedDocument() {
        val opened = AtomicReference<Uri>()
        val removed = AtomicReference<Uri>()
        val items = listOf(
            RecentPdf(Uri.parse("content://documents/a"), "Annual report.pdf", 1, 20L),
            RecentPdf(Uri.parse("content://documents/b"), "Notes.pdf", 8, 10L),
        )
        setContent(items, opened::set, removed::set)
        composeRule.onNodeWithTag("recent_files_title").assertIsDisplayed()
        composeRule.onNodeWithText("Annual report.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("1 page").assertIsDisplayed()
        composeRule.onNodeWithText("8 pages").assertIsDisplayed()
        composeRule.onNodeWithTag("recent_file_0").performClick()
        assertEquals(items[0].uri, opened.get())
        composeRule.onNodeWithTag("recent_file_remove_1").performClick()
        assertEquals(items[1].uri, removed.get())
    }

    @Test
    fun clearAllRequiresConfirmationAndExplainsFilesAreNotDeleted() {
        val clears = AtomicInteger()
        setContent(
            listOf(RecentPdf(Uri.parse("content://documents/a"), "A.pdf", 2, 1L)),
            onClear = { clears.incrementAndGet() },
        )
        composeRule.onNodeWithTag("recent_files_clear").performClick()
        composeRule.onNodeWithTag("recent_files_clear_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("will not be deleted", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("recent_files_clear_confirm").performClick()
        assertEquals(1, clears.get())
    }

    @Test
    fun emptyRecentListDoesNotAddAnEmptySection() {
        setContent(emptyList())
        composeRule.onAllNodesWithTag("recent_files_title").assertCountEquals(0)
    }

    @Test
    fun homeDisplaysAtMostFiveRecentFiles() {
        setContent((0..6).map { index ->
            RecentPdf(Uri.parse("content://documents/$index"), "File $index.pdf", index + 1, 100L - index)
        })
        composeRule.onAllNodesWithText("Remove").assertCountEquals(5)
        composeRule.onNodeWithText("File 4.pdf").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("File 5.pdf").assertCountEquals(0)
    }

    private fun setContent(
        recentPdfs: List<RecentPdf>,
        onOpen: (Uri) -> Unit = {},
        onRemove: (Uri) -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    recentPdfs = recentPdfs,
                    onOpenRecentPdf = onOpen,
                    onRemoveRecentPdf = onRemove,
                    onClearRecentPdfs = onClear,
                    onOpenPdf = {},
                    renderPage = { _, _ -> PageRenderResult.Failed(PageRenderFailure.UnableToRender) },
                )
            }
        }
    }
}
