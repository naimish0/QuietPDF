package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

data class ScannerCapturePreview(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

sealed interface ScannerPreviewResult {
    data class Ready(val preview: ScannerCapturePreview) : ScannerPreviewResult
    data object InvalidImage : ScannerPreviewResult
    data object InsufficientMemory : ScannerPreviewResult
    data object Failed : ScannerPreviewResult
}

sealed interface ScannerPdfResult {
    data object Success : ScannerPdfResult
    data object InvalidImage : ScannerPdfResult
    data object PermissionDenied : ScannerPdfResult
    data object InsufficientMemory : ScannerPdfResult
    data object Failed : ScannerPdfResult
}

object ScannerPreviewSampling {
    fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        val largest = maxOf(width, height)
        return if (largest <= maxDimension) 1
        else ceil(largest / maxDimension.toDouble()).toInt().coerceAtLeast(1)
    }
}

class ScannerCaptureEngine(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val imagesToPdfEngine = ImagesToPdfEngine(contentResolver, appContext.cacheDir)

    fun createCaptureFile(): File = File.createTempFile("scanner-capture-", ".jpg", appContext.cacheDir)

    suspend fun preparePreview(captureFile: File): ScannerPreviewResult =
        withContext(Dispatchers.IO) {
            if (!captureFile.isFile || captureFile.length() <= 0L) {
                return@withContext ScannerPreviewResult.InvalidImage
            }
            try {
                var sourceWidth = 0
                var sourceHeight = 0
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(captureFile)) {
                        decoder, info, _ ->
                    sourceWidth = info.size.width
                    sourceHeight = info.size.height
                    if (sourceWidth <= 0 || sourceHeight <= 0) {
                        throw IllegalArgumentException("Invalid captured image dimensions")
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                    decoder.setTargetSampleSize(
                        ScannerPreviewSampling.sampleSize(
                            sourceWidth,
                            sourceHeight,
                            PreviewMaxDimension,
                        ),
                    )
                }
                ScannerPreviewResult.Ready(
                    ScannerCapturePreview(bitmap, sourceWidth, sourceHeight),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                ScannerPreviewResult.InsufficientMemory
            } catch (_: Exception) {
                ScannerPreviewResult.InvalidImage
            }
        }

    suspend fun createSinglePagePdf(captureFile: File, outputUri: Uri): ScannerPdfResult {
        if (!captureFile.isFile || captureFile.length() <= 0L) return ScannerPdfResult.InvalidImage
        return try {
            when (
                val result = imagesToPdfEngine.create(
                    imageUris = listOf(Uri.fromFile(captureFile)),
                    outputUri = outputUri,
                    layout = ImagePdfLayout(
                        orientation = ImagePdfOrientation.Auto,
                        scaleMode = ImagePdfScaleMode.Fit,
                        margin = ImagePdfMargin.None,
                    ),
                )
            ) {
                is ImagesToPdfResult.Success -> validatePublishedPdf(outputUri)
                is ImagesToPdfResult.InvalidImage -> cleanupFailedOutput(
                    outputUri,
                    ScannerPdfResult.InvalidImage,
                )
                ImagesToPdfResult.PermissionDenied -> cleanupFailedOutput(
                    outputUri,
                    ScannerPdfResult.PermissionDenied,
                )
                ImagesToPdfResult.InsufficientMemory -> cleanupFailedOutput(
                    outputUri,
                    ScannerPdfResult.InsufficientMemory,
                )
                ImagesToPdfResult.Failed -> cleanupFailedOutput(outputUri, ScannerPdfResult.Failed)
            }
        } catch (cancelled: CancellationException) {
            cleanupNewPdfOutput(appContext, contentResolver, outputUri)
            throw cancelled
        }
    }

    fun discard(captureFile: File?) {
        captureFile?.takeIf(File::exists)?.delete()
    }

    private suspend fun validatePublishedPdf(outputUri: Uri): ScannerPdfResult =
        withContext(Dispatchers.IO) {
            try {
                val descriptor = contentResolver.openFileDescriptor(outputUri, "r")
                    ?: return@withContext cleanupFailedOutput(outputUri, ScannerPdfResult.Failed)
                val valid = descriptor.use { PdfRenderer(it).use { renderer -> renderer.pageCount == 1 } }
                if (valid) ScannerPdfResult.Success
                else cleanupFailedOutput(outputUri, ScannerPdfResult.Failed)
            } catch (cancelled: CancellationException) {
                cleanupNewPdfOutput(appContext, contentResolver, outputUri)
                throw cancelled
            } catch (_: SecurityException) {
                cleanupFailedOutput(outputUri, ScannerPdfResult.PermissionDenied)
            } catch (_: OutOfMemoryError) {
                cleanupFailedOutput(outputUri, ScannerPdfResult.InsufficientMemory)
            } catch (_: Exception) {
                cleanupFailedOutput(outputUri, ScannerPdfResult.Failed)
            }
        }

    private fun cleanupFailedOutput(outputUri: Uri, result: ScannerPdfResult): ScannerPdfResult {
        cleanupNewPdfOutput(appContext, contentResolver, outputUri)
        return result
    }

    private companion object {
        const val PreviewMaxDimension = 1400
    }
}
