package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import io.legere.pdfiumandroid.core.unlocked.PdfDocumentU
import io.legere.pdfiumandroid.core.unlocked.PdfiumCoreU
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToLong

enum class PdfCompressionMode(
    val maxImageDimension: Int,
    val jpegQuality: Int,
    internal val estimateQualityFactor: Double,
) {
    HighQuality(2560, 88, 0.95),
    Balanced(1800, 74, 0.78),
    MaximumCompression(1280, 55, 0.60),
}

sealed interface PdfCompressionRequest {
    data class Quality(val mode: PdfCompressionMode) : PdfCompressionRequest
    data class TargetSize(val targetSizeBytes: Long) : PdfCompressionRequest
}

data class CompressionProgress(
    val attempt: Int,
    val totalAttempts: Int,
    val completedPages: Int,
    val totalPages: Int,
)

internal data class CompressionAttempt(
    val maxImageDimension: Int,
    val jpegQuality: Int,
)

object TargetFileSize {
    private const val BytesPerMegabyte = 1_000_000L
    const val MinimumTargetBytes = 10_000L

    fun parseMegabytes(input: String, originalSizeBytes: Long): Long? {
        if (originalSizeBytes <= MinimumTargetBytes) return null
        val value = input.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO) return null
        val bytes = runCatching {
            value.multiply(BigDecimal.valueOf(BytesPerMegabyte))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull() ?: return null
        return bytes.takeIf { it in MinimumTargetBytes until originalSizeBytes }
    }
}

internal object TargetCompressionPlanner {
    val attempts: List<CompressionAttempt> = listOf(
        CompressionAttempt(2560, 88),
        CompressionAttempt(2200, 82),
        CompressionAttempt(1800, 74),
        CompressionAttempt(1500, 64),
        CompressionAttempt(1280, 55),
        CompressionAttempt(1024, 45),
        CompressionAttempt(800, 35),
    )
}

data class CompressibleImage(
    val encodedSizeBytes: Long,
    val width: Long,
    val height: Long,
)

data class CompressPdfAnalysis(
    val pageCount: Int,
    val originalSizeBytes: Long,
    val compressibleImages: List<CompressibleImage>,
) {
    fun estimatedOutputSize(mode: PdfCompressionMode): Long =
        CompressionEstimate.outputSize(originalSizeBytes, compressibleImages, mode)
}

object CompressionEstimate {
    fun outputSize(
        originalSizeBytes: Long,
        images: List<CompressibleImage>,
        mode: PdfCompressionMode,
    ): Long {
        if (originalSizeBytes <= 0L || images.isEmpty()) return originalSizeBytes.coerceAtLeast(0L)
        var originalImageBytes = 0L
        var estimatedImageBytes = 0L
        images.forEach { image ->
            if (image.encodedSizeBytes <= 0L || image.width <= 0L || image.height <= 0L) return@forEach
            originalImageBytes = saturatingAdd(originalImageBytes, image.encodedSizeBytes)
            val largestDimension = maxOf(image.width, image.height)
            val scale = if (largestDimension <= mode.maxImageDimension) {
                1.0
            } else {
                mode.maxImageDimension.toDouble() / largestDimension
            }
            val estimate = (image.encodedSizeBytes * scale * scale * mode.estimateQualityFactor)
                .roundToLong()
                .coerceAtLeast(512L)
            estimatedImageBytes = saturatingAdd(
                estimatedImageBytes,
                minOf(image.encodedSizeBytes, estimate),
            )
        }
        val nonImageBytes = (originalSizeBytes - minOf(originalSizeBytes, originalImageBytes))
            .coerceAtLeast(0L)
        return saturatingAdd(nonImageBytes, estimatedImageBytes)
            .coerceIn(1L, originalSizeBytes)
    }

    private fun saturatingAdd(first: Long, second: Long): Long =
        if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
}

sealed interface CompressPdfAnalysisResult {
    data class Ready(val analysis: CompressPdfAnalysis) : CompressPdfAnalysisResult
    data object InvalidDocument : CompressPdfAnalysisResult
    data object PermissionDenied : CompressPdfAnalysisResult
    data object InsufficientMemory : CompressPdfAnalysisResult
    data object Failed : CompressPdfAnalysisResult
}

sealed interface CompressPdfResult {
    data class Success(
        val pageCount: Int,
        val originalSizeBytes: Long,
        val outputSizeBytes: Long,
        val recompressedImageCount: Int,
        val targetSizeBytes: Long? = null,
        val targetReached: Boolean = true,
    ) : CompressPdfResult
    data class NotSmaller(
        val originalSizeBytes: Long,
        val candidateSizeBytes: Long,
        val recompressedImageCount: Int,
    ) : CompressPdfResult
    data object InvalidDocument : CompressPdfResult
    data object InvalidTargetSize : CompressPdfResult
    data object PermissionDenied : CompressPdfResult
    data object InsufficientMemory : CompressPdfResult
    data object Failed : CompressPdfResult
}

class CompressPdfEngine(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    suspend fun analyze(sourceUri: Uri): CompressPdfAnalysisResult = withContext(Dispatchers.IO) {
        var descriptor: ParcelFileDescriptor? = null
        var document: PdfDocumentU? = null
        try {
            descriptor = try {
                contentResolver.openFileDescriptor(sourceUri, "r")
                    ?: return@withContext CompressPdfAnalysisResult.InvalidDocument
            } catch (_: SecurityException) {
                return@withContext CompressPdfAnalysisResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext CompressPdfAnalysisResult.InvalidDocument
            }
            val size = descriptor.statSize
            if (size <= 0L) return@withContext CompressPdfAnalysisResult.InvalidDocument
            document = try {
                PdfiumCoreU(appContext).newDocument(descriptor)
            } catch (_: SecurityException) {
                return@withContext CompressPdfAnalysisResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext CompressPdfAnalysisResult.InvalidDocument
            }
            val pageCount = document.getPageCount()
            if (pageCount <= 0) return@withContext CompressPdfAnalysisResult.InvalidDocument
            coroutineContext.ensureActive()
            val rawDetails = NativePdfCompressor.analyzeImages(document.mNativeDocPtr, pageCount)
                ?: return@withContext CompressPdfAnalysisResult.Failed
            if (rawDetails.size % 3 != 0) return@withContext CompressPdfAnalysisResult.Failed
            val images = rawDetails.asList().chunked(3).map { values ->
                CompressibleImage(values[0], values[1], values[2])
            }
            CompressPdfAnalysisResult.Ready(CompressPdfAnalysis(pageCount, size, images))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            CompressPdfAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            CompressPdfAnalysisResult.InsufficientMemory
        } catch (_: LinkageError) {
            CompressPdfAnalysisResult.Failed
        } catch (_: Exception) {
            CompressPdfAnalysisResult.Failed
        } finally {
            document?.let { runCatching { it.close() } }
            descriptor?.let { runCatching { it.close() } }
        }
    }

    suspend fun compress(
        sourceUri: Uri,
        outputUri: Uri,
        mode: PdfCompressionMode,
        expectedPageCount: Int,
        expectedOriginalSizeBytes: Long,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit = { _, _ -> },
    ): CompressPdfResult = compress(
        sourceUri = sourceUri,
        outputUri = outputUri,
        request = PdfCompressionRequest.Quality(mode),
        expectedPageCount = expectedPageCount,
        expectedOriginalSizeBytes = expectedOriginalSizeBytes,
    ) { progress -> onProgress(progress.completedPages, progress.totalPages) }

    suspend fun compress(
        sourceUri: Uri,
        outputUri: Uri,
        request: PdfCompressionRequest,
        expectedPageCount: Int,
        expectedOriginalSizeBytes: Long,
        onProgress: (CompressionProgress) -> Unit = {},
    ): CompressPdfResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri || expectedPageCount <= 0 || expectedOriginalSizeBytes <= 0L) {
            return@withContext CompressPdfResult.InvalidDocument
        }
        val targetSize = (request as? PdfCompressionRequest.TargetSize)?.targetSizeBytes
        if (targetSize != null && targetSize !in TargetFileSize.MinimumTargetBytes until expectedOriginalSizeBytes) {
            return@withContext CompressPdfResult.InvalidTargetSize
        }
        val temporary = try {
            File.createTempFile("compress-pdf-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, contentResolver, outputUri)
            return@withContext CompressPdfResult.Failed
        }
        val bestTemporary = try {
            File.createTempFile("compress-pdf-best-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            temporary.delete()
            cleanupNewPdfOutput(appContext, contentResolver, outputUri)
            return@withContext CompressPdfResult.Failed
        }
        var sourceDescriptor: ParcelFileDescriptor? = null
        var sourceDocument: PdfDocumentU? = null
        var session = 0L
        var outputCommitted = false
        try {
            sourceDescriptor = try {
                contentResolver.openFileDescriptor(sourceUri, "r")
                    ?: return@withContext CompressPdfResult.InvalidDocument
            } catch (_: SecurityException) {
                return@withContext CompressPdfResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext CompressPdfResult.InvalidDocument
            }
            if (sourceDescriptor.statSize != expectedOriginalSizeBytes) {
                return@withContext CompressPdfResult.InvalidDocument
            }
            sourceDocument = try {
                PdfiumCoreU(appContext).newDocument(sourceDescriptor)
            } catch (_: SecurityException) {
                return@withContext CompressPdfResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext CompressPdfResult.InvalidDocument
            }
            if (sourceDocument.getPageCount() != expectedPageCount) {
                return@withContext CompressPdfResult.InvalidDocument
            }
            val attempts = when (request) {
                is PdfCompressionRequest.Quality -> listOf(
                    CompressionAttempt(request.mode.maxImageDimension, request.mode.jpegQuality),
                )
                is PdfCompressionRequest.TargetSize -> TargetCompressionPlanner.attempts
            }
            var bestSize = Long.MAX_VALUE
            var bestRecompressedImages = 0
            for ((attemptIndex, attempt) in attempts.withIndex()) {
                coroutineContext.ensureActive()
                session = NativePdfCompressor.createSession(
                    sourceDocument.mNativeDocPtr,
                    expectedPageCount,
                )
                if (session == 0L) return@withContext CompressPdfResult.Failed
                var recompressedImages = 0
                withContext(Dispatchers.Main.immediate) {
                    onProgress(
                        CompressionProgress(
                            attemptIndex + 1,
                            attempts.size,
                            0,
                            expectedPageCount,
                        ),
                    )
                }
                repeat(expectedPageCount) { pageIndex ->
                    coroutineContext.ensureActive()
                    val compressed = NativePdfCompressor.compressPage(
                        session,
                        pageIndex,
                        attempt.maxImageDimension,
                        attempt.jpegQuality,
                    )
                    if (compressed < 0) return@withContext CompressPdfResult.Failed
                    recompressedImages += compressed
                    withContext(Dispatchers.Main.immediate) {
                        onProgress(
                            CompressionProgress(
                                attemptIndex + 1,
                                attempts.size,
                                pageIndex + 1,
                                expectedPageCount,
                            ),
                        )
                    }
                }
                coroutineContext.ensureActive()
                ParcelFileDescriptor.open(
                    temporary,
                    ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or
                        ParcelFileDescriptor.MODE_READ_WRITE,
                ).use { output ->
                    if (NativePdfCompressor.saveSession(session, output.fd) != 0) {
                        return@withContext CompressPdfResult.Failed
                    }
                }
                NativePdfCompressor.closeSession(session)
                session = 0L
                val candidateSize = temporary.length()
                val stagedPageCount = ParcelFileDescriptor.open(
                    temporary,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                ).use { descriptor -> PdfRenderer(descriptor).use(PdfRenderer::getPageCount) }
                if (stagedPageCount != expectedPageCount || candidateSize <= 0L) {
                    return@withContext CompressPdfResult.Failed
                }
                if (candidateSize < bestSize && candidateSize < expectedOriginalSizeBytes) {
                    temporary.inputStream().use { input ->
                        bestTemporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    bestSize = candidateSize
                    bestRecompressedImages = recompressedImages
                }
                if (targetSize != null && bestSize <= targetSize) break
            }
            if (bestSize == Long.MAX_VALUE) {
                return@withContext CompressPdfResult.NotSmaller(
                    expectedOriginalSizeBytes,
                    temporary.length(),
                    0,
                )
            }
            coroutineContext.ensureActive()
            contentResolver.openOutputStream(outputUri, "wt")?.use { output ->
                bestTemporary.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext CompressPdfResult.Failed
            val published = contentResolver.openFileDescriptor(outputUri, "r")
                ?: return@withContext CompressPdfResult.Failed
            val publishedSize = published.use { descriptor ->
                val size = descriptor.statSize
                val pages = PdfRenderer(descriptor).use(PdfRenderer::getPageCount)
                if (pages != expectedPageCount) return@withContext CompressPdfResult.Failed
                size
            }
            if (publishedSize != bestSize) return@withContext CompressPdfResult.Failed
            coroutineContext.ensureActive()
            outputCommitted = true
            CompressPdfResult.Success(
                expectedPageCount,
                expectedOriginalSizeBytes,
                publishedSize,
                bestRecompressedImages,
                targetSize,
                targetSize == null || publishedSize <= targetSize,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            CompressPdfResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            CompressPdfResult.InsufficientMemory
        } catch (_: LinkageError) {
            CompressPdfResult.Failed
        } catch (_: Exception) {
            CompressPdfResult.Failed
        } finally {
            if (session != 0L) NativePdfCompressor.closeSession(session)
            sourceDocument?.let { runCatching { it.close() } }
            sourceDescriptor?.let { runCatching { it.close() } }
            temporary.delete()
            bestTemporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, contentResolver, outputUri)
        }
    }
}

@Keep
internal object NativePdfCompressor {
    init {
        System.loadLibrary("quietpdf_merge")
    }

    external fun analyzeImages(sourcePointer: Long, expectedPageCount: Int): LongArray?
    external fun createSession(sourcePointer: Long, expectedPageCount: Int): Long
    external fun compressPage(
        sessionPointer: Long,
        pageIndex: Int,
        maxDimension: Int,
        jpegQuality: Int,
    ): Int
    external fun saveSession(sessionPointer: Long, outputFileDescriptor: Int): Int
    external fun closeSession(sessionPointer: Long)

    @JvmStatic
    fun encodeJpeg(
        pixels: ByteBuffer,
        width: Int,
        height: Int,
        stride: Int,
        format: Int,
        maxDimension: Int,
        quality: Int,
    ): ByteArray? {
        val bytesPerPixel = when (format) {
            1 -> 1
            2 -> 3
            3, 4 -> 4
            else -> return null
        }
        if (width <= 0 || height <= 0 || stride < width * bytesPerPixel) return null
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            pixels.position(0)
            if (bytesPerPixel == 4 && stride == width * 4) {
                source.copyPixelsFromBuffer(pixels)
            } else if (bytesPerPixel == 4) {
                val packed = ByteBuffer.allocateDirect(width * height * 4)
                val row = ByteArray(width * 4)
                repeat(height) { rowIndex ->
                    pixels.position(rowIndex * stride)
                    pixels.get(row)
                    packed.put(row)
                }
                packed.flip()
                source.copyPixelsFromBuffer(packed)
            } else {
                val colors = IntArray(width * height)
                repeat(height) { rowIndex ->
                    pixels.position(rowIndex * stride)
                    repeat(width) { columnIndex ->
                        val color = if (format == 1) {
                            val gray = pixels.get().toInt() and 0xff
                            (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
                        } else {
                            val blue = pixels.get().toInt() and 0xff
                            val green = pixels.get().toInt() and 0xff
                            val red = pixels.get().toInt() and 0xff
                            (0xff shl 24) or (red shl 16) or (green shl 8) or blue
                        }
                        colors[rowIndex * width + columnIndex] = color
                    }
                }
                source.setPixels(colors, 0, width, 0, 0, width, height)
            }
            source.setHasAlpha(false)
            val largest = maxOf(width, height)
            val scaled = if (largest > maxDimension) {
                val ratio = maxDimension.toDouble() / largest
                Bitmap.createScaledBitmap(
                    source,
                    (width * ratio).roundToLong().toInt().coerceAtLeast(1),
                    (height * ratio).roundToLong().toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                source
            }
            try {
                return ByteArrayOutputStream().use { output ->
                    if (scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                        output.toByteArray()
                    } else {
                        null
                    }
                }
            } finally {
                if (scaled !== source) scaled.recycle()
            }
        } finally {
            source.recycle()
        }
    }
}
