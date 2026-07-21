package com.rameshta.quietpdf

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
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
class T43ProductionHomeUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun searchIsDedicatedAndBackRestoresHome() {
        setContent()
        composeRule.onNodeWithTag("smart_home_search_action").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("smart_search_title").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("smart_home_search_action").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_tools_title").performScrollTo().assertIsDisplayed()
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
