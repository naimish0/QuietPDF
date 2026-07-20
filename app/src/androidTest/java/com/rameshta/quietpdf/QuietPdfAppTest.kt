package com.rameshta.quietpdf

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.PdfOpenFailure
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuietPdfAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idleState_offersOpenActionAndPrivacyExplanation() {
        setContent(PdfOpenState.Idle)

        composeRule.onNodeWithText("Your PDFs stay on this device").assertIsDisplayed()
        composeRule.onNodeWithTag("open_pdf_button").assertTextEquals("Open PDF")
    }

    @Test
    fun openedState_showsNameAndPageCount() {
        setContent(
            PdfOpenState.Opened(
                uri = Uri.parse("content://test/document"),
                displayName = "fixture.pdf",
                pageCount = 2,
            ),
        )

        composeRule.onNodeWithTag("opened_file_name").assertTextEquals("fixture.pdf")
        composeRule.onNodeWithText("2 pages").assertIsDisplayed()
    }

    @Test
    fun failureState_explainsHowToRecover() {
        setContent(PdfOpenState.Failed(PdfOpenFailure.PermissionDenied))

        composeRule.onNodeWithTag("open_error").assertIsDisplayed()
        composeRule.onNodeWithTag("open_pdf_button").assertTextEquals("Open PDF")
    }

    private fun setContent(state: PdfOpenState) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(state = state, onOpenPdf = {})
            }
        }
    }
}
