package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenFailure
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.PdfSearchMatch
import com.rameshta.quietpdf.pdf.PdfSearchResult
import com.rameshta.quietpdf.pdf.PdfOutlineEntry
import com.rameshta.quietpdf.pdf.PdfTableOfContentsResult
import com.rameshta.quietpdf.pdf.PdfHealthReport
import com.rameshta.quietpdf.pdf.PdfHealthResult
import com.rameshta.quietpdf.pdf.ImagesToPdfFailure
import com.rameshta.quietpdf.pdf.ImagesToPdfState
import com.rameshta.quietpdf.pdf.ImagePdfLayout
import com.rameshta.quietpdf.pdf.ImagePdfMargin
import com.rameshta.quietpdf.pdf.ImagePdfOrientation
import com.rameshta.quietpdf.pdf.ImagePdfPageSize
import com.rameshta.quietpdf.pdf.ImagePdfScaleMode
import com.rameshta.quietpdf.pdf.MergePdfFailure
import com.rameshta.quietpdf.pdf.MergePdfItem
import com.rameshta.quietpdf.pdf.MergePdfState
import com.rameshta.quietpdf.pdf.SplitPageRange
import com.rameshta.quietpdf.pdf.SplitPdfState
import com.rameshta.quietpdf.pdf.ExtractPagesFailure
import com.rameshta.quietpdf.pdf.ExtractPagesState
import com.rameshta.quietpdf.pdf.DeletePagesFailure
import com.rameshta.quietpdf.pdf.DeletePagesState
import com.rameshta.quietpdf.pdf.RearrangePagesFailure
import com.rameshta.quietpdf.pdf.RearrangePagesState
import com.rameshta.quietpdf.pdf.PageRotation
import com.rameshta.quietpdf.pdf.RotatePagesFailure
import com.rameshta.quietpdf.pdf.RotatePagesState
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
        composeRule.onNodeWithTag("images_to_pdf_button").assertTextEquals("Images to PDF")
    }

    @Test
    fun imagesToPdf_buttonStartsSelection() {
        val selections = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            onImagesToPdf = { selections.incrementAndGet() },
        )
        composeRule.onNodeWithTag("images_to_pdf_button").performClick()
        assertEquals(1, selections.get())
    }

    @Test
    fun imagesToPdf_creationShowsProgress() {
        setContent(
            state = PdfOpenState.Idle,
            imagesToPdfState = ImagesToPdfState.Creating(imageCount = 3),
        )
        composeRule.onNodeWithTag("images_to_pdf_progress").assertIsDisplayed()
        composeRule.onNodeWithText("Creating a PDF from 3 images…").assertIsDisplayed()
    }

    @Test
    fun imagesToPdf_failureIsActionable() {
        setContent(
            state = PdfOpenState.Idle,
            imagesToPdfState = ImagesToPdfState.Failed(ImagesToPdfFailure.InvalidImage),
        )

        composeRule.onNodeWithTag("images_to_pdf_error").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun imagesPdfLayout_returnsExplicitSelections() {
        val selected = AtomicReference<ImagePdfLayout>()
        setContent(
            state = PdfOpenState.Idle,
            imagesToPdfState = ImagesToPdfState.Configuring(imageCount = 4),
            onConfirmImagesPdfLayout = selected::set,
        )

        composeRule.onNodeWithTag("images_pdf_layout_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("layout_page_size_1").performClick()
        composeRule.onNodeWithTag("layout_orientation_2").performClick()
        composeRule.onNodeWithTag("layout_scale_1").performClick()
        composeRule.onNodeWithTag("layout_margin_2").performClick()
        composeRule.onNodeWithTag("layout_confirm").performClick()

        assertEquals(
            ImagePdfLayout(
                pageSize = ImagePdfPageSize.Letter,
                orientation = ImagePdfOrientation.Landscape,
                scaleMode = ImagePdfScaleMode.Fill,
                margin = ImagePdfMargin.Wide,
            ),
            selected.get(),
        )
    }

    @Test
    fun mergePdf_buttonStartsSelection() {
        val selections = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            onMergePdfs = { selections.incrementAndGet() },
        )

        composeRule.onNodeWithTag("merge_pdf_button")
            .assertTextEquals("Merge PDFs")
            .performClick()
        assertEquals(1, selections.get())
    }

    @Test
    fun mergePdf_orderDialogExposesReorderAndConfirmActions() {
        val move = AtomicReference<Pair<Int, Int>>()
        val confirms = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            mergePdfState = MergePdfState.Configuring(
                listOf(
                    MergePdfItem(Uri.parse("content://test/first"), "first.pdf"),
                    MergePdfItem(Uri.parse("content://test/second"), "second.pdf"),
                ),
            ),
            onMoveMergeDocument = { from, to -> move.set(from to to) },
            onConfirmMerge = { confirms.incrementAndGet() },
        )

        composeRule.onNodeWithTag("merge_order_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("1. first.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("2. second.pdf").assertIsDisplayed()
        composeRule.onNodeWithTag("merge_move_up_1").performClick()
        assertEquals(1 to 0, move.get())
        composeRule.onNodeWithTag("merge_confirm").performClick()
        assertEquals(1, confirms.get())
    }

    @Test
    fun mergePdf_progressAndFailureAreSpecific() {
        val cancellations = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            mergePdfState = MergePdfState.Merging(documentCount = 3),
            onCancelMerge = { cancellations.incrementAndGet() },
        )

        composeRule.onNodeWithTag("operation_progress").assertIsDisplayed()
        composeRule.onNodeWithText("Merging 3 PDFs…").assertIsDisplayed()
        composeRule.onNodeWithTag("operation_cancel").performClick()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun mergePdf_invalidDocumentFailureIsActionable() {
        setContent(
            state = PdfOpenState.Idle,
            mergePdfState = MergePdfState.Failed(MergePdfFailure.InvalidDocument),
        )

        composeRule.onNodeWithTag("merge_pdf_error").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun splitPdf_buttonStartsSingleDocumentSelection() {
        val selections = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            onSplitPdf = { selections.incrementAndGet() },
        )

        composeRule.onNodeWithTag("split_pdf_button")
            .assertTextEquals("Split PDF")
            .performClick()
        assertEquals(1, selections.get())
    }

    @Test
    fun splitPdf_dialogBuildsCompleteCustomRangePlan() {
        val selected = AtomicReference<List<SplitPageRange>>()
        setContent(
            state = PdfOpenState.Idle,
            splitPdfState = SplitPdfState.Configuring(
                sourceUri = Uri.parse("content://test/split"),
                displayName = "source.pdf",
                pageCount = 6,
            ),
            onConfirmSplit = selected::set,
        )

        composeRule.onNodeWithTag("split_pdf_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("6 PDFs will be created.").assertIsDisplayed()
        composeRule.onNodeWithTag("split_mode_1").performClick()
        composeRule.onNodeWithTag("split_ranges_input").performTextInput("1-2, 3-6")
        composeRule.onNodeWithTag("split_confirm").performClick()

        assertEquals(
            listOf(SplitPageRange(0, 1), SplitPageRange(2, 5)),
            selected.get(),
        )
    }

    @Test
    fun splitPdf_incompleteRangesCannotContinue() {
        setContent(
            state = PdfOpenState.Idle,
            splitPdfState = SplitPdfState.Configuring(
                sourceUri = Uri.parse("content://test/split-invalid"),
                displayName = "source.pdf",
                pageCount = 6,
            ),
        )

        composeRule.onNodeWithTag("split_mode_1").performClick()
        composeRule.onNodeWithTag("split_ranges_input").performTextInput("1-2, 4-6")
        composeRule.onNodeWithTag("split_plan_error").assertIsDisplayed()
        composeRule.onNodeWithTag("split_confirm").assertIsNotEnabled()
    }

    @Test
    fun splitPdf_progressCanBeCancelled() {
        val cancellations = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            splitPdfState = SplitPdfState.Splitting(completedOutputs = 1, totalOutputs = 3),
            onCancelSplit = { cancellations.incrementAndGet() },
        )
        composeRule.onNodeWithText("Creating output 1 of 3…").assertIsDisplayed()
        composeRule.onNodeWithTag("operation_cancel").performClick()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun splitPdf_completionIsExplicit() {
        setContent(
            state = PdfOpenState.Idle,
            splitPdfState = SplitPdfState.Completed(outputCount = 3),
        )
        composeRule.onNodeWithTag("split_pdf_success").assertIsDisplayed()
    }

    @Test
    fun extractPages_buttonStartsSingleDocumentSelection() {
        val selections = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            onExtractPages = { selections.incrementAndGet() },
        )

        composeRule.onNodeWithTag("extract_pages_button")
            .assertTextEquals("Extract pages")
            .performClick()
        assertEquals(1, selections.get())
    }

    @Test
    fun extractPages_dialogReturnsExpandedOrderedSelection() {
        val selected = AtomicReference<IntArray>()
        setContent(
            state = PdfOpenState.Idle,
            extractPagesState = ExtractPagesState.Configuring(
                sourceUri = Uri.parse("content://test/extract"),
                displayName = "source.pdf",
                pageCount = 8,
            ),
            onConfirmPageExtraction = selected::set,
        )

        composeRule.onNodeWithTag("extract_pages_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("extract_pages_input").performTextInput("1, 3, 5-7")
        composeRule.onNodeWithText("5 pages selected.").assertIsDisplayed()
        composeRule.onNodeWithTag("extract_pages_confirm").performClick()
        assertArrayEquals(intArrayOf(0, 2, 4, 5, 6), selected.get())
    }

    @Test
    fun extractPages_reorderedSelectionCannotBeSaved() {
        setContent(
            state = PdfOpenState.Idle,
            extractPagesState = ExtractPagesState.Configuring(
                sourceUri = Uri.parse("content://test/extract-invalid"),
                displayName = "source.pdf",
                pageCount = 8,
            ),
        )

        composeRule.onNodeWithTag("extract_pages_input").performTextInput("5, 2")
        composeRule.onNodeWithTag("extract_pages_selection_error").assertIsDisplayed()
        composeRule.onNodeWithTag("extract_pages_confirm").assertIsNotEnabled()
    }

    @Test
    fun extractPages_progressCanBeCancelled() {
        val cancellations = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            extractPagesState = ExtractPagesState.Extracting(selectedPageCount = 4),
            onCancelPageExtraction = { cancellations.incrementAndGet() },
        )
        composeRule.onNodeWithText("Extracting 4 selected pages…").assertIsDisplayed()
        composeRule.onNodeWithTag("operation_cancel").performClick()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun extractPages_failureIsActionable() {
        setContent(
            state = PdfOpenState.Idle,
            extractPagesState = ExtractPagesState.Failed(ExtractPagesFailure.InvalidDocument),
        )
        composeRule.onNodeWithTag("extract_pages_error").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deletePages_buttonStartsSingleDocumentSelection() {
        val selections = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            onDeletePages = { selections.incrementAndGet() },
        )

        composeRule.onNodeWithTag("delete_pages_button")
            .assertTextEquals("Delete pages")
            .performClick()
        assertEquals(1, selections.get())
    }

    @Test
    fun deletePages_dialogReturnsPagesToRemoveAndRemainingCount() {
        val deleted = AtomicReference<IntArray>()
        setContent(
            state = PdfOpenState.Idle,
            deletePagesState = DeletePagesState.Configuring(
                sourceUri = Uri.parse("content://test/delete"),
                displayName = "source.pdf",
                pageCount = 5,
            ),
            onConfirmPageDeletion = deleted::set,
        )

        composeRule.onNodeWithTag("delete_pages_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_pages_input").performTextInput("2, 4")
        composeRule.onNodeWithText("Selected for removal: 2. Pages remaining: 3.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("delete_pages_confirm").performClick()
        assertArrayEquals(intArrayOf(1, 3), deleted.get())
    }

    @Test
    fun deletePages_cannotRemoveEveryPage() {
        setContent(
            state = PdfOpenState.Idle,
            deletePagesState = DeletePagesState.Configuring(
                sourceUri = Uri.parse("content://test/delete-all"),
                displayName = "source.pdf",
                pageCount = 3,
            ),
        )

        composeRule.onNodeWithTag("delete_pages_input").performTextInput("1-3")
        composeRule.onNodeWithTag("delete_pages_selection_error").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_pages_confirm").assertIsNotEnabled()
    }

    @Test
    fun deletePages_progressCanBeCancelled() {
        val cancellations = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            deletePagesState = DeletePagesState.Deleting(
                deletedPageCount = 2,
                remainingPageCount = 3,
            ),
            onCancelPageDeletion = { cancellations.incrementAndGet() },
        )
        composeRule.onNodeWithText("Removing selected pages (2); pages remaining: 3…")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("operation_cancel").performClick()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun deletePages_failureIsActionable() {
        setContent(
            state = PdfOpenState.Idle,
            deletePagesState = DeletePagesState.Failed(DeletePagesFailure.InvalidDocument),
        )
        composeRule.onNodeWithTag("delete_pages_error").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rearrangePages_buttonStartsSingleDocumentSelection() {
        val selections = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            onRearrangePages = { selections.incrementAndGet() },
        )

        composeRule.onNodeWithTag("rearrange_pages_button")
            .assertTextEquals("Rearrange pages")
            .performClick()
        assertEquals(1, selections.get())
    }

    @Test
    fun rearrangePages_dialogShowsOrderAndReturnsMoveResetAndConfirmActions() {
        val move = AtomicReference<Pair<Int, Int>>()
        val resets = AtomicInteger()
        val confirms = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            rearrangePagesState = RearrangePagesState.Configuring(
                sourceUri = Uri.parse("content://test/rearrange"),
                displayName = "source.pdf",
                pageCount = 3,
                pageOrder = listOf(2, 0, 1),
            ),
            onMoveRearrangedPage = { from, to -> move.set(from to to) },
            onResetRearrangedPages = { resets.incrementAndGet() },
            onConfirmPageRearrangement = { confirms.incrementAndGet() },
        )

        composeRule.onNodeWithTag("rearrange_pages_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Position 1: page 3").assertIsDisplayed()
        composeRule.onNodeWithTag("rearrange_page_2_down").performClick()
        assertEquals(0 to 1, move.get())
        composeRule.onNodeWithTag("rearrange_pages_reset").performClick()
        composeRule.onNodeWithTag("rearrange_pages_confirm").performClick()
        assertEquals(1, resets.get())
        assertEquals(1, confirms.get())
    }

    @Test
    fun rearrangePages_progressCanBeCancelled() {
        val cancellations = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            rearrangePagesState = RearrangePagesState.Rearranging(pageCount = 4),
            onCancelPageRearrangement = { cancellations.incrementAndGet() },
        )
        composeRule.onNodeWithText("Rearranging all 4 pages…").assertIsDisplayed()
        composeRule.onNodeWithTag("operation_cancel").performClick()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun rearrangePages_failureIsActionable() {
        setContent(
            state = PdfOpenState.Idle,
            rearrangePagesState = RearrangePagesState.Failed(
                RearrangePagesFailure.InvalidDocument,
            ),
        )
        composeRule.onNodeWithTag("rearrange_pages_error").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rotatePages_buttonStartsSingleDocumentSelection() {
        val selections = AtomicInteger()
        setContent(state = PdfOpenState.Idle, onRotatePages = { selections.incrementAndGet() })

        composeRule.onNodeWithTag("rotate_pages_button")
            .assertTextEquals("Rotate pages")
            .performClick()
        assertEquals(1, selections.get())
    }

    @Test
    fun rotatePages_dialogReturnsSelectedPagesAndRotation() {
        val selectedPages = AtomicReference<IntArray>()
        val selectedRotation = AtomicReference<PageRotation>()
        setContent(
            state = PdfOpenState.Idle,
            rotatePagesState = RotatePagesState.Configuring(
                sourceUri = Uri.parse("content://test/rotate"),
                displayName = "source.pdf",
                pageCount = 6,
            ),
            onConfirmPageRotation = { pages, rotation ->
                selectedPages.set(pages)
                selectedRotation.set(rotation)
            },
        )

        composeRule.onNodeWithTag("rotate_pages_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("rotate_pages_input").performTextInput("2, 4-5")
        composeRule.onNodeWithText("3 pages selected.").assertIsDisplayed()
        composeRule.onNodeWithTag("rotate_direction_2").performClick()
        composeRule.onNodeWithTag("rotate_pages_confirm").performClick()
        assertArrayEquals(intArrayOf(1, 3, 4), selectedPages.get())
        assertEquals(PageRotation.CounterClockwise90, selectedRotation.get())
    }

    @Test
    fun rotatePages_invalidSelectionCannotBeSaved() {
        setContent(
            state = PdfOpenState.Idle,
            rotatePagesState = RotatePagesState.Configuring(
                sourceUri = Uri.parse("content://test/rotate-invalid"),
                displayName = "source.pdf",
                pageCount = 3,
            ),
        )
        composeRule.onNodeWithTag("rotate_pages_input").performTextInput("3, 2")
        composeRule.onNodeWithTag("rotate_pages_selection_error").assertIsDisplayed()
        composeRule.onNodeWithTag("rotate_pages_confirm").assertIsNotEnabled()
    }

    @Test
    fun rotatePages_progressCanBeCancelled() {
        val cancellations = AtomicInteger()
        setContent(
            state = PdfOpenState.Idle,
            rotatePagesState = RotatePagesState.Rotating(selectedPageCount = 3),
            onCancelPageRotation = { cancellations.incrementAndGet() },
        )
        composeRule.onNodeWithText("Rotating 3 selected pages…").assertIsDisplayed()
        composeRule.onNodeWithTag("operation_cancel").performClick()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun rotatePages_failureIsActionable() {
        setContent(
            state = PdfOpenState.Idle,
            rotatePagesState = RotatePagesState.Failed(RotatePagesFailure.InvalidDocument),
        )
        composeRule.onNodeWithTag("rotate_pages_error").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
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

    @Test
    fun readerModeMenu_switchesBetweenAllSupportedModesLazily() {
        val renderCount = AtomicInteger()
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/reader-modes"),
                displayName = "modes.pdf",
                pageCount = 1_000,
            ),
            renderPage = { _, _ ->
                renderCount.incrementAndGet()
                PageRenderResult.Ready(
                    Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888),
                )
            },
        )

        composeRule.onNodeWithTag("reader_vertical").assertIsDisplayed()
        selectReaderMode("HorizontalContinuous")
        composeRule.onNodeWithTag("reader_horizontal").assertIsDisplayed()
        selectReaderMode("SinglePage")
        composeRule.onNodeWithTag("reader_single_page").assertIsDisplayed()
        composeRule.waitForIdle()

        assertTrue("Reader modes rendered too many pages", renderCount.get() < 30)
    }

    @Test
    fun fullscreen_autoHidesChromeAndTapRestoresIt() {
        setContent(
            PdfOpenState.Opened(
                uri = Uri.parse("content://test/fullscreen"),
                displayName = "fullscreen.pdf",
                pageCount = 1,
            ),
        )

        composeRule.onNodeWithTag("fullscreen_button").performClick()
        composeRule.onNodeWithTag("reader_fullscreen").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reader_top_bar").fetchSemanticsNodes().isEmpty()
        }

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("pdf_page_1").performTouchInput { click() }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.onNodeWithTag("reader_top_bar").assertIsDisplayed()
            composeRule.onNodeWithTag("fullscreen_button").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithTag("reader_fullscreen").assertCountEquals(0)
            composeRule.onNodeWithTag("reader_top_bar").assertIsDisplayed()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun nightAppearance_isReversibleAndDoesNotModifyThePageBitmap() {
        val sourceBitmap = Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.WHITE)
        }
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/night-appearance"),
                displayName = "night.pdf",
                pageCount = 1,
            ),
            renderPage = { _, _ -> PageRenderResult.Ready(sourceBitmap) },
        )

        val pageNode = composeRule.onNodeWithTag("pdf_page_1")
        val originalPixel = pageNode.captureToImage().toPixelMap()[50, 70]
        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("night_appearance_button").performClick()
        val nightPixel = pageNode.captureToImage().toPixelMap()[50, 70]

        assertTrue("Original white page was not light", originalPixel.luminance() > 0.9f)
        assertTrue("Night appearance did not darken the white page", nightPixel.luminance() < 0.1f)
        assertEquals(AndroidColor.WHITE, sourceBitmap.getPixel(50, 70))

        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("night_appearance_button").assertTextEquals("Original pages")
        composeRule.onNodeWithTag("night_appearance_button").performClick()
        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("night_appearance_button").assertTextEquals("Night pages")
    }

    @Test
    fun rememberedPage_isShownOnOpenAndNewPagesAreReported() {
        val reportedPage = AtomicInteger(-1)
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/remembered-page"),
                displayName = "remembered.pdf",
                pageCount = 5,
                initialPageIndex = 2,
            ),
            onPageChanged = reportedPage::set,
        )

        composeRule.onNodeWithText("Page 3 of 5").assertIsDisplayed()
        selectReaderMode("SinglePage")
        composeRule.onNodeWithTag("reader_single_page").performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 5_000) { reportedPage.get() == 3 }
        composeRule.onNodeWithText("Page 4 of 5").assertIsDisplayed()
    }

    @Test
    fun search_navigatesToAndHighlightsMatches() {
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/search"),
                displayName = "search.pdf",
                pageCount = 3,
            ),
            searchDocument = {
                PdfSearchResult.Matches(
                    listOf(
                        PdfSearchMatch(
                            pageIndex = 1,
                            bounds = listOf(RectF(0.1f, 0.2f, 0.4f, 0.3f)),
                        ),
                    ),
                )
            },
        )

        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("search_button").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput("needle")
        composeRule.onNodeWithTag("search_submit").performClick()

        composeRule.onNodeWithText("1 of 1").assertIsDisplayed()
        composeRule.onNodeWithText("Page 2 of 3").assertIsDisplayed()
        composeRule.onNodeWithTag("search_highlights_2").assertIsDisplayed()
        composeRule.onNodeWithTag("search_close").performClick()
        composeRule.onAllNodesWithTag("search_highlights_2").assertCountEquals(0)
    }

    @Test
    fun bookmarks_listNavigatesAndCurrentPageCanBeRemoved() {
        val toggledPage = AtomicInteger(-1)
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/bookmarks"),
                displayName = "bookmarks.pdf",
                pageCount = 3,
                bookmarkedPages = setOf(1),
            ),
            onToggleBookmark = toggledPage::set,
        )

        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("bookmarks_button").performClick()
        composeRule.onNodeWithTag("bookmarks_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("bookmark_page_2").performClick()
        composeRule.onNodeWithText("Page 2 of 3").assertIsDisplayed()

        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("toggle_bookmark_button")
            .assertTextEquals("Remove bookmark from page 2")
            .performClick()
        assertEquals(1, toggledPage.get())
    }

    @Test
    fun tableOfContents_preservesHierarchyAndNavigatesToPage() {
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/table-of-contents"),
                displayName = "outline.pdf",
                pageCount = 3,
            ),
            loadTableOfContents = {
                PdfTableOfContentsResult.Entries(
                    listOf(
                        PdfOutlineEntry("Chapter One", pageIndex = 0, depth = 0),
                        PdfOutlineEntry("Section A", pageIndex = 2, depth = 1),
                    ),
                )
            },
        )

        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("table_of_contents_button").performClick()
        composeRule.onNodeWithTag("table_of_contents_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Section A").assertIsDisplayed()
        composeRule.onNodeWithTag("table_of_contents_page_3").performClick()
        composeRule.onNodeWithText("Page 3 of 3").assertIsDisplayed()
    }

    @Test
    fun tableOfContents_explainsWhenDocumentHasNoOutline() {
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/no-table-of-contents"),
                displayName = "plain.pdf",
                pageCount = 1,
            ),
            loadTableOfContents = { PdfTableOfContentsResult.Empty },
        )

        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("table_of_contents_button").performClick()
        composeRule.onNodeWithText("This PDF does not contain a table of contents.")
            .assertIsDisplayed()
    }

    @Test
    fun pdfHealth_showsVerifiedDocumentProperties() {
        setContent(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://test/health"),
                displayName = "healthy.pdf",
                pageCount = 2,
            ),
            inspectHealth = {
                PdfHealthResult.Healthy(
                    PdfHealthReport(
                        pageCount = 2,
                        fileSizeBytes = 2048,
                        hasSearchableText = true,
                        hasTableOfContents = false,
                        title = "Sample report",
                        author = null,
                    ),
                )
            },
        )

        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("pdf_health_button").performClick()
        composeRule.onNodeWithTag("pdf_health_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("All 2 pages are readable").assertIsDisplayed()
        composeRule.onNodeWithText("Searchable text").assertIsDisplayed()
        composeRule.onNodeWithText("Sample report").assertIsDisplayed()
    }

    private fun selectReaderMode(modeName: String) {
        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("reader_mode_$modeName").performClick()
    }

    private fun setContent(
        state: PdfOpenState,
        renderPage: suspend (Int, Int) -> PageRenderResult = { _, _ ->
            PageRenderResult.Ready(
                Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888),
            )
        },
        onPageChanged: (Int) -> Unit = {},
        searchDocument: suspend (String) -> PdfSearchResult = { PdfSearchResult.Failed },
        onToggleBookmark: (Int) -> Unit = {},
        loadTableOfContents: suspend () -> PdfTableOfContentsResult = {
            PdfTableOfContentsResult.Failed
        },
        inspectHealth: suspend () -> PdfHealthResult = { PdfHealthResult.Failed },
        imagesToPdfState: ImagesToPdfState = ImagesToPdfState.Idle,
        onImagesToPdf: () -> Unit = {},
        onConfirmImagesPdfLayout: (ImagePdfLayout) -> Unit = {},
        mergePdfState: MergePdfState = MergePdfState.Idle,
        onMergePdfs: () -> Unit = {},
        onMoveMergeDocument: (Int, Int) -> Unit = { _, _ -> },
        onConfirmMerge: () -> Unit = {},
        onCancelMerge: () -> Unit = {},
        splitPdfState: SplitPdfState = SplitPdfState.Idle,
        onSplitPdf: () -> Unit = {},
        onConfirmSplit: (List<SplitPageRange>) -> Unit = {},
        onCancelSplit: () -> Unit = {},
        extractPagesState: ExtractPagesState = ExtractPagesState.Idle,
        onExtractPages: () -> Unit = {},
        onConfirmPageExtraction: (IntArray) -> Unit = {},
        onCancelPageExtraction: () -> Unit = {},
        deletePagesState: DeletePagesState = DeletePagesState.Idle,
        onDeletePages: () -> Unit = {},
        onConfirmPageDeletion: (IntArray) -> Unit = {},
        onCancelPageDeletion: () -> Unit = {},
        rearrangePagesState: RearrangePagesState = RearrangePagesState.Idle,
        onRearrangePages: () -> Unit = {},
        onMoveRearrangedPage: (Int, Int) -> Unit = { _, _ -> },
        onResetRearrangedPages: () -> Unit = {},
        onConfirmPageRearrangement: () -> Unit = {},
        onCancelPageRearrangement: () -> Unit = {},
        rotatePagesState: RotatePagesState = RotatePagesState.Idle,
        onRotatePages: () -> Unit = {},
        onConfirmPageRotation: (IntArray, PageRotation) -> Unit = { _, _ -> },
        onCancelPageRotation: () -> Unit = {},
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = state,
                    onOpenPdf = {},
                    renderPage = renderPage,
                    onPageChanged = onPageChanged,
                    searchDocument = searchDocument,
                    onToggleBookmark = onToggleBookmark,
                    loadTableOfContents = loadTableOfContents,
                    inspectHealth = inspectHealth,
                    imagesToPdfState = imagesToPdfState,
                    onImagesToPdf = onImagesToPdf,
                    onConfirmImagesPdfLayout = onConfirmImagesPdfLayout,
                    mergePdfState = mergePdfState,
                    onMergePdfs = onMergePdfs,
                    onMoveMergeDocument = onMoveMergeDocument,
                    onConfirmMerge = onConfirmMerge,
                    onCancelMerge = onCancelMerge,
                    splitPdfState = splitPdfState,
                    onSplitPdf = onSplitPdf,
                    onConfirmSplit = onConfirmSplit,
                    onCancelSplit = onCancelSplit,
                    extractPagesState = extractPagesState,
                    onExtractPages = onExtractPages,
                    onConfirmPageExtraction = onConfirmPageExtraction,
                    onCancelPageExtraction = onCancelPageExtraction,
                    deletePagesState = deletePagesState,
                    onDeletePages = onDeletePages,
                    onConfirmPageDeletion = onConfirmPageDeletion,
                    onCancelPageDeletion = onCancelPageDeletion,
                    rearrangePagesState = rearrangePagesState,
                    onRearrangePages = onRearrangePages,
                    onMoveRearrangedPage = onMoveRearrangedPage,
                    onResetRearrangedPages = onResetRearrangedPages,
                    onConfirmPageRearrangement = onConfirmPageRearrangement,
                    onCancelPageRearrangement = onCancelPageRearrangement,
                    rotatePagesState = rotatePagesState,
                    onRotatePages = onRotatePages,
                    onConfirmPageRotation = onConfirmPageRotation,
                    onCancelPageRotation = onCancelPageRotation,
                )
            }
        }
    }
}
