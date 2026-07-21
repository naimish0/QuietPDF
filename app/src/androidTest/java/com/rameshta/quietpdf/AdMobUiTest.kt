package com.rameshta.quietpdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.ImagesToPdfState
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdMobUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun consentedBannerAppearsOnlyOnHomeAndPrivacyDisclosureIsVisible() {
        setApp()
        composeRule.onNodeWithTag("fake_home_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_privacy_disclosure").assertIsDisplayed()

        composeRule.onNodeWithTag("smart_home_nav_Files").performClick()
        composeRule.onAllNodesWithTag("fake_home_banner").assertCountEquals(0)
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

    private fun setApp(
        adsCanLoad: Boolean = true,
        imagesToPdfState: ImagesToPdfState = ImagesToPdfState.Idle,
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    legacyHomeSections = false,
                    adsCanLoad = adsCanLoad,
                    homeBannerContent = {
                        Box(
                            Modifier.fillMaxWidth().height(10.dp).testTag("fake_home_banner"),
                        )
                    },
                    onOpenPdf = {},
                    renderPage = { _, _ ->
                        PageRenderResult.Failed(PageRenderFailure.UnableToRender)
                    },
                    imagesToPdfState = imagesToPdfState,
                )
            }
        }
    }
}
