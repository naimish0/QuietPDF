package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class TextWatermarkPosition {
    TopLeft,
    TopCenter,
    TopRight,
    MiddleLeft,
    Center,
    MiddleRight,
    BottomLeft,
    BottomCenter,
    BottomRight,
}

data class TextWatermarkSettings(
    val text: String,
    val pageIndices: Set<Int>,
    val position: TextWatermarkPosition = TextWatermarkPosition.Center,
    val opacity: Float = 0.3f,
    val rotationDegrees: Int = -45,
    val scale: Float = 0.1f,
) {
    fun isValid(pageCount: Int): Boolean =
        text.isNotBlank() && text.length <= MAX_TEXT_LENGTH && text.none(Char::isISOControl) &&
            pageIndices.isNotEmpty() && pageIndices.all { it in 0 until pageCount } &&
            opacity in 0.1f..1f && rotationDegrees in setOf(-45, 0, 45) &&
            scale in 0.05f..0.25f

    companion object {
        const val MAX_TEXT_LENGTH = 80
    }
}

object TextWatermarkPageSelection {
    fun parse(input: String, pageCount: Int): Set<Int>? {
        if (pageCount <= 0) return null
        if (input.isBlank()) return (0 until pageCount).toSet()
        return ExtractPageSelectionParser.parse(input, pageCount)?.toSet()
    }
}

data class TextWatermarkAnalysis(val pageCount: Int)

sealed interface TextWatermarkAnalysisResult {
    data class Ready(val analysis: TextWatermarkAnalysis) : TextWatermarkAnalysisResult
    data object PasswordProtected : TextWatermarkAnalysisResult
    data object InvalidDocument : TextWatermarkAnalysisResult
    data object PermissionDenied : TextWatermarkAnalysisResult
    data object InsufficientMemory : TextWatermarkAnalysisResult
}

sealed interface TextWatermarkPreviewResult {
    data class Ready(val bitmap: Bitmap) : TextWatermarkPreviewResult
    data object InvalidSettings : TextWatermarkPreviewResult
    data object Failed : TextWatermarkPreviewResult
}

sealed interface TextWatermarkResult {
    data class Success(val pageCount: Int, val watermarkedPageCount: Int) : TextWatermarkResult
    data object PasswordProtected : TextWatermarkResult
    data object InvalidDocument : TextWatermarkResult
    data object InvalidSettings : TextWatermarkResult
    data object PermissionDenied : TextWatermarkResult
    data object InsufficientMemory : TextWatermarkResult
    data object Failed : TextWatermarkResult
}

class TextWatermarkEngine(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun analyze(sourceUri: Uri): TextWatermarkAnalysisResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    when {
                        document.isEncrypted -> TextWatermarkAnalysisResult.PasswordProtected
                        document.numberOfPages <= 0 -> TextWatermarkAnalysisResult.InvalidDocument
                        else -> TextWatermarkAnalysisResult.Ready(
                            TextWatermarkAnalysis(document.numberOfPages),
                        )
                    }
                }
            } ?: TextWatermarkAnalysisResult.InvalidDocument
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            TextWatermarkAnalysisResult.PasswordProtected
        } catch (_: SecurityException) {
            TextWatermarkAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            TextWatermarkAnalysisResult.InsufficientMemory
        } catch (_: Exception) {
            TextWatermarkAnalysisResult.InvalidDocument
        }
    }

    suspend fun preview(
        sourceUri: Uri,
        pageIndex: Int,
        settings: TextWatermarkSettings,
        targetWidth: Int,
    ): TextWatermarkPreviewResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (pageIndex !in 0 until document.numberOfPages ||
                        !settings.isValid(document.numberOfPages)
                    ) return@withContext TextWatermarkPreviewResult.InvalidSettings
                    val font = loadFont(document)
                    addWatermark(document, pageIndex, settings, font)
                    val page = document.getPage(pageIndex)
                    val visualWidth = visualWidth(page).coerceAtLeast(1f)
                    val scale = targetWidth.coerceIn(240, 1200) / visualWidth
                    TextWatermarkPreviewResult.Ready(
                        PDFRenderer(document).renderImage(pageIndex, scale.coerceIn(0.25f, 3f)),
                    )
                }
            } ?: TextWatermarkPreviewResult.Failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            TextWatermarkPreviewResult.Failed
        }
    }

    suspend fun apply(
        sourceUri: Uri,
        outputUri: Uri,
        settings: TextWatermarkSettings,
        expectedPageCount: Int,
    ): TextWatermarkResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri || !settings.isValid(expectedPageCount)) {
            if (sourceUri != outputUri) cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext TextWatermarkResult.InvalidSettings
        }
        val temporary = try {
            File.createTempFile("text-watermark-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext TextWatermarkResult.Failed
        }
        var outputCommitted = false
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) return@withContext TextWatermarkResult.PasswordProtected
                    if (document.numberOfPages != expectedPageCount) {
                        return@withContext TextWatermarkResult.InvalidDocument
                    }
                    val font = loadFont(document)
                    settings.pageIndices.sorted().forEach { pageIndex ->
                        coroutineContext.ensureActive()
                        addWatermark(document, pageIndex, settings, font)
                    }
                    document.save(temporary)
                }
            } ?: return@withContext TextWatermarkResult.InvalidDocument
            coroutineContext.ensureActive()
            if (!validatesOutput(temporary, expectedPageCount)) return@withContext TextWatermarkResult.Failed

            resolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext TextWatermarkResult.Failed
            if (!validatesOutput(outputUri, expectedPageCount)) return@withContext TextWatermarkResult.Failed
            outputCommitted = true
            TextWatermarkResult.Success(expectedPageCount, settings.pageIndices.size)
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            TextWatermarkResult.PasswordProtected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            TextWatermarkResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            TextWatermarkResult.InsufficientMemory
        } catch (_: Exception) {
            TextWatermarkResult.Failed
        } finally {
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, resolver, outputUri)
        }
    }

    private fun loadFont(document: PDDocument): PDType0Font =
        appContext.assets.open(FONT_ASSET).use { PDType0Font.load(document, it, true) }

    private fun addWatermark(
        document: PDDocument,
        pageIndex: Int,
        settings: TextWatermarkSettings,
        font: PDType0Font,
    ) {
        val page = document.getPage(pageIndex)
        val crop = page.cropBox
        val visualWidth = visualWidth(page)
        val visualHeight = visualHeight(page)
        val margin = minOf(visualWidth, visualHeight) * 0.08f
        val glyphWidth = font.getStringWidth(settings.text) / 1000f
        val requestedFontSize = (minOf(visualWidth, visualHeight) * settings.scale).coerceAtLeast(8f)
        val maximumFontSize = if (glyphWidth > 0f) {
            (visualWidth - margin * 2f) / glyphWidth
        } else requestedFontSize
        val fontSize = minOf(requestedFontSize, maximumFontSize).coerceAtLeast(8f)
        val textWidth = glyphWidth * fontSize
        val visualAngle = settings.rotationDegrees * PI.toFloat() / 180f
        val rotatedHalfWidth = abs(cos(visualAngle)) * textWidth / 2f +
            abs(sin(visualAngle)) * fontSize / 2f
        val rotatedHalfHeight = abs(sin(visualAngle)) * textWidth / 2f +
            abs(cos(visualAngle)) * fontSize / 2f
        val visualCenterX = when (settings.position) {
            TextWatermarkPosition.TopLeft, TextWatermarkPosition.MiddleLeft,
            TextWatermarkPosition.BottomLeft -> margin + rotatedHalfWidth
            TextWatermarkPosition.TopRight, TextWatermarkPosition.MiddleRight,
            TextWatermarkPosition.BottomRight -> visualWidth - margin - rotatedHalfWidth
            else -> visualWidth / 2f
        }.coerceIn(margin, visualWidth - margin)
        val visualCenterY = when (settings.position) {
            TextWatermarkPosition.TopLeft, TextWatermarkPosition.TopCenter,
            TextWatermarkPosition.TopRight -> visualHeight - margin - rotatedHalfHeight
            TextWatermarkPosition.BottomLeft, TextWatermarkPosition.BottomCenter,
            TextWatermarkPosition.BottomRight -> margin + rotatedHalfHeight
            TextWatermarkPosition.MiddleLeft, TextWatermarkPosition.Center,
            TextWatermarkPosition.MiddleRight -> visualHeight / 2f
        }
        val (pageX, pageY) = visualToPage(
            visualCenterX,
            visualCenterY,
            crop.lowerLeftX,
            crop.lowerLeftY,
            crop.width,
            crop.height,
            normalizedRotation(page),
        )
        val pageAngle = (settings.rotationDegrees + normalizedRotation(page)) * PI.toFloat() / 180f
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true,
        ).use { stream ->
            stream.saveGraphicsState()
            stream.setGraphicsStateParameters(
                PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = settings.opacity
                },
            )
            val watermarkGray = 90f / 255f
            stream.setNonStrokingColor(watermarkGray, watermarkGray, watermarkGray)
            stream.transform(Matrix.getRotateInstance(pageAngle.toDouble(), pageX, pageY))
            stream.beginText()
            stream.setFont(font, fontSize)
            stream.newLineAtOffset(-textWidth / 2f, -fontSize * 0.35f)
            stream.showText(settings.text)
            stream.endText()
            stream.restoreGraphicsState()
        }
    }

    private fun normalizedRotation(page: PDPage): Int = ((page.rotation % 360) + 360) % 360

    private fun visualWidth(page: PDPage): Float =
        if (normalizedRotation(page) in setOf(90, 270)) page.cropBox.height else page.cropBox.width

    private fun visualHeight(page: PDPage): Float =
        if (normalizedRotation(page) in setOf(90, 270)) page.cropBox.width else page.cropBox.height

    private fun visualToPage(
        x: Float,
        y: Float,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        rotation: Int,
    ): Pair<Float, Float> = when (rotation) {
        90 -> originX + width - y to originY + x
        180 -> originX + width - x to originY + height - y
        270 -> originX + y to originY + height - x
        else -> originX + x to originY + y
    }

    private fun validatesOutput(file: File, pageCount: Int): Boolean = runCatching {
        PDDocument.load(file).use { !it.isEncrypted && it.numberOfPages == pageCount }
    }.getOrDefault(false)

    private fun validatesOutput(uri: Uri, pageCount: Int): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { !it.isEncrypted && it.numberOfPages == pageCount }
        } ?: false
    }.getOrDefault(false)

    companion object {
        private const val FONT_ASSET =
            "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"
    }
}
