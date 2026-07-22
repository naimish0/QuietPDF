package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.CompressPdfAnalysis
import com.rameshta.quietpdf.pdf.CompressPdfState
import com.rameshta.quietpdf.pdf.CompressibleImage
import com.rameshta.quietpdf.pdf.FavoritePdf
import com.rameshta.quietpdf.pdf.ImagesToPdfState
import com.rameshta.quietpdf.pdf.MergePdfItem
import com.rameshta.quietpdf.pdf.MergePdfState
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.PdfSearchMatch
import com.rameshta.quietpdf.pdf.PdfSearchResult
import com.rameshta.quietpdf.pdf.RearrangePagesState
import com.rameshta.quietpdf.pdf.RecentPdf
import com.rameshta.quietpdf.pdf.ScannerCapturePreview
import com.rameshta.quietpdf.pdf.ScannerCaptureState
import com.rameshta.quietpdf.pdf.ScannerCropPoint
import com.rameshta.quietpdf.pdf.ScannerCropSelection
import com.rameshta.quietpdf.pdf.ScannerEnhancementSettings
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Capture-only renderer for Play Store assets.
 *
 * It hosts the real production Compose UI with deterministic fixture state. Ad loading is explicitly
 * disabled and no ad composable is supplied. The test is isolated to androidTest and cannot change
 * production advertising behavior.
 */
@RunWith(AndroidJUnit4::class)
class PlayStoreUiCaptureTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun captureHome() {
        setApp(
            recentPdfs = listOf(
                RecentPdf(Uri.parse("content://capture/quarterly"), "Quarterly Summary.pdf", 8, 1_784_313_000_000L),
                RecentPdf(Uri.parse("content://capture/travel"), "Travel Plan.pdf", 4, 1_784_226_600_000L),
                RecentPdf(Uri.parse("content://capture/notes"), "Project Notes.pdf", 12, 1_784_140_200_000L),
            ),
            favoritePdfs = listOf(
                FavoritePdf(Uri.parse("content://capture/travel"), "Travel Plan.pdf", 4, 1_784_226_600_000L),
            ),
        )
        capture("01-home-ui.png")
    }

    @Test
    fun captureScannerReview() {
        val before = receiptBitmap()
        val after = receiptBitmap(clean = true)
        setApp(
            scannerCaptureState = ScannerCaptureState.Review(
                preview = ScannerCapturePreview(
                    bitmap = before,
                    sourceWidth = before.width,
                    sourceHeight = before.height,
                    suggestedCrop = ScannerCropSelection(
                        topLeft = ScannerCropPoint(.12f, .08f),
                        topRight = ScannerCropPoint(.91f, .13f),
                        bottomRight = ScannerCropPoint(.95f, .92f),
                        bottomLeft = ScannerCropPoint(.08f, .88f),
                    ),
                    automaticCropDetected = true,
                ),
                enhancedPreview = after,
                enhancement = ScannerEnhancementSettings(),
                pageIndex = 0,
                pageCount = 2,
            ),
        )
        capture("02-scanner-review-ui.png")
    }

    @Test
    fun captureReaderSearch() {
        setApp(
            state = PdfOpenState.Opened(
                uri = Uri.parse("content://capture/research"),
                displayName = "Research Report.pdf",
                pageCount = 18,
                initialPageIndex = 6,
                bookmarkedPages = setOf(1, 6, 11),
                isFavorite = true,
            ),
            renderPage = { _, _ -> PageRenderResult.Ready(researchPageBitmap()) },
            searchDocument = {
                PdfSearchResult.Matches(
                    listOf(
                        PdfSearchMatch(pageIndex = 6, bounds = listOf(android.graphics.RectF(.18f, .23f, .45f, .28f))),
                        PdfSearchMatch(pageIndex = 8, bounds = listOf(android.graphics.RectF(.2f, .4f, .5f, .46f))),
                        PdfSearchMatch(pageIndex = 11, bounds = listOf(android.graphics.RectF(.22f, .55f, .62f, .61f))),
                    ),
                )
            },
        )
        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("search_button").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput("privacy")
        composeRule.onNodeWithTag("search_submit").performClick()
        capture("03-reader-search-ui.png")
    }

    @Test
    fun captureCompression() {
        setApp(
            compressPdfState = CompressPdfState.Configuring(
                sourceUri = Uri.parse("content://capture/catalogue"),
                displayName = "Product Catalogue.pdf",
                analysis = CompressPdfAnalysis(
                    pageCount = 12,
                    originalSizeBytes = 8_400_000,
                    compressibleImages = listOf(
                        CompressibleImage(6_900_000, 3600, 2400),
                    ),
                ),
            ),
        )
        capture("04-compression-ui.png", composeRule.onNodeWithTag("compress_pdf_dialog"))
    }

    @Test
    fun captureImagesToPdf() {
        setApp(imagesToPdfState = ImagesToPdfState.Configuring(imageCount = 4))
        capture("05-images-to-pdf-ui.png", composeRule.onNodeWithTag("images_pdf_layout_dialog"))
    }

    @Test
    fun captureMergeAndRearrange() {
        setApp(
            mergePdfState = MergePdfState.Configuring(
                listOf(
                    MergePdfItem(Uri.parse("content://capture/brief"), "Project Brief.pdf"),
                    MergePdfItem(Uri.parse("content://capture/budget"), "Budget Overview.pdf"),
                    MergePdfItem(Uri.parse("content://capture/timeline"), "Delivery Timeline.pdf"),
                ),
            ),
        )
        capture("06-merge-ui.png", composeRule.onNodeWithTag("merge_order_dialog"))
    }

    @Test
    fun captureRearrange() {
        setApp(
            rearrangePagesState = RearrangePagesState.Configuring(
                sourceUri = Uri.parse("content://capture/project"),
                displayName = "Combined Project.pdf",
                pageCount = 4,
                pageOrder = listOf(2, 0, 3, 1),
            ),
        )
        capture("06b-rearrange-ui.png", composeRule.onNodeWithTag("rearrange_pages_dialog"))
    }

    @Test
    fun capturePrivacyPanel() {
        setApp()
        capture("07-privacy-ui.png", composeRule.onNodeWithTag("smart_home_privacy"))
    }

    @Test
    fun captureResult() {
        setApp(
            compressPdfState = CompressPdfState.Completed(
                outputUri = Uri.parse("content://capture/travel-itinerary"),
                originalSizeBytes = 2_000_000,
                outputSizeBytes = 1_000_000,
                recompressedImageCount = 3,
            ),
        )
        capture("08-result-ui.png")
    }

    private fun setApp(
        state: PdfOpenState = PdfOpenState.Idle,
        recentPdfs: List<RecentPdf> = emptyList(),
        favoritePdfs: List<FavoritePdf> = emptyList(),
        renderPage: suspend (Int, Int) -> PageRenderResult = { _, _ ->
            PageRenderResult.Ready(researchPageBitmap())
        },
        searchDocument: suspend (String) -> PdfSearchResult = { PdfSearchResult.Failed },
        imagesToPdfState: ImagesToPdfState = ImagesToPdfState.Idle,
        mergePdfState: MergePdfState = MergePdfState.Idle,
        rearrangePagesState: RearrangePagesState = RearrangePagesState.Idle,
        compressPdfState: CompressPdfState = CompressPdfState.Idle,
        scannerCaptureState: ScannerCaptureState = ScannerCaptureState.Idle,
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = state,
                    recentPdfs = recentPdfs,
                    favoritePdfs = favoritePdfs,
                    legacyHomeSections = false,
                    adsCanLoad = false,
                    homeBannerContent = null,
                    onOpenPdf = {},
                    renderPage = renderPage,
                    searchDocument = searchDocument,
                    imagesToPdfState = imagesToPdfState,
                    mergePdfState = mergePdfState,
                    rearrangePagesState = rearrangePagesState,
                    compressPdfState = compressPdfState,
                    scannerCaptureState = scannerCaptureState,
                )
            }
        }
    }

    private fun capture(name: String, node: SemanticsNodeInteraction = composeRule.onRoot()) {
        composeRule.waitForIdle()
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDirectory = File(targetContext.getExternalFilesDir(null), "play-store-ui-captures")
        check(outputDirectory.exists() || outputDirectory.mkdirs())
        File(outputDirectory, name).outputStream().use { output ->
            check(node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun researchPageBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(900, 1240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)
        paint.color = Color.rgb(37, 99, 235)
        canvas.drawRect(0f, 0f, 900f, 28f, paint)
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 58f
        paint.isFakeBoldText = true
        canvas.drawText("Research Report", 72f, 112f, paint)
        paint.color = Color.rgb(71, 85, 105)
        paint.textSize = 26f
        paint.isFakeBoldText = false
        canvas.drawText("Northstar Studio · 2026", 72f, 158f, paint)
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText("Privacy-first document workflows", 72f, 246f, paint)
        paint.isFakeBoldText = false
        val lineWidths = listOf(690f, 610f, 730f, 540f)
        paint.color = Color.rgb(203, 213, 225)
        lineWidths.forEachIndexed { index, width ->
            canvas.drawRoundRect(72f, 292f + index * 38f, 72f + width, 308f + index * 38f, 8f, 8f, paint)
        }
        paint.color = Color.rgb(37, 99, 235)
        paint.strokeWidth = 12f
        paint.style = Paint.Style.STROKE
        val points = floatArrayOf(100f, 740f, 230f, 690f, 360f, 720f, 490f, 610f, 620f, 640f, 760f, 520f)
        for (index in 0 until points.size - 2 step 2) {
            canvas.drawLine(points[index], points[index + 1], points[index + 2], points[index + 3], paint)
        }
        paint.style = Paint.Style.FILL
        listOf(190f, 320f, 250f, 390f).forEachIndexed { index, height ->
            paint.color = if (index == 2) Color.rgb(37, 99, 235) else Color.rgb(191, 219, 254)
            canvas.drawRoundRect(110f + index * 175f, 1120f - height, 220f + index * 175f, 1120f, 16f, 16f, paint)
        }
        return bitmap
    }

    private fun receiptBitmap(clean: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(820, 1040, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(if (clean) Color.WHITE else Color.rgb(218, 214, 206))
        paint.color = Color.WHITE
        canvas.drawRoundRect(110f, 70f, 720f, 970f, 12f, 12f, paint)
        paint.color = Color.rgb(37, 99, 235)
        canvas.drawRect(150f, 120f, 680f, 150f, paint)
        paint.color = Color.rgb(71, 85, 105)
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText("NORTHSTAR RECEIPT", 150f, 214f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(148, 163, 184)
        for (index in 0 until 10) {
            val y = 278f + index * 54f
            canvas.drawRoundRect(150f, y, if (index % 3 == 0) 580f else 650f, y + 14f, 7f, 7f, paint)
        }
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 30f
        paint.isFakeBoldText = true
        canvas.drawText("TOTAL  84.60", 400f, 900f, paint)
        return bitmap
    }
}
