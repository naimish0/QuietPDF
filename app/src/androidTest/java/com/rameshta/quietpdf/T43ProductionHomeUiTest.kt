package com.rameshta.quietpdf

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.FavoritePdf
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenFailure
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.RecentPdf
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class T43ProductionHomeUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun backRestoresEachPreviousDestinationInOrder() {
        setContent(withFiles = true)
        composeRule.onNodeWithTag("smart_home_nav_Files").performClick()
        composeRule.onNodeWithTag("smart_files_search_banner").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("smart_search_title").assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("smart_files_title").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_files_search_banner").assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("smart_home_search_action").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_tools_title").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bottomNavigationAlsoParticipatesInBackHistory() {
        setContent()
        composeRule.onNodeWithTag("smart_home_nav_Tools").performClick()
        composeRule.onNodeWithTag("tool_category_Create").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("smart_home_nav_History").performClick()
        composeRule.onNodeWithTag("smart_history_title").assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("tool_category_Create").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun revisitingTabsRemovesObsoleteNavigationBranch() {
        setContent(withFiles = true)
        composeRule.onNodeWithTag("smart_home_nav_Files").performClick()
        composeRule.onNodeWithTag("smart_home_nav_Tools").performClick()
        composeRule.onNodeWithTag("smart_home_nav_History").performClick()
        composeRule.onNodeWithTag("smart_home_nav_Files").performClick()
        composeRule.onNodeWithTag("smart_home_nav_Tools").performClick()

        pressBack()
        composeRule.onNodeWithTag("smart_files_title").assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("smart_home_search_action").assertIsDisplayed()
    }

    @Test
    fun backFromOpenedRecentPdfReturnsToFiles() {
        val uri = Uri.parse("content://recent/selected")
        composeRule.setContent {
            var state by remember { mutableStateOf<PdfOpenState>(PdfOpenState.Idle) }
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = state,
                    recentPdfs = listOf(RecentPdf(uri, "Selected.pdf", 3, 200L)),
                    legacyHomeSections = false,
                    onOpenRecentPdf = {
                        state = PdfOpenState.Opened(uri, "Selected.pdf", 3)
                    },
                    onClosePdf = { state = PdfOpenState.Idle },
                    onOpenPdf = {},
                    renderPage = { _, _ ->
                        PageRenderResult.Failed(PageRenderFailure.UnableToRender)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("smart_home_nav_Files").performClick()
        composeRule.onNodeWithTag("recent_file_0").performScrollTo().performClick()
        composeRule.onNodeWithTag("reader_top_bar").assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("smart_files_title").assertIsDisplayed()
        composeRule.onNodeWithTag("recent_file_0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun backFromPasswordProtectedOpenFailureReturnsToOriginatingTab() {
        lateinit var showPasswordFailure: () -> Unit
        var dismissals = 0
        composeRule.setContent {
            var state by remember { mutableStateOf<PdfOpenState>(PdfOpenState.Idle) }
            showPasswordFailure = {
                state = PdfOpenState.Failed(PdfOpenFailure.PasswordProtected)
            }
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = state,
                    legacyHomeSections = false,
                    onClosePdf = {
                        dismissals++
                        state = PdfOpenState.Idle
                    },
                    onOpenPdf = {},
                    renderPage = { _, _ ->
                        PageRenderResult.Failed(PageRenderFailure.UnableToRender)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("smart_home_nav_Files").performClick()
        composeRule.runOnIdle(showPasswordFailure)
        composeRule.onNodeWithTag("open_error").assertIsDisplayed()
        composeRule.onNodeWithTag("open_pdf_button").assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("smart_files_title").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun filesProvidesSearchFiltersAndAccurateContentDestinations() {
        setContent(withFiles = true)
        composeRule.onNodeWithTag("smart_home_nav_Files").performClick()
        composeRule.onNodeWithTag("smart_files_search_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_files_filter_All").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_files_filter_Recent").performClick()
        composeRule.onNodeWithTag("recent_files_title").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeRecentFilesHaveProminentContainerCountAndPersistentSeeAll() {
        setContent(withFiles = true)

        composeRule.onNodeWithTag("smart_home_recent_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("smart_home_recent_count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1 recent PDF").assertExists()
        composeRule.onNodeWithTag("smart_home_recent_container").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("smart_recent_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("smart_home_recent_see_all").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun toolsAreGroupedByIntentAndContainOnlyWorkingActions() {
        setContent()
        composeRule.onNodeWithTag("smart_home_nav_Tools").performClick()
        composeRule.onNodeWithTag("tool_category_Create").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("tool_category_Secure").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("protect_pdf_button").performScrollTo().assertIsDisplayed()
    }

    private fun setContent(withFiles: Boolean = false) {
        val recent = if (withFiles) listOf(
            RecentPdf(Uri.parse("content://recent/one"), "Recent.pdf", 3, 200L),
        ) else emptyList()
        val favorites = if (withFiles) listOf(
            FavoritePdf(Uri.parse("content://favorite/one"), "Favorite.pdf", 5, 100L),
        ) else emptyList()
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    recentPdfs = recent,
                    favoritePdfs = favorites,
                    legacyHomeSections = false,
                    onOpenPdf = {},
                    renderPage = { _, _ ->
                        PageRenderResult.Failed(PageRenderFailure.UnableToRender)
                    },
                )
            }
        }
    }
}
