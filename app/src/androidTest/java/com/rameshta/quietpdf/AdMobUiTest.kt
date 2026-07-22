package com.rameshta.quietpdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.ImagesToPdfState
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.PdfHistoryEntry
import com.rameshta.quietpdf.pdf.SplitPdfState
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdMobUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun consentedBannerAppearsOnEveryNavigationScreenAndPrivacyDisclosureIsVisible() {
        setApp()
        composeRule.onAllNodesWithTag("home_banner_top_spacing").assertCountEquals(0)
        composeRule.onNodeWithTag("fake_home_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_privacy_disclosure").assertIsDisplayed()

        composeRule.onNodeWithTag("smart_home_nav_Files").performClick()
        composeRule.onNodeWithTag("fake_home_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_home_nav_Tools").performClick()
        composeRule.onNodeWithTag("fake_home_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_home_nav_History").performClick()
        composeRule.onNodeWithTag("fake_home_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_home_nav_Home").performClick()
        composeRule.onNodeWithTag("smart_home_search_action").performClick()
        composeRule.onNodeWithTag("fake_home_banner").assertIsDisplayed()
    }

    @Test
    fun bannerIsAbsentWithoutConsent() {
        setApp(adsCanLoad = false)
        composeRule.onAllNodesWithTag("fake_home_banner").assertCountEquals(0)
    }

    @Test
    fun bannerIsAbsentWhileAnOperationIsBeingConfigured() {
        setApp(
            adsCanLoad = true,
            imagesToPdfState = ImagesToPdfState.Configuring(imageCount = 2),
        )
        composeRule.onAllNodesWithTag("fake_home_banner").assertCountEquals(0)
    }

    @Test
    fun bannerAppearsOnASuccessfulResultScreen() {
        setApp(splitPdfState = SplitPdfState.Completed(outputCount = 2))
        composeRule.onNodeWithTag("fake_home_banner").assertIsDisplayed()
    }

    @Test
    fun homeNavigationFromSuccessfulResultUsesTheDismissTransition() {
        var dismissals = 0
        setApp(
            splitPdfState = SplitPdfState.Completed(outputCount = 2),
            onDismissSplitResult = { dismissals++ },
        )
        composeRule.onNodeWithTag("smart_home_nav_Tools").performClick()
        composeRule.onNodeWithTag("smart_home_nav_Home").performClick()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun consentedBannerAppearsOnHistoryWithOrWithoutHistoryEntries() {
        setApp()
        composeRule.onNodeWithTag("smart_home_nav_History").performClick()
        composeRule.onNodeWithTag("fake_home_banner").assertIsDisplayed()
    }

    @Test
    fun navigationKeepsOneBannerCompositionAlive() {
        var compositions = 0
        var disposals = 0
        setApp(
            onBannerComposed = { compositions++ },
            onBannerDisposed = { disposals++ },
        )
        composeRule.onNodeWithTag("smart_home_nav_Files").performClick()
        composeRule.onNodeWithTag("smart_home_nav_Tools").performClick()
        composeRule.onNodeWithTag("smart_home_nav_History").performClick()
        composeRule.onNodeWithTag("smart_home_nav_Home").performClick()
        composeRule.onNodeWithTag("smart_home_search_action").performClick()
        composeRule.runOnIdle {
            assertEquals(1, compositions)
            assertEquals(0, disposals)
        }
    }

    private fun setApp(
        adsCanLoad: Boolean = true,
        imagesToPdfState: ImagesToPdfState = ImagesToPdfState.Idle,
        splitPdfState: SplitPdfState = SplitPdfState.Idle,
        onDismissSplitResult: () -> Unit = {},
        history: List<PdfHistoryEntry> = emptyList(),
        onBannerComposed: () -> Unit = {},
        onBannerDisposed: () -> Unit = {},
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    legacyHomeSections = false,
                    history = history,
                    adsCanLoad = adsCanLoad,
                    homeBannerContent = {
                        DisposableEffect(Unit) {
                            onBannerComposed()
                            onDispose(onBannerDisposed)
                        }
                        Box(
                            Modifier.fillMaxWidth().height(10.dp).testTag("fake_home_banner"),
                        )
                    },
                    onOpenPdf = {},
                    renderPage = { _, _ ->
                        PageRenderResult.Failed(PageRenderFailure.UnableToRender)
                    },
                    imagesToPdfState = imagesToPdfState,
                    splitPdfState = splitPdfState,
                    onDismissSplitResult = onDismissSplitResult,
                )
            }
        }
    }
}
