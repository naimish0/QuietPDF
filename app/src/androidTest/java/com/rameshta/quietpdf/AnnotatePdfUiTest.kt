package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.AnnotatePdfFailure
import com.rameshta.quietpdf.pdf.AnnotatePdfPreviewResult
import com.rameshta.quietpdf.pdf.AnnotatePdfState
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfAnnotationItem
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotatePdfUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun homeAnnotationButtonStartsSelection() {
        val selections = AtomicInteger()
        setContent(onAnnotate = { selections.incrementAndGet() })
        composeRule.onNodeWithTag("annotate_pdf_button").performScrollTo().performClick()
        assertEquals(1, selections.get())
    }

    @Test
    fun freeTextCanBeStagedPreviewedAndSavedAsStandardAnnotation() {
        val submitted = AtomicReference<List<PdfAnnotationItem>>()
        setContent(
            state = configuring(),
            renderPreview = { _, _, _ -> readyPreview() },
            onConfirm = submitted::set,
        )
        composeRule.onNodeWithTag("annotate_pdf_scope").assertIsDisplayed()
        composeRule.onNodeWithText("does not edit existing PDF text", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("annotate_free_text").performTextInput("Needs review")
        composeRule.onNodeWithTag("annotate_add_text").performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithTag("annotate_item_count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Staged annotations: 1").assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("annotate_save").assertIsEnabled().performClick()
        assertEquals("Needs review", (submitted.get().single() as PdfAnnotationItem.FreeText).text)
    }

    @Test
    fun highlightSupportsUndoRedoAndDelete() {
        setContent(state = configuring(), renderPreview = { _, _, _ -> readyPreview() })
        composeRule.onNodeWithTag("annotate_tool_Highlight").performClick()
        composeRule.onNodeWithTag("annotate_add_highlight").performScrollTo().performClick()
        composeRule.onNodeWithTag("annotate_undo").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("annotate_redo").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("annotate_delete_0").performScrollTo().performClick()
        composeRule.onNodeWithText("Staged annotations: 0").assertIsDisplayed()
    }

    @Test
    fun inkDrawingCanBeAddedAndPreviewed() {
        setContent(state = configuring(), renderPreview = { _, _, _ -> readyPreview() })
        composeRule.onNodeWithTag("annotate_tool_Ink").performClick()
        composeRule.onNodeWithTag("annotate_ink_pad").performTouchInput {
            swipe(Offset(20f, 160f), Offset(260f, 60f), 350)
        }
        composeRule.onNodeWithTag("annotate_add_ink").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("annotate_preview").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savingIsCancellableAndPasswordFailureIsActionable() {
        val cancellations = AtomicInteger()
        setContent(
            state = AnnotatePdfState.Saving,
            onCancel = { cancellations.incrementAndGet() },
        )
        composeRule.onNodeWithTag("operation_cancel").performClick()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun passwordFailureExplainsRecovery() {
        setContent(state = AnnotatePdfState.Failed(AnnotatePdfFailure.PasswordProtected))
        composeRule.onNodeWithTag("annotate_pdf_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Remove its password first", substring = true).assertIsDisplayed()
    }

    private fun configuring() = AnnotatePdfState.Configuring(
        Uri.parse("content://test/source"), "notes.pdf", 3,
    )

    private fun readyPreview() = AnnotatePdfPreviewResult.Ready(
        Bitmap.createBitmap(360, 520, Bitmap.Config.ARGB_8888),
    )

    private fun setContent(
        state: AnnotatePdfState = AnnotatePdfState.Idle,
        onAnnotate: () -> Unit = {},
        renderPreview: suspend (List<PdfAnnotationItem>, Int, Int) -> AnnotatePdfPreviewResult = { _, _, _ ->
            AnnotatePdfPreviewResult.Failed
        },
        onConfirm: (List<PdfAnnotationItem>) -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    onOpenPdf = {},
                    renderPage = { _, _ -> PageRenderResult.Failed(PageRenderFailure.UnableToRender) },
                    annotatePdfState = state,
                    onAnnotatePdf = onAnnotate,
                    renderAnnotationPreview = renderPreview,
                    onConfirmAnnotations = onConfirm,
                    onCancelPdfAnnotation = onCancel,
                )
            }
        }
    }
}
