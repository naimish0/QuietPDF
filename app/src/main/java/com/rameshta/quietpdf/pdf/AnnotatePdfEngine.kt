package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDAppearanceContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

data class AnnotatePdfAnalysis(val pageCount: Int)

data class AnnotationPoint(val x: Float, val y: Float) {
    fun isValid(): Boolean = x in 0f..1f && y in 0f..1f
}

sealed interface PdfAnnotationItem {
    val pageIndex: Int

    data class FreeText(
        override val pageIndex: Int,
        val text: String,
        val centerX: Float = 0.5f,
        val centerY: Float = 0.75f,
        val widthFraction: Float = 0.5f,
        val fontSize: Float = 18f,
    ) : PdfAnnotationItem

    data class Ink(
        override val pageIndex: Int,
        val strokes: List<List<AnnotationPoint>>,
        val lineWidth: Float = 3f,
    ) : PdfAnnotationItem

    data class Highlight(
        override val pageIndex: Int,
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val widthFraction: Float = 0.45f,
        val heightFraction: Float = 0.06f,
    ) : PdfAnnotationItem
}

sealed interface AnnotatePdfAnalysisResult {
    data class Ready(val analysis: AnnotatePdfAnalysis) : AnnotatePdfAnalysisResult
    data object PasswordProtected : AnnotatePdfAnalysisResult
    data object InvalidDocument : AnnotatePdfAnalysisResult
    data object PermissionDenied : AnnotatePdfAnalysisResult
    data object InsufficientMemory : AnnotatePdfAnalysisResult
}

sealed interface AnnotatePdfPreviewResult {
    data class Ready(val bitmap: Bitmap) : AnnotatePdfPreviewResult
    data object InvalidAnnotations : AnnotatePdfPreviewResult
    data object Failed : AnnotatePdfPreviewResult
}

sealed interface AnnotatePdfResult {
    data class Success(val pageCount: Int, val annotationCount: Int) : AnnotatePdfResult
    data object PasswordProtected : AnnotatePdfResult
    data object InvalidDocument : AnnotatePdfResult
    data object InvalidAnnotations : AnnotatePdfResult
    data object PermissionDenied : AnnotatePdfResult
    data object InsufficientMemory : AnnotatePdfResult
    data object Failed : AnnotatePdfResult
}

class AnnotatePdfEngine(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    suspend fun analyze(sourceUri: Uri): AnnotatePdfAnalysisResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    when {
                        document.isEncrypted -> AnnotatePdfAnalysisResult.PasswordProtected
                        document.numberOfPages <= 0 -> AnnotatePdfAnalysisResult.InvalidDocument
                        else -> AnnotatePdfAnalysisResult.Ready(AnnotatePdfAnalysis(document.numberOfPages))
                    }
                }
            } ?: AnnotatePdfAnalysisResult.InvalidDocument
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            AnnotatePdfAnalysisResult.PasswordProtected
        } catch (_: SecurityException) {
            AnnotatePdfAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            AnnotatePdfAnalysisResult.InsufficientMemory
        } catch (_: Exception) {
            AnnotatePdfAnalysisResult.InvalidDocument
        }
    }

    suspend fun preview(
        sourceUri: Uri,
        annotations: List<PdfAnnotationItem>,
        pageIndex: Int,
        targetWidth: Int,
    ): AnnotatePdfPreviewResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (pageIndex !in 0 until document.numberOfPages ||
                        !annotationsAreValid(annotations, document.numberOfPages)
                    ) return@withContext AnnotatePdfPreviewResult.InvalidAnnotations
                    addAnnotations(document, annotations)
                    val page = document.getPage(pageIndex)
                    val scale = targetWidth.coerceIn(240, 1200) / visualWidth(page).coerceAtLeast(1f)
                    AnnotatePdfPreviewResult.Ready(
                        PDFRenderer(document).renderImage(pageIndex, scale.coerceIn(0.25f, 3f)),
                    )
                }
            } ?: AnnotatePdfPreviewResult.Failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OutOfMemoryError) {
            AnnotatePdfPreviewResult.Failed
        } catch (_: Exception) {
            AnnotatePdfPreviewResult.Failed
        }
    }

    suspend fun annotate(
        sourceUri: Uri,
        outputUri: Uri,
        annotations: List<PdfAnnotationItem>,
        expectedPageCount: Int,
    ): AnnotatePdfResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri || !annotationsAreValid(annotations, expectedPageCount)) {
            if (sourceUri != outputUri) cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext AnnotatePdfResult.InvalidAnnotations
        }
        val temporary = try {
            File.createTempFile("annotations-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext AnnotatePdfResult.Failed
        }
        var committed = false
        var expectedAnnotationCount = -1
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) return@withContext AnnotatePdfResult.PasswordProtected
                    if (document.numberOfPages != expectedPageCount) {
                        return@withContext AnnotatePdfResult.InvalidDocument
                    }
                    coroutineContext.ensureActive()
                    addAnnotations(document, annotations)
                    expectedAnnotationCount = document.pages.sumOf { it.annotations.size }
                    document.save(temporary)
                }
            } ?: return@withContext AnnotatePdfResult.InvalidDocument
            if (!validatesOutput(temporary, expectedPageCount, expectedAnnotationCount)) {
                return@withContext AnnotatePdfResult.Failed
            }
            resolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { it.copyTo(output) }
            } ?: return@withContext AnnotatePdfResult.Failed
            if (!validatesOutput(outputUri, expectedPageCount, expectedAnnotationCount)) {
                return@withContext AnnotatePdfResult.Failed
            }
            committed = true
            AnnotatePdfResult.Success(expectedPageCount, annotations.size)
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            AnnotatePdfResult.PasswordProtected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            AnnotatePdfResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            AnnotatePdfResult.InsufficientMemory
        } catch (_: Exception) {
            AnnotatePdfResult.Failed
        } finally {
            temporary.delete()
            if (!committed) cleanupNewPdfOutput(appContext, resolver, outputUri)
        }
    }

    private fun annotationsAreValid(items: List<PdfAnnotationItem>, pageCount: Int): Boolean =
        items.isNotEmpty() && items.size <= MAX_ANNOTATIONS && items.all { item ->
            item.pageIndex in 0 until pageCount && when (item) {
                is PdfAnnotationItem.FreeText -> item.text.isNotBlank() && item.text.length <= MAX_TEXT_LENGTH &&
                    item.centerX in 0f..1f && item.centerY in 0f..1f &&
                    item.widthFraction in 0.15f..0.9f && item.fontSize in 8f..40f
                is PdfAnnotationItem.Ink -> item.strokes.isNotEmpty() && item.strokes.size <= MAX_STROKES &&
                    item.lineWidth in 1f..12f && item.strokes.all { stroke ->
                        stroke.size in 2..MAX_POINTS_PER_STROKE && stroke.all(AnnotationPoint::isValid)
                    }
                is PdfAnnotationItem.Highlight -> item.centerX in 0f..1f && item.centerY in 0f..1f &&
                    item.widthFraction in 0.05f..0.95f && item.heightFraction in 0.02f..0.3f
            }
        }

    private fun addAnnotations(document: PDDocument, items: List<PdfAnnotationItem>) {
        val font = if (items.any { it is PdfAnnotationItem.FreeText }) loadFont(document) else null
        items.forEach { item ->
            val page = document.getPage(item.pageIndex)
            val annotation = when (item) {
                is PdfAnnotationItem.FreeText -> createFreeText(document, page, item, checkNotNull(font))
                is PdfAnnotationItem.Ink -> createInk(document, page, item)
                is PdfAnnotationItem.Highlight -> createHighlight(document, page, item)
            }
            annotation.setPage(page)
            annotation.isPrinted = true
            page.annotations.add(annotation)
            if (item !is PdfAnnotationItem.FreeText) annotation.constructAppearances(document)
        }
    }

    private fun loadFont(document: PDDocument): PDType0Font =
        appContext.assets.open(FONT_ASSET).use { PDType0Font.load(document, it, true) }

    private fun createFreeText(
        document: PDDocument,
        page: PDPage,
        item: PdfAnnotationItem.FreeText,
        font: PDType0Font,
    ): PDAnnotationMarkup {
        val visualWidth = visualWidth(page)
        val visualHeight = visualHeight(page)
        val width = visualWidth * item.widthFraction
        val height = min(visualHeight * 0.3f, max(item.fontSize * 4.8f, visualHeight * 0.1f))
        val visualRect = centeredVisualRect(visualWidth, visualHeight, item.centerX, item.centerY, width, height)
        val annotation = PDAnnotationMarkup().apply {
            cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_FREETEXT)
            rectangle = pageRectangle(page, visualRect)
            contents = item.text.trim()
            constantOpacity = 1f
            defaultAppearance = "/Helv ${item.fontSize} Tf 0.075 0.184 0.333 rg"
            defaultStyleString = "font: ${item.fontSize}pt Helvetica; color: #132f55"
            subject = "Free text"
        }
        annotation.appearance = freeTextAppearance(document, annotation.rectangle, annotation.contents, font, item.fontSize)
        return annotation
    }

    private fun freeTextAppearance(
        document: PDDocument,
        rectangle: PDRectangle,
        text: String,
        font: PDType0Font,
        fontSize: Float,
    ): PDAppearanceDictionary {
        val appearanceStream = PDAppearanceStream(document).apply {
            resources = PDResources()
            bBox = PDRectangle(rectangle.width, rectangle.height)
        }
        val padding = 5f
        val lineHeight = fontSize * 1.2f
        val lines = wrapText(text, font, fontSize, (rectangle.width - padding * 2f).coerceAtLeast(1f))
        val maxLines = ((rectangle.height - padding * 2f) / lineHeight).toInt().coerceAtLeast(1)
        PDAppearanceContentStream(appearanceStream).use { stream ->
            stream.setNonStrokingColor(BLUE)
            stream.beginText()
            stream.setFont(font, fontSize)
            stream.setLeading(lineHeight)
            stream.newLineAtOffset(padding, rectangle.height - padding - fontSize)
            lines.take(maxLines).forEachIndexed { index, line ->
                if (index > 0) stream.newLine()
                stream.showText(line)
            }
            stream.endText()
        }
        return PDAppearanceDictionary().apply { setNormalAppearance(appearanceStream) }
    }

    private fun wrapText(text: String, font: PDType0Font, fontSize: Float, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            var current = ""
            paragraph.split(Regex("\\s+")).filter(String::isNotEmpty).forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                val width = font.getStringWidth(candidate) / 1000f * fontSize
                if (width <= maxWidth || current.isEmpty()) current = candidate
                else {
                    result += current
                    current = word
                }
            }
            if (current.isNotEmpty()) result += current
            else if (paragraph.isEmpty()) result += ""
        }
        return result.ifEmpty { listOf("") }
    }

    private fun createInk(
        document: PDDocument,
        page: PDPage,
        item: PdfAnnotationItem.Ink,
    ): PDAnnotationMarkup {
        val visualWidth = visualWidth(page)
        val visualHeight = visualHeight(page)
        val pageStrokes = item.strokes.map { stroke ->
            stroke.flatMap { point ->
                val (x, y) = visualToPage(
                    point.x * visualWidth, point.y * visualHeight, page,
                )
                listOf(x, y)
            }.toFloatArray()
        }.toTypedArray()
        val allX = pageStrokes.flatMap { stroke -> stroke.filterIndexed { index, _ -> index % 2 == 0 }.asIterable() }
        val allY = pageStrokes.flatMap { stroke -> stroke.filterIndexed { index, _ -> index % 2 == 1 }.asIterable() }
        val padding = item.lineWidth * 2f
        return PDAnnotationMarkup().apply {
            cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
            rectangle = PDRectangle(
                allX.minOrNull()!! - padding,
                allY.minOrNull()!! - padding,
                allX.maxOrNull()!! - allX.minOrNull()!! + padding * 2f,
                allY.maxOrNull()!! - allY.minOrNull()!! + padding * 2f,
            )
            inkList = pageStrokes
            setColor(BLUE)
            constantOpacity = 1f
            borderStyle = PDBorderStyleDictionary().apply { width = item.lineWidth }
            subject = "Ink"
        }
    }

    private fun createHighlight(
        document: PDDocument,
        page: PDPage,
        item: PdfAnnotationItem.Highlight,
    ): PDAnnotationTextMarkup {
        val visualWidth = visualWidth(page)
        val visualHeight = visualHeight(page)
        val visualRect = centeredVisualRect(
            visualWidth, visualHeight, item.centerX, item.centerY,
            visualWidth * item.widthFraction, visualHeight * item.heightFraction,
        )
        val topLeft = visualToPage(visualRect.left, visualRect.top, page)
        val topRight = visualToPage(visualRect.right, visualRect.top, page)
        val bottomLeft = visualToPage(visualRect.left, visualRect.bottom, page)
        val bottomRight = visualToPage(visualRect.right, visualRect.bottom, page)
        return PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT).apply {
            rectangle = pageRectangle(page, visualRect)
            quadPoints = floatArrayOf(
                topLeft.first, topLeft.second, topRight.first, topRight.second,
                bottomLeft.first, bottomLeft.second, bottomRight.first, bottomRight.second,
            )
            setColor(YELLOW)
            constantOpacity = 0.45f
            contents = "Highlight"
            subject = "Highlight"
        }
    }

    private data class VisualRect(val left: Float, val bottom: Float, val right: Float, val top: Float)

    private fun centeredVisualRect(
        pageWidth: Float,
        pageHeight: Float,
        centerX: Float,
        centerY: Float,
        requestedWidth: Float,
        requestedHeight: Float,
    ): VisualRect {
        val width = requestedWidth.coerceAtMost(pageWidth * 0.98f)
        val height = requestedHeight.coerceAtMost(pageHeight * 0.98f)
        val x = (centerX * pageWidth).coerceIn(width / 2f, pageWidth - width / 2f)
        val y = (centerY * pageHeight).coerceIn(height / 2f, pageHeight - height / 2f)
        return VisualRect(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)
    }

    private fun pageRectangle(page: PDPage, rect: VisualRect): PDRectangle {
        val corners = listOf(
            visualToPage(rect.left, rect.bottom, page),
            visualToPage(rect.right, rect.bottom, page),
            visualToPage(rect.left, rect.top, page),
            visualToPage(rect.right, rect.top, page),
        )
        val minX = corners.minOf { it.first }
        val maxX = corners.maxOf { it.first }
        val minY = corners.minOf { it.second }
        val maxY = corners.maxOf { it.second }
        return PDRectangle(minX, minY, maxX - minX, maxY - minY)
    }

    private fun normalizedRotation(page: PDPage): Int = ((page.rotation % 360) + 360) % 360
    private fun visualWidth(page: PDPage): Float =
        if (normalizedRotation(page) in setOf(90, 270)) page.cropBox.height else page.cropBox.width
    private fun visualHeight(page: PDPage): Float =
        if (normalizedRotation(page) in setOf(90, 270)) page.cropBox.width else page.cropBox.height
    private fun visualToPage(x: Float, y: Float, page: PDPage): Pair<Float, Float> {
        val crop = page.cropBox
        return when (normalizedRotation(page)) {
            90 -> crop.lowerLeftX + crop.width - y to crop.lowerLeftY + x
            180 -> crop.lowerLeftX + crop.width - x to crop.lowerLeftY + crop.height - y
            270 -> crop.lowerLeftX + y to crop.lowerLeftY + crop.height - x
            else -> crop.lowerLeftX + x to crop.lowerLeftY + y
        }
    }

    private fun validatesOutput(file: File, pageCount: Int, expectedAnnotationCount: Int): Boolean = runCatching {
        PDDocument.load(file).use { document ->
            !document.isEncrypted && document.numberOfPages == pageCount &&
                document.pages.sumOf { it.annotations.size } == expectedAnnotationCount
        }
    }.getOrDefault(false)
    private fun validatesOutput(uri: Uri, pageCount: Int, expectedAnnotationCount: Int): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                !document.isEncrypted && document.numberOfPages == pageCount &&
                    document.pages.sumOf { it.annotations.size } == expectedAnnotationCount
            }
        } ?: false
    }.getOrDefault(false)

    private companion object {
        const val MAX_ANNOTATIONS = 100
        const val MAX_TEXT_LENGTH = 2_000
        const val MAX_STROKES = 100
        const val MAX_POINTS_PER_STROKE = 5_000
        const val FONT_ASSET = "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"
        val BLUE = PDColor(floatArrayOf(0.075f, 0.184f, 0.333f), PDDeviceRGB.INSTANCE)
        val YELLOW = PDColor(floatArrayOf(1f, 0.92f, 0.23f), PDDeviceRGB.INSTANCE)
    }
}
