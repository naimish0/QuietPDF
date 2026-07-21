package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min

sealed interface ImagesToPdfResult {
    data class Success(val pageCount: Int) : ImagesToPdfResult
    data class InvalidImage(val imageIndex: Int) : ImagesToPdfResult
    data object PermissionDenied : ImagesToPdfResult
    data object InsufficientMemory : ImagesToPdfResult
    data object Failed : ImagesToPdfResult
}

enum class ImagePdfPageSize(val shortSide: Int, val longSide: Int) {
    A4(595, 842),
    Letter(612, 792),
}

enum class ImagePdfOrientation { Auto, Portrait, Landscape }

enum class ImagePdfScaleMode { Fit, Fill }

enum class ImagePdfMargin(val points: Float) {
    None(0f),
    Standard(24f),
    Wide(48f),
}

data class ImagePdfLayout(
    val pageSize: ImagePdfPageSize = ImagePdfPageSize.A4,
    val orientation: ImagePdfOrientation = ImagePdfOrientation.Auto,
    val scaleMode: ImagePdfScaleMode = ImagePdfScaleMode.Fit,
    val margin: ImagePdfMargin = ImagePdfMargin.Standard,
)

class ImagesToPdfEngine(
    private val contentResolver: ContentResolver,
    private val cacheDir: File,
) {
    suspend fun create(
        imageUris: List<Uri>,
        outputUri: Uri,
        layout: ImagePdfLayout = ImagePdfLayout(),
    ): ImagesToPdfResult =
        withContext(Dispatchers.IO) {
            if (imageUris.isEmpty()) return@withContext ImagesToPdfResult.Failed
            val temporary = try {
                File.createTempFile("images-to-pdf-", ".pdf", cacheDir)
            } catch (_: Exception) {
                return@withContext ImagesToPdfResult.Failed
            }
            try {
                val document = PdfDocument()
                try {
                    imageUris.forEachIndexed { index, uri ->
                        val bitmap = try {
                            decode(uri)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: SecurityException) {
                            return@withContext ImagesToPdfResult.PermissionDenied
                        } catch (_: OutOfMemoryError) {
                            return@withContext ImagesToPdfResult.InsufficientMemory
                        } catch (_: Exception) {
                            return@withContext ImagesToPdfResult.InvalidImage(index)
                        }
                        try {
                            appendPage(document, bitmap, index + 1, layout)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    temporary.outputStream().use(document::writeTo)
                } finally {
                    document.close()
                }

                val output = contentResolver.openOutputStream(outputUri, "wt")
                    ?: return@withContext ImagesToPdfResult.Failed
                output.use { sink -> temporary.inputStream().use { it.copyTo(sink) } }
                ImagesToPdfResult.Success(imageUris.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                ImagesToPdfResult.PermissionDenied
            } catch (_: OutOfMemoryError) {
                ImagesToPdfResult.InsufficientMemory
            } catch (_: Exception) {
                ImagesToPdfResult.Failed
            } finally {
                temporary.delete()
            }
        }

    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
            val largestSide = maxOf(info.size.width, info.size.height)
            if (largestSide > MaxImageDimension) {
                decoder.setTargetSampleSize(ceil(largestSide / MaxImageDimension.toDouble()).toInt())
            }
        }
    }

    private fun appendPage(
        document: PdfDocument,
        bitmap: Bitmap,
        pageNumber: Int,
        layout: ImagePdfLayout,
    ) {
        val landscape = when (layout.orientation) {
            ImagePdfOrientation.Auto -> bitmap.width > bitmap.height
            ImagePdfOrientation.Portrait -> false
            ImagePdfOrientation.Landscape -> true
        }
        val width = if (landscape) layout.pageSize.longSide else layout.pageSize.shortSide
        val height = if (landscape) layout.pageSize.shortSide else layout.pageSize.longSide
        val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        try {
            page.canvas.drawColor(Color.WHITE)
            val availableWidth = width - 2f * layout.margin.points
            val availableHeight = height - 2f * layout.margin.points
            val widthScale = availableWidth / bitmap.width
            val heightScale = availableHeight / bitmap.height
            val scale = when (layout.scaleMode) {
                ImagePdfScaleMode.Fit -> min(widthScale, heightScale)
                ImagePdfScaleMode.Fill -> maxOf(widthScale, heightScale)
            }
            val imageWidth = bitmap.width * scale
            val imageHeight = bitmap.height * scale
            val left = (width - imageWidth) / 2f
            val top = (height - imageHeight) / 2f
            val canvas = page.canvas
            val checkpoint = canvas.save()
            try {
                canvas.clipRect(
                    layout.margin.points,
                    layout.margin.points,
                    width - layout.margin.points,
                    height - layout.margin.points,
                )
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(left, top, left + imageWidth, top + imageHeight),
                    null,
                )
            } finally {
                canvas.restoreToCount(checkpoint)
            }
        } finally {
            document.finishPage(page)
        }
    }

    private companion object {
        const val MaxImageDimension = 2400
    }
}
