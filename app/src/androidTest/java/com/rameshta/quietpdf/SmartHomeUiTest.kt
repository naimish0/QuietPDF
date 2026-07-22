package com.rameshta.quietpdf

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.ContinueReadingPdf
import com.rameshta.quietpdf.pdf.FavoriteToolStore
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
    fun newUserHomePreservesHeroAndLinksToGroupedFunctionalTools() {
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
        composeRule.onNodeWithTag("quick_tools_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("view_all_tools").performScrollTo().performClick()
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
    fun favoriteToolsUsePersistedOrderAndSearchBannerOpensDedicatedSearch() {
        setSmartHome(favoriteTools = listOf(SmartTool.ProtectPdf, SmartTool.MergePdf))

        composeRule.onNodeWithTag("favorite_protect_pdf_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("favorite_merge_pdf_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("smart_home_search_action").performScrollTo().performClick()
        composeRule.onNodeWithTag("smart_search_title").assertIsDisplayed()
        composeRule.onNodeWithTag("file_search_field").assertIsDisplayed()
    }

    @Test
    fun historyIsAMeaningfulBottomNavigationDestination() {
        setSmartHome()
        composeRule.onNodeWithTag("smart_home_nav_History").performClick()
        composeRule.onNodeWithTag("smart_history_title").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_history_empty").assertIsDisplayed()
    }

    @Test
    fun everySettingsCardOpensADedicatedScreen() {
        val selectedLanguage = AtomicReference<String>()
        val advertisingClicks = AtomicInteger()
        setSmartHome(
            onChangeLanguage = selectedLanguage::set,
            adPrivacyOptionsRequired = true,
            onOpenAdvertisingPrivacy = { advertisingClicks.incrementAndGet() },
        )

        composeRule.onAllNodesWithText("Offline", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("smart_home_settings_action").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("smart_home_nav_Home").assertCountEquals(0)
        composeRule.onNodeWithTag("settings_language_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_privacy_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_advertising_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_about_card").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("settings_language_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_language_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_language_system").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("settings_language_hi").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_language_search").performTextInput("German")
        composeRule.onNodeWithTag("settings_language_de").performClick()
        assertEquals("de", selectedLanguage.get())

        composeRule.onNodeWithTag("settings_back_action").performClick()
        composeRule.onNodeWithTag("settings_privacy_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_privacy_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_back_action").performClick()
        composeRule.onNodeWithTag("settings_advertising_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_advertising_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_advertising_manage").performScrollTo().performClick()
        assertEquals(1, advertisingClicks.get())

        composeRule.onNodeWithTag("settings_back_action").performClick()
        composeRule.onNodeWithTag("settings_about_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_about_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_back_action").performClick()
        composeRule.onNodeWithTag("settings_back_action").performClick()
        composeRule.onNodeWithTag("smart_home_settings_action").assertIsDisplayed()
    }

    @Test
    fun toolFavoriteIconReflectsStateAndTogglesImmediately() {
        composeRule.setContent {
            var favorites by remember { mutableStateOf(listOf(SmartTool.ImagesToPdf)) }
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    favoriteTools = favorites,
                    legacyHomeSections = false,
                    onToggleFavoriteTool = { tool ->
                        favorites = if (tool in favorites) favorites - tool else favorites + tool
                    },
                    onOpenPdf = {},
                    renderPage = { _, _ ->
                        PageRenderResult.Failed(PageRenderFailure.UnableToRender)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("smart_home_nav_Tools").performClick()
        composeRule.onNodeWithTag("favorite_tool_ProtectPdf").performScrollTo()
            .assertTextEquals("☆").performClick()
        composeRule.onNodeWithTag("favorite_tool_ProtectPdf").assertTextEquals("★")
        composeRule.onNodeWithContentDescription(
            "Protect PDF: remove from favorite tools",
        ).assertIsDisplayed()
    }

    @Test
    fun fullFavoriteListExplainsWhyAnotherToolCannotBeAdded() {
        setSmartHome(favoriteTools = FavoriteToolStore.DEFAULT_TOOLS)

        composeRule.onNodeWithTag("smart_home_nav_Tools").performClick()
        composeRule.onNodeWithTag("favorite_tool_ProtectPdf").performScrollTo()
            .assertTextEquals("☆")
        composeRule.onNodeWithContentDescription(
            "Protect PDF: favorite limit reached; remove another favorite first",
        ).assertIsDisplayed()
    }

    private fun setSmartHome(
        continueReading: ContinueReadingPdf? = null,
        favoriteTools: List<SmartTool> = emptyList(),
        onOpenPdf: () -> Unit = {},
        onScanDocument: () -> Unit = {},
        onOpenRecentPdf: (Uri) -> Unit = {},
        onChangeLanguage: (String) -> Unit = {},
        adPrivacyOptionsRequired: Boolean = false,
        onOpenAdvertisingPrivacy: () -> Unit = {},
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
                    settings = QuietPdfSettings(
                        onChangeLanguage = onChangeLanguage,
                        adPrivacyOptionsRequired = adPrivacyOptionsRequired,
                        onOpenAdvertisingPrivacy = onOpenAdvertisingPrivacy,
                    ),
                    renderPage = { _, _ ->
                        PageRenderResult.Failed(PageRenderFailure.UnableToRender)
                    },
                )
            }
        }
    }
}
