package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.SignPdfPreviewResult
import com.rameshta.quietpdf.pdf.SignPdfState
import com.rameshta.quietpdf.pdf.VisibleSignatureSettings
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignPdfUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun homeSignButtonStartsSelection() {
        val selections = AtomicInteger()
        setContent(onSignPdf = { selections.incrementAndGet() })
        composeRule.onNodeWithTag("sign_pdf_button").performScrollTo().performClick()
        assertEquals(1, selections.get())
    }

    @Test
    fun importedSignatureShowsNonCryptographicNoticePreviewAndReturnsPlacement() {
        val signature = Bitmap.createBitmap(220, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        val submitted = AtomicReference<VisibleSignatureSettings>()
        setContent(
            signPdfState = SignPdfState.Configuring(
                Uri.parse("content://test/source"), "contract.pdf", 3, signature,
            ),
            renderPreview = { _, _, _ ->
                SignPdfPreviewResult.Ready(Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888))
            },
            onConfirm = { _, settings -> submitted.set(settings) },
        )
        composeRule.onNodeWithTag("sign_pdf_visible_notice").assertIsDisplayed()
        composeRule.onNodeWithText("certificate-backed", substring = true).assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("sign_pdf_save"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sign_pdf_save").assertIsEnabled().performClick()
        assertEquals(0, submitted.get().pageIndex)
        assertEquals(0.3f, submitted.get().widthFraction)
    }

    @Test
    fun drawnSignatureSupportsUndoRedoAndPlacementPreview() {
        setContent(
            signPdfState = SignPdfState.Configuring(
                Uri.parse("content://test/source"), "drawing.pdf", 1,
            ),
            renderPreview = { _, _, _ ->
                SignPdfPreviewResult.Ready(Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888))
            },
        )
        composeRule.onNodeWithTag("sign_pdf_drawing_pad").performTouchInput {
            swipe(Offset(20f, 110f), Offset(260f, 45f), 350)
        }
        composeRule.onNodeWithTag("sign_pdf_undo").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("sign_pdf_redo").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("sign_pdf_use_drawing").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sign_pdf_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("sign_pdf_change").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savingIsCancellable() {
        val cancellations = AtomicInteger()
        setContent(
            signPdfState = SignPdfState.Saving,
            onCancel = { cancellations.incrementAndGet() },
        )
        composeRule.onNodeWithTag("operation_cancel").performClick()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun passwordFailureIsActionable() {
        setContent(signPdfState = SignPdfState.Failed(com.rameshta.quietpdf.pdf.SignPdfFailure.PasswordProtected))
        composeRule.onNodeWithTag("sign_pdf_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Remove its password first", substring = true).assertIsDisplayed()
    }

    private fun setContent(
        signPdfState: SignPdfState = SignPdfState.Idle,
        onSignPdf: () -> Unit = {},
        renderPreview: suspend (Bitmap, VisibleSignatureSettings, Int) -> SignPdfPreviewResult = { _, _, _ ->
            SignPdfPreviewResult.Failed
        },
        onConfirm: (Bitmap, VisibleSignatureSettings) -> Unit = { _, _ -> },
        onCancel: () -> Unit = {},
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    onOpenPdf = {},
                    renderPage = { _, _ -> PageRenderResult.Failed(PageRenderFailure.UnableToRender) },
                    signPdfState = signPdfState,
                    onSignPdf = onSignPdf,
                    renderSignaturePreview = renderPreview,
                    onConfirmSignature = onConfirm,
                    onCancelPdfSigning = onCancel,
                )
            }
        }
    }
}
