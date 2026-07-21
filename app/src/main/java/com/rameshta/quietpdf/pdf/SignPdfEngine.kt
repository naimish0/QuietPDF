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
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.min

data class SignPdfAnalysis(val pageCount: Int)

data class VisibleSignatureSettings(
    val pageIndex: Int,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.2f,
    val widthFraction: Float = 0.3f,
) {
    fun isValid(pageCount: Int, bitmap: Bitmap): Boolean =
        pageIndex in 0 until pageCount && centerX in 0f..1f && centerY in 0f..1f &&
            widthFraction in 0.1f..0.6f && bitmap.width > 0 && bitmap.height > 0 && !bitmap.isRecycled
}

sealed interface SignPdfAnalysisResult {
    data class Ready(val analysis: SignPdfAnalysis) : SignPdfAnalysisResult
    data object PasswordProtected : SignPdfAnalysisResult
    data object InvalidDocument : SignPdfAnalysisResult
    data object PermissionDenied : SignPdfAnalysisResult
    data object InsufficientMemory : SignPdfAnalysisResult
}

sealed interface SignatureImageResult {
    data class Ready(val bitmap: Bitmap) : SignatureImageResult
    data object InvalidImage : SignatureImageResult
    data object PermissionDenied : SignatureImageResult
    data object InsufficientMemory : SignatureImageResult
}

sealed interface SignPdfPreviewResult {
    data class Ready(val bitmap: Bitmap) : SignPdfPreviewResult
    data object InvalidSettings : SignPdfPreviewResult
    data object Failed : SignPdfPreviewResult
}

sealed interface SignPdfResult {
    data class Success(val pageCount: Int) : SignPdfResult
    data object PasswordProtected : SignPdfResult
    data object InvalidDocument : SignPdfResult
    data object InvalidSignature : SignPdfResult
    data object PermissionDenied : SignPdfResult
    data object InsufficientMemory : SignPdfResult
    data object Failed : SignPdfResult
}

class SignPdfEngine(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    init { PDFBoxResourceLoader.init(appContext) }

    suspend fun analyze(sourceUri: Uri): SignPdfAnalysisResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    when {
                        document.isEncrypted -> SignPdfAnalysisResult.PasswordProtected
                        document.numberOfPages <= 0 -> SignPdfAnalysisResult.InvalidDocument
                        else -> SignPdfAnalysisResult.Ready(SignPdfAnalysis(document.numberOfPages))
                    }
                }
            } ?: SignPdfAnalysisResult.InvalidDocument
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            SignPdfAnalysisResult.PasswordProtected
        } catch (_: SecurityException) {
            SignPdfAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            SignPdfAnalysisResult.InsufficientMemory
        } catch (_: Exception) {
            SignPdfAnalysisResult.InvalidDocument
        }
    }

    suspend fun decodeSignatureImage(uri: Uri): SignatureImageResult = withContext(Dispatchers.IO) {
        try {
            SignatureImageResult.Ready(cropTransparent(decodeImage(uri)))
        } catch (_: SecurityException) {
            SignatureImageResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            SignatureImageResult.InsufficientMemory
        } catch (_: Exception) {
            SignatureImageResult.InvalidImage
        }
    }

    suspend fun preview(
        sourceUri: Uri,
        signature: Bitmap,
        settings: VisibleSignatureSettings,
        targetWidth: Int,
    ): SignPdfPreviewResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (!settings.isValid(document.numberOfPages, signature) || !hasVisiblePixels(signature)) {
                        return@withContext SignPdfPreviewResult.InvalidSettings
                    }
                    addSignature(document, signature, settings)
                    val page = document.getPage(settings.pageIndex)
                    val scale = targetWidth.coerceIn(240, 1200) / visualWidth(page).coerceAtLeast(1f)
                    SignPdfPreviewResult.Ready(
                        PDFRenderer(document).renderImage(settings.pageIndex, scale.coerceIn(0.25f, 3f)),
                    )
                }
            } ?: SignPdfPreviewResult.Failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OutOfMemoryError) {
            SignPdfPreviewResult.Failed
        } catch (_: Exception) {
            SignPdfPreviewResult.Failed
        }
    }

    suspend fun sign(
        sourceUri: Uri,
        outputUri: Uri,
        signature: Bitmap,
        settings: VisibleSignatureSettings,
        expectedPageCount: Int,
    ): SignPdfResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri || !settings.isValid(expectedPageCount, signature) ||
            !hasVisiblePixels(signature)
        ) {
            if (sourceUri != outputUri) cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext SignPdfResult.InvalidSignature
        }
        val temporary = try {
            File.createTempFile("visible-signature-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext SignPdfResult.Failed
        }
        var committed = false
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) return@withContext SignPdfResult.PasswordProtected
                    if (document.numberOfPages != expectedPageCount) return@withContext SignPdfResult.InvalidDocument
                    coroutineContext.ensureActive()
                    addSignature(document, signature, settings)
                    document.save(temporary)
                }
            } ?: return@withContext SignPdfResult.InvalidDocument
            if (!validatesOutput(temporary, expectedPageCount)) return@withContext SignPdfResult.Failed
            resolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { it.copyTo(output) }
            } ?: return@withContext SignPdfResult.Failed
            if (!validatesOutput(outputUri, expectedPageCount)) return@withContext SignPdfResult.Failed
            committed = true
            SignPdfResult.Success(expectedPageCount)
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            SignPdfResult.PasswordProtected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            SignPdfResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            SignPdfResult.InsufficientMemory
        } catch (_: Exception) {
            SignPdfResult.Failed
        } finally {
            temporary.delete()
            if (!committed) cleanupNewPdfOutput(appContext, resolver, outputUri)
        }
    }

    private fun addSignature(
        document: PDDocument,
        signature: Bitmap,
        settings: VisibleSignatureSettings,
    ) {
        val page = document.getPage(settings.pageIndex)
        val visualWidth = visualWidth(page)
        val visualHeight = visualHeight(page)
        val requestedWidth = visualWidth * settings.widthFraction
        val requestedHeight = requestedWidth * signature.height / signature.width.toFloat()
        val fit = min(1f, min(visualWidth * 0.95f / requestedWidth, visualHeight * 0.95f / requestedHeight))
        val width = requestedWidth * fit
        val height = requestedHeight * fit
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val centerX = (visualWidth * settings.centerX).coerceIn(halfWidth, visualWidth - halfWidth)
        val centerY = (visualHeight * settings.centerY).coerceIn(halfHeight, visualHeight - halfHeight)
        val crop = page.cropBox
        val (pageX, pageY) = visualToPage(
            centerX, centerY, crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height,
            normalizedRotation(page),
        )
        val image = LosslessFactory.createFromImage(document, signature)
        val angle = normalizedRotation(page) * Math.PI / 180.0
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.saveGraphicsState()
            stream.transform(Matrix.getRotateInstance(angle, pageX, pageY))
            stream.drawImage(image, -width / 2f, -height / 2f, width, height)
            stream.restoreGraphicsState()
        }
    }

    private fun decodeImage(uri: Uri): Bitmap = ImageDecoder.decodeBitmap(
        ImageDecoder.createSource(resolver, uri),
    ) { decoder, info, _ ->
        require(info.size.width > 0 && info.size.height > 0)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
        val largest = maxOf(info.size.width, info.size.height)
        if (largest > MAX_IMAGE_DIMENSION) {
            decoder.setTargetSampleSize(ceil(largest / MAX_IMAGE_DIMENSION.toDouble()).toInt())
        }
    }

    private fun cropTransparent(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, pixel ->
            if (pixel ushr 24 > 8) {
                val x = index % width
                val y = index / width
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) {
            source.recycle()
            throw IllegalArgumentException("Blank signature")
        }
        if (left == 0 && top == 0 && right == width - 1 && bottom == height - 1) return source
        return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1).also {
            source.recycle()
        }
    }

    private fun hasVisiblePixels(bitmap: Bitmap): Boolean {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (row.any { it ushr 24 > 8 }) return true
        }
        return false
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

    companion object { private const val MAX_IMAGE_DIMENSION = 2048 }
}
