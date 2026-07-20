package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenFailure
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList

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

        composeRule.onNodeWithText("fixture.pdf").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf_page_1").assertIsDisplayed()
        composeRule.onNodeWithText("Page 1 of 2").assertIsDisplayed()
    }

    @Test
    fun failureState_explainsHowToRecover() {
        setContent(PdfOpenState.Failed(PdfOpenFailure.PermissionDenied))

        composeRule.onNodeWithTag("open_error").assertIsDisplayed()
        composeRule.onNodeWithTag("open_pdf_button").assertTextEquals("Open PDF")
    }

    @Test
    fun largeDocument_onlyRendersPagesNearTheViewport() {
        val renderCount = AtomicInteger()
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/large-document"),
                displayName = "large.pdf",
                pageCount = 1_000,
            ),
            renderPage = { _, _ ->
                renderCount.incrementAndGet()
                PageRenderResult.Ready(
                    Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888),
                )
            },
        )

        composeRule.waitUntil(timeoutMillis = 5_000) { renderCount.get() > 0 }
        assertTrue("Lazy reader rendered too many pages", renderCount.get() < 20)
    }

    @Test
    fun doubleTap_zoomsRerendersSharplyAndCanReset() {
        val requestedWidths = CopyOnWriteArrayList<Int>()
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/zoom-document"),
                displayName = "zoom.pdf",
                pageCount = 1,
            ),
            renderPage = { _, width ->
                requestedWidths += width
                PageRenderResult.Ready(
                    Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888),
                )
            },
        )

        composeRule.onNodeWithTag("pdf_page_1").performTouchInput { doubleClick(center) }
        composeRule.onNodeWithTag("reset_zoom_1").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            requestedWidths.size >= 2 && requestedWidths.max() > requestedWidths.min() * 2
        }

        composeRule.onNodeWithTag("reset_zoom_1").performClick()
        composeRule.onAllNodesWithTag("reset_zoom_1").assertCountEquals(0)
    }

    @Test
    fun pinchGesture_zoomsThePage() {
        setContent(
            PdfOpenState.Opened(
                uri = Uri.parse("content://test/pinch-document"),
                displayName = "pinch.pdf",
                pageCount = 1,
            ),
        )

        composeRule.onNodeWithTag("pdf_page_1").performTouchInput {
            down(0, center - Offset(40f, 0f))
            down(1, center + Offset(40f, 0f))
            updatePointerTo(0, center - Offset(160f, 0f))
            updatePointerTo(1, center + Offset(160f, 0f))
            move()
            up(1)
            up(0)
        }

        composeRule.onNodeWithTag("reset_zoom_1").assertIsDisplayed()
    }

    private fun setContent(
        state: PdfOpenState,
        renderPage: suspend (Int, Int) -> PageRenderResult = { _, _ ->
            PageRenderResult.Ready(
                Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888),
            )
        },
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = state,
                    onOpenPdf = {},
                    renderPage = renderPage,
                )
            }
        }
    }
}
