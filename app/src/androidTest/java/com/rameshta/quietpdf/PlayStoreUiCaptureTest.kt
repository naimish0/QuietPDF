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
                RecentPdf(Uri.parse("content://capture/quarterly"), fixtureText("quarterly"), 8, 1_784_313_000_000L),
                RecentPdf(Uri.parse("content://capture/travel"), fixtureText("travel"), 4, 1_784_226_600_000L),
                RecentPdf(Uri.parse("content://capture/notes"), fixtureText("notes"), 12, 1_784_140_200_000L),
            ),
            favoritePdfs = listOf(
                FavoritePdf(Uri.parse("content://capture/travel"), fixtureText("travel"), 4, 1_784_226_600_000L),
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
                displayName = fixtureText("research"),
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
        composeRule.onNodeWithTag("search_query").performTextInput(fixtureText("privacy_query"))
        composeRule.onNodeWithTag("search_submit").performClick()
        capture("03-reader-search-ui.png")
    }

    @Test
    fun captureCompression() {
        setApp(
            compressPdfState = CompressPdfState.Configuring(
                sourceUri = Uri.parse("content://capture/catalogue"),
                displayName = fixtureText("catalogue"),
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
                    MergePdfItem(Uri.parse("content://capture/brief"), fixtureText("brief")),
                    MergePdfItem(Uri.parse("content://capture/budget"), fixtureText("budget")),
                    MergePdfItem(Uri.parse("content://capture/timeline"), fixtureText("timeline")),
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
                displayName = fixtureText("combined"),
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

    @Test
    fun captureSettings() {
        setApp()
        composeRule.onNodeWithTag("smart_home_settings_action").performClick()
        capture("09-settings-ui.png")
    }

    @Test
    fun captureLanguageChooser() {
        setApp()
        composeRule.onNodeWithTag("smart_home_settings_action").performClick()
        composeRule.onNodeWithTag("settings_language_card").performClick()
        capture(
            "10-language-chooser-ui.png",
            composeRule.onNodeWithTag("settings_language_dialog_container"),
        )
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
        canvas.drawText(fixtureText("research_title"), 72f, 112f, paint)
        paint.color = Color.rgb(71, 85, 105)
        paint.textSize = 26f
        paint.isFakeBoldText = false
        canvas.drawText("Northstar Studio · 2026", 72f, 158f, paint)
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText(fixtureText("research_heading"), 72f, 246f, paint)
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
        canvas.drawText(fixtureText("receipt_title"), 150f, 214f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(148, 163, 184)
        for (index in 0 until 10) {
            val y = 278f + index * 54f
            canvas.drawRoundRect(150f, y, if (index % 3 == 0) 580f else 650f, y + 14f, 7f, 7f, paint)
        }
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 30f
        paint.isFakeBoldText = true
        canvas.drawText(fixtureText("receipt_total"), 400f, 900f, paint)
        return bitmap
    }

    private fun fixtureText(key: String): String {
        val locale = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.configuration.locales[0]
        fun t(
            en: String, de: String, fr: String, ja: String, hi: String,
            ru: String, es: String, ptPt: String, ptBr: String, it: String,
            id: String, ar: String, ko: String, ur: String,
        ) = mapOf(
            "en" to en, "de" to de, "fr" to fr, "ja" to ja, "hi" to hi,
            "ru" to ru, "es" to es, "pt-PT" to ptPt, "pt-BR" to ptBr,
            "it" to it, "id" to id, "ar" to ar, "ko" to ko, "ur" to ur,
        )
        val translations = mapOf(
            "quarterly" to t("Quarterly Summary.pdf","Quartalsübersicht.pdf","Synthèse trimestrielle.pdf","四半期サマリー.pdf","तिमाही सारांश.pdf","Квартальный обзор.pdf","Resumen trimestral.pdf","Resumo trimestral.pdf","Resumo trimestral.pdf","Riepilogo trimestrale.pdf","Ringkasan Triwulanan.pdf","الملخص ربع السنوي.pdf","분기 요약.pdf","سہ ماہی خلاصہ.pdf"),
            "travel" to t("Travel Plan.pdf","Reiseplan.pdf","Plan de voyage.pdf","旅行プラン.pdf","यात्रा योजना.pdf","План путешествия.pdf","Plan de viaje.pdf","Plano de viagem.pdf","Plano de viagem.pdf","Piano di viaggio.pdf","Rencana Perjalanan.pdf","خطة السفر.pdf","여행 계획.pdf","سفری منصوبہ.pdf"),
            "notes" to t("Project Notes.pdf","Projektnotizen.pdf","Notes de projet.pdf","プロジェクトノート.pdf","प्रोजेक्ट नोट्स.pdf","Заметки проекта.pdf","Notas del proyecto.pdf","Notas do projeto.pdf","Notas do projeto.pdf","Note del progetto.pdf","Catatan Proyek.pdf","ملاحظات المشروع.pdf","프로젝트 노트.pdf","پروجیکٹ نوٹس.pdf"),
            "research" to t("Research Report.pdf","Forschungsbericht.pdf","Rapport de recherche.pdf","調査レポート.pdf","शोध रिपोर्ट.pdf","Исследовательский отчет.pdf","Informe de investigación.pdf","Relatório de investigação.pdf","Relatório de pesquisa.pdf","Rapporto di ricerca.pdf","Laporan Riset.pdf","تقرير البحث.pdf","연구 보고서.pdf","تحقیقی رپورٹ.pdf"),
            "research_title" to t("Research Report","Forschungsbericht","Rapport de recherche","調査レポート","शोध रिपोर्ट","Исследовательский отчет","Informe de investigación","Relatório de investigação","Relatório de pesquisa","Rapporto di ricerca","Laporan Riset","تقرير البحث","연구 보고서","تحقیقی رپورٹ"),
            "research_heading" to t("Privacy-first document workflows","Datenschutzorientierte Abläufe","Flux de documents confidentiels","プライバシー重視の文書処理","निजता-केंद्रित दस्तावेज़ प्रक्रिया","Конфиденциальная обработка документов","Flujos de documentos privados","Fluxos de documentos privados","Fluxos de documentos privados","Flussi di documenti privati","Alur dokumen yang mengutamakan privasi","مسارات مستندات تراعي الخصوصية","개인정보 보호 중심 문서 작업","رازداری پر مبنی دستاویزی طریقۂ کار"),
            "privacy_query" to t("privacy","Datenschutz","confidentialité","プライバシー","निजता","конфиденциальность","privacidad","privacidade","privacidade","privacy","privasi","الخصوصية","개인정보 보호","رازداری"),
            "catalogue" to t("Product Catalogue.pdf","Produktkatalog.pdf","Catalogue produits.pdf","製品カタログ.pdf","उत्पाद कैटलॉग.pdf","Каталог продукции.pdf","Catálogo de productos.pdf","Catálogo de produtos.pdf","Catálogo de produtos.pdf","Catalogo prodotti.pdf","Katalog Produk.pdf","كتالوج المنتجات.pdf","제품 카탈로그.pdf","مصنوعات کی فہرست.pdf"),
            "brief" to t("Project Brief.pdf","Projektübersicht.pdf","Brief projet.pdf","プロジェクト概要.pdf","प्रोजेक्ट ब्रीफ़.pdf","Описание проекта.pdf","Resumen del proyecto.pdf","Resumo do projeto.pdf","Resumo do projeto.pdf","Sintesi del progetto.pdf","Ringkasan Proyek.pdf","ملخص المشروع.pdf","프로젝트 개요.pdf","پروجیکٹ بریف.pdf"),
            "budget" to t("Budget Overview.pdf","Budgetübersicht.pdf","Aperçu du budget.pdf","予算概要.pdf","बजट अवलोकन.pdf","Обзор бюджета.pdf","Resumen del presupuesto.pdf","Visão geral do orçamento.pdf","Visão geral do orçamento.pdf","Panoramica del budget.pdf","Ringkasan Anggaran.pdf","نظرة عامة على الميزانية.pdf","예산 개요.pdf","بجٹ کا جائزہ.pdf"),
            "timeline" to t("Delivery Timeline.pdf","Lieferzeitplan.pdf","Calendrier de livraison.pdf","納品スケジュール.pdf","डिलीवरी समयरेखा.pdf","График поставки.pdf","Calendario de entrega.pdf","Calendário de entrega.pdf","Cronograma de entrega.pdf","Tempistica di consegna.pdf","Jadwal Pengiriman.pdf","الجدول الزمني للتسليم.pdf","배송 일정.pdf","ترسیل کا شیڈول.pdf"),
            "combined" to t("Combined Project.pdf","Kombiniertes Projekt.pdf","Projet combiné.pdf","統合プロジェクト.pdf","संयुक्त प्रोजेक्ट.pdf","Объединенный проект.pdf","Proyecto combinado.pdf","Projeto combinado.pdf","Projeto combinado.pdf","Progetto combinato.pdf","Proyek Gabungan.pdf","المشروع المدمج.pdf","통합 프로젝트.pdf","مشترکہ پروجیکٹ.pdf"),
            "receipt_title" to t("NORTHSTAR RECEIPT","NORTHSTAR BELEG","REÇU NORTHSTAR","NORTHSTAR レシート","नॉर्थस्टार रसीद","ЧЕК NORTHSTAR","RECIBO NORTHSTAR","RECIBO NORTHSTAR","RECIBO NORTHSTAR","RICEVUTA NORTHSTAR","STRUK NORTHSTAR","إيصال NORTHSTAR","NORTHSTAR 영수증","NORTHSTAR رسید"),
            "receipt_total" to t("TOTAL  84.60","SUMME  84,60","TOTAL  84,60","合計  84.60","कुल  84.60","ИТОГО  84,60","TOTAL  84,60","TOTAL  84,60","TOTAL  84,60","TOTALE  84,60","TOTAL  84,60","الإجمالي  84.60","합계  84.60","کل  84.60"),
        )
        val values = translations.getValue(key)
        return values[locale.toLanguageTag()] ?: values[locale.language] ?: values.getValue("en")
    }
}
