package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.ensureActive
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PdfPageRenderer internal constructor(
    private val openDescriptor: (Uri) -> android.os.ParcelFileDescriptor?,
) {
    constructor(contentResolver: ContentResolver) : this(
        openDescriptor = { uri -> contentResolver.openFileDescriptor(uri, "r") },
    )

    suspend fun render(uri: Uri, pageIndex: Int, requestedWidth: Int): Bitmap {
        require(pageIndex >= 0) { "Page index must be non-negative" }
        val targetWidth = requestedWidth.coerceIn(MinRenderWidth, MaxRenderWidth)
        coroutineContext.ensureActive()

        val descriptor = openDescriptor(uri)
            ?: throw IOException("Document is unavailable")

        return descriptor.use { parcelFileDescriptor ->
            PdfRenderer(parcelFileDescriptor).use { renderer ->
                require(pageIndex < renderer.pageCount) { "Page index is out of bounds" }
                renderer.openPage(pageIndex).use { page ->
                    val scale = targetWidth.toFloat() / page.width.toFloat()
                    var width = targetWidth
                    var height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val pixelCount = width.toLong() * height.toLong()
                    if (pixelCount > MaxPagePixels) {
                        val reduction = sqrt(MaxPagePixels.toDouble() / pixelCount.toDouble())
                        width = (width * reduction).roundToInt().coerceAtLeast(1)
                        height = (height * reduction).roundToInt().coerceAtLeast(1)
                    }

                    coroutineContext.ensureActive()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        val renderScale = width.toFloat() / page.width.toFloat()
                        page.render(
                            bitmap,
                            null,
                            Matrix().apply { postScale(renderScale, renderScale) },
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )
                        coroutineContext.ensureActive()
                        bitmap
                    } catch (throwable: Throwable) {
                        bitmap.recycle()
                        throw throwable
                    }
                }
            }
        }
    }

    private companion object {
        const val MinRenderWidth = 320
        const val MaxRenderWidth = 2_048
        const val MaxPagePixels = 12_000_000L
    }
}
