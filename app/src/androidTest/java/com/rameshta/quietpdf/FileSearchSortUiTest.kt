package com.rameshta.quietpdf

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.FavoritePdf
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.RecentPdf
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileSearchSortUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun searchFiltersRecentAndFavoriteFilenamesCaseInsensitively() {
        setContent(
            favorites = listOf(favorite("Annual Report.pdf", 1, 20L)),
            recents = listOf(recent("Meeting Notes.pdf", 2, 10L)),
        )
        composeRule.onNodeWithTag("file_search_field").performTextInput("ANNUAL")
        composeRule.onNodeWithText("Annual Report.pdf").assertIsDisplayed()
        composeRule.onAllNodesWithText("Meeting Notes.pdf").assertCountEquals(0)
        composeRule.onAllNodesWithTag("recent_files_title").assertCountEquals(0)
    }

    @Test
    fun unmatchedQueryShowsAnExplicitEmptyResult() {
        setContent(recents = listOf(recent("Notes.pdf", 2, 10L)))
        composeRule.onNodeWithTag("file_search_field").performTextInput("invoice")
        composeRule.onNodeWithTag("file_search_no_results").assertIsDisplayed()
    }

    @Test
    fun sortMenuReordersFavoriteFilesByName() {
        setContent(
            favorites = listOf(
                favorite("Zulu.pdf", 1, 30L),
                favorite("Alpha.pdf", 9, 10L),
            ),
        )
        composeRule.onNodeWithTag("favorite_file_0").assertTextContains("Zulu.pdf")
        composeRule.onNodeWithTag("file_sort_button").performClick()
        composeRule.onNodeWithTag("file_sort_NameAscending").performClick()
        composeRule.onNodeWithTag("favorite_file_0").assertTextContains("Alpha.pdf")
    }

    @Test
    fun organizerControlsAreHiddenWhenThereAreNoKnownFiles() {
        setContent()
        composeRule.onAllNodesWithTag("file_search_field").assertCountEquals(0)
        composeRule.onAllNodesWithTag("file_sort_button").assertCountEquals(0)
    }

    private fun setContent(
        favorites: List<FavoritePdf> = emptyList(),
        recents: List<RecentPdf> = emptyList(),
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    favoritePdfs = favorites,
                    recentPdfs = recents,
                    onOpenPdf = {},
                    renderPage = { _, _ -> PageRenderResult.Failed(PageRenderFailure.UnableToRender) },
                )
            }
        }
    }

    private fun favorite(name: String, pages: Int, timestamp: Long) = FavoritePdf(
        Uri.parse("content://favorites/$name"), name, pages, timestamp,
    )

    private fun recent(name: String, pages: Int, timestamp: Long) = RecentPdf(
        Uri.parse("content://recents/$name"), name, pages, timestamp,
    )
}
