package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

enum class ImageWatermarkPosition {
    TopLeft, TopCenter, TopRight,
    MiddleLeft, Center, MiddleRight,
    BottomLeft, BottomCenter, BottomRight,
}

data class ImageWatermarkSettings(
    val pageIndices: Set<Int>,
    val position: ImageWatermarkPosition = ImageWatermarkPosition.Center,
    val opacity: Float = 0.5f,
    val rotationDegrees: Int = 0,
    val scale: Float = 0.2f,
) {
    fun isValid(pageCount: Int): Boolean =
        pageIndices.isNotEmpty() && pageIndices.all { it in 0 until pageCount } &&
            opacity in 0.1f..1f && rotationDegrees in setOf(-45, 0, 45) &&
            scale in 0.05f..0.5f
}

object ImageWatermarkPageSelection {
    fun parse(input: String, pageCount: Int): Set<Int>? {
        if (pageCount <= 0) return null
        if (input.isBlank()) return (0 until pageCount).toSet()
        return ExtractPageSelectionParser.parse(input, pageCount)?.toSet()
    }
}

data class ImageWatermarkAnalysis(val pageCount: Int)
data class WatermarkImageInfo(val width: Int, val height: Int)

sealed interface ImageWatermarkAnalysisResult {
    data class Ready(val analysis: ImageWatermarkAnalysis) : ImageWatermarkAnalysisResult
    data object PasswordProtected : ImageWatermarkAnalysisResult
    data object InvalidDocument : ImageWatermarkAnalysisResult
    data object PermissionDenied : ImageWatermarkAnalysisResult
    data object InsufficientMemory : ImageWatermarkAnalysisResult
}

sealed interface WatermarkImageAnalysisResult {
    data class Ready(val info: WatermarkImageInfo) : WatermarkImageAnalysisResult
    data object InvalidImage : WatermarkImageAnalysisResult
    data object PermissionDenied : WatermarkImageAnalysisResult
    data object InsufficientMemory : WatermarkImageAnalysisResult
}

sealed interface ImageWatermarkPreviewResult {
    data class Ready(val bitmap: Bitmap) : ImageWatermarkPreviewResult
    data object InvalidSettings : ImageWatermarkPreviewResult
    data object Failed : ImageWatermarkPreviewResult
}

sealed interface ImageWatermarkResult {
    data class Success(val pageCount: Int, val watermarkedPageCount: Int) : ImageWatermarkResult
    data object PasswordProtected : ImageWatermarkResult
    data object InvalidDocument : ImageWatermarkResult
    data object InvalidImage : ImageWatermarkResult
    data object InvalidSettings : ImageWatermarkResult
    data object PermissionDenied : ImageWatermarkResult
    data object InsufficientMemory : ImageWatermarkResult
    data object Failed : ImageWatermarkResult
}

class ImageWatermarkEngine(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    init { PDFBoxResourceLoader.init(appContext) }

    suspend fun analyzePdf(sourceUri: Uri): ImageWatermarkAnalysisResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    when {
                        document.isEncrypted -> ImageWatermarkAnalysisResult.PasswordProtected
                        document.numberOfPages <= 0 -> ImageWatermarkAnalysisResult.InvalidDocument
                        else -> ImageWatermarkAnalysisResult.Ready(ImageWatermarkAnalysis(document.numberOfPages))
                    }
                }
            } ?: ImageWatermarkAnalysisResult.InvalidDocument
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            ImageWatermarkAnalysisResult.PasswordProtected
        } catch (_: SecurityException) {
            ImageWatermarkAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ImageWatermarkAnalysisResult.InsufficientMemory
        } catch (_: Exception) {
            ImageWatermarkAnalysisResult.InvalidDocument
        }
    }

    suspend fun analyzeImage(imageUri: Uri): WatermarkImageAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeImage(imageUri)
            try {
                WatermarkImageAnalysisResult.Ready(WatermarkImageInfo(bitmap.width, bitmap.height))
            } finally {
                bitmap.recycle()
            }
        } catch (_: SecurityException) {
            WatermarkImageAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            WatermarkImageAnalysisResult.InsufficientMemory
        } catch (_: Exception) {
            WatermarkImageAnalysisResult.InvalidImage
        }
    }

    suspend fun preview(
        sourceUri: Uri,
        imageUri: Uri,
        pageIndex: Int,
        settings: ImageWatermarkSettings,
        targetWidth: Int,
    ): ImageWatermarkPreviewResult = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            bitmap = decodeImage(imageUri)
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (pageIndex !in 0 until document.numberOfPages ||
                        !settings.isValid(document.numberOfPages)
                    ) return@withContext ImageWatermarkPreviewResult.InvalidSettings
                    addWatermark(document, pageIndex, settings, LosslessFactory.createFromImage(document, bitmap))
                    val page = document.getPage(pageIndex)
                    val renderScale = targetWidth.coerceIn(240, 1200) / visualWidth(page).coerceAtLeast(1f)
                    ImageWatermarkPreviewResult.Ready(
                        PDFRenderer(document).renderImage(pageIndex, renderScale.coerceIn(0.25f, 3f)),
                    )
                }
            } ?: ImageWatermarkPreviewResult.Failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ImageWatermarkPreviewResult.Failed
        } finally {
            bitmap?.recycle()
        }
    }

    suspend fun apply(
        sourceUri: Uri,
        imageUri: Uri,
        outputUri: Uri,
        settings: ImageWatermarkSettings,
        expectedPageCount: Int,
    ): ImageWatermarkResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri || imageUri == outputUri || !settings.isValid(expectedPageCount)) {
            if (sourceUri != outputUri && imageUri != outputUri) cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext ImageWatermarkResult.InvalidSettings
        }
        val temporary = try {
            File.createTempFile("image-watermark-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext ImageWatermarkResult.Failed
        }
        var bitmap: Bitmap? = null
        var outputCommitted = false
        try {
            bitmap = try {
                decodeImage(imageUri)
            } catch (denied: SecurityException) {
                throw denied
            } catch (memory: OutOfMemoryError) {
                throw memory
            } catch (_: Exception) {
                return@withContext ImageWatermarkResult.InvalidImage
            }
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) return@withContext ImageWatermarkResult.PasswordProtected
                    if (document.numberOfPages != expectedPageCount) return@withContext ImageWatermarkResult.InvalidDocument
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    settings.pageIndices.sorted().forEach { pageIndex ->
                        coroutineContext.ensureActive()
                        addWatermark(document, pageIndex, settings, image)
                    }
                    document.save(temporary)
                }
            } ?: return@withContext ImageWatermarkResult.InvalidDocument
            coroutineContext.ensureActive()
            if (!validatesOutput(temporary, expectedPageCount)) return@withContext ImageWatermarkResult.Failed
            resolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { it.copyTo(output) }
            } ?: return@withContext ImageWatermarkResult.Failed
            if (!validatesOutput(outputUri, expectedPageCount)) return@withContext ImageWatermarkResult.Failed
            outputCommitted = true
            ImageWatermarkResult.Success(expectedPageCount, settings.pageIndices.size)
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            ImageWatermarkResult.PasswordProtected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            ImageWatermarkResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ImageWatermarkResult.InsufficientMemory
        } catch (_: Exception) {
            ImageWatermarkResult.Failed
        } finally {
            bitmap?.recycle()
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, resolver, outputUri)
        }
    }

    private fun decodeImage(uri: Uri): Bitmap = ImageDecoder.decodeBitmap(
        ImageDecoder.createSource(resolver, uri),
    ) { decoder, info, _ ->
        require(info.size.width > 0 && info.size.height > 0)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
        val largestSide = maxOf(info.size.width, info.size.height)
        if (largestSide > MAX_IMAGE_DIMENSION) {
            decoder.setTargetSampleSize(ceil(largestSide / MAX_IMAGE_DIMENSION.toDouble()).toInt())
        }
    }

    private fun addWatermark(
        document: PDDocument,
        pageIndex: Int,
        settings: ImageWatermarkSettings,
        image: PDImageXObject,
    ) {
        val page = document.getPage(pageIndex)
        val crop = page.cropBox
        val visualWidth = visualWidth(page)
        val visualHeight = visualHeight(page)
        val margin = minOf(visualWidth, visualHeight) * 0.06f
        val requestedWidth = visualWidth * settings.scale
        val requestedHeight = requestedWidth * image.height / image.width.toFloat()
        val fit = minOf(
            1f,
            (visualWidth - margin * 2f) / requestedWidth,
            (visualHeight - margin * 2f) / requestedHeight,
        )
        val imageWidth = requestedWidth * fit
        val imageHeight = requestedHeight * fit
        val angle = settings.rotationDegrees * PI.toFloat() / 180f
        val halfWidth = abs(cos(angle)) * imageWidth / 2f + abs(sin(angle)) * imageHeight / 2f
        val halfHeight = abs(sin(angle)) * imageWidth / 2f + abs(cos(angle)) * imageHeight / 2f
        val centerX = when (settings.position) {
            ImageWatermarkPosition.TopLeft, ImageWatermarkPosition.MiddleLeft,
            ImageWatermarkPosition.BottomLeft -> margin + halfWidth
            ImageWatermarkPosition.TopRight, ImageWatermarkPosition.MiddleRight,
            ImageWatermarkPosition.BottomRight -> visualWidth - margin - halfWidth
            else -> visualWidth / 2f
        }.coerceIn(margin + halfWidth, visualWidth - margin - halfWidth)
        val centerY = when (settings.position) {
            ImageWatermarkPosition.TopLeft, ImageWatermarkPosition.TopCenter,
            ImageWatermarkPosition.TopRight -> visualHeight - margin - halfHeight
            ImageWatermarkPosition.BottomLeft, ImageWatermarkPosition.BottomCenter,
            ImageWatermarkPosition.BottomRight -> margin + halfHeight
            else -> visualHeight / 2f
        }.coerceIn(margin + halfHeight, visualHeight - margin - halfHeight)
        val (pageX, pageY) = visualToPage(
            centerX, centerY, crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height,
            normalizedRotation(page),
        )
        val pageAngle = (settings.rotationDegrees + normalizedRotation(page)) * PI.toFloat() / 180f
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.saveGraphicsState()
            stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = settings.opacity
            })
            stream.transform(Matrix.getRotateInstance(pageAngle.toDouble(), pageX, pageY))
            stream.drawImage(image, -imageWidth / 2f, -imageHeight / 2f, imageWidth, imageHeight)
            stream.restoreGraphicsState()
        }
    }

    private fun normalizedRotation(page: PDPage): Int = ((page.rotation % 360) + 360) % 360
    private fun visualWidth(page: PDPage): Float =
        if (normalizedRotation(page) in setOf(90, 270)) page.cropBox.height else page.cropBox.width
    private fun visualHeight(page: PDPage): Float =
        if (normalizedRotation(page) in setOf(90, 270)) page.cropBox.width else page.cropBox.height

    private fun visualToPage(
        x: Float, y: Float, originX: Float, originY: Float, width: Float, height: Float, rotation: Int,
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

    companion object { private const val MAX_IMAGE_DIMENSION = 4096 }
}
