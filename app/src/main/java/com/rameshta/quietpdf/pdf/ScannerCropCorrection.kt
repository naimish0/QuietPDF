package com.rameshta.quietpdf.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class ScannerCropPoint(val x: Float, val y: Float) {
    fun clamped(): ScannerCropPoint = ScannerCropPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

data class ScannerCropSelection(
    val topLeft: ScannerCropPoint,
    val topRight: ScannerCropPoint,
    val bottomRight: ScannerCropPoint,
    val bottomLeft: ScannerCropPoint,
) {
    val points: List<ScannerCropPoint>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun moveCorner(index: Int, point: ScannerCropPoint): ScannerCropSelection {
        val candidate = withCorner(index, point.clamped()) ?: return this
        return candidate.takeIf(ScannerCropGeometry::isValid) ?: this
    }

    fun moveCornerConstrained(index: Int, point: ScannerCropPoint): ScannerCropSelection {
        val start = points.getOrNull(index) ?: return this
        val target = point.clamped()
        val direct = withCorner(index, target) ?: return this
        if (ScannerCropGeometry.isValid(direct)) return direct

        var validFraction = 0f
        var invalidFraction = 1f
        var best = this
        repeat(14) {
            val fraction = (validFraction + invalidFraction) / 2f
            val candidate = withCorner(
                index,
                ScannerCropPoint(
                    x = start.x + (target.x - start.x) * fraction,
                    y = start.y + (target.y - start.y) * fraction,
                ),
            ) ?: return this
            if (ScannerCropGeometry.isValid(candidate)) {
                validFraction = fraction
                best = candidate
            } else {
                invalidFraction = fraction
            }
        }
        return best
    }

    private fun withCorner(index: Int, point: ScannerCropPoint): ScannerCropSelection? = when (index) {
        0 -> copy(topLeft = point)
        1 -> copy(topRight = point)
        2 -> copy(bottomRight = point)
        3 -> copy(bottomLeft = point)
        else -> null
    }

    companion object {
        fun fullImage(inset: Float = 0f): ScannerCropSelection {
            val safeInset = inset.coerceIn(0f, 0.2f)
            return ScannerCropSelection(
                topLeft = ScannerCropPoint(safeInset, safeInset),
                topRight = ScannerCropPoint(1f - safeInset, safeInset),
                bottomRight = ScannerCropPoint(1f - safeInset, 1f - safeInset),
                bottomLeft = ScannerCropPoint(safeInset, 1f - safeInset),
            )
        }
    }
}

data class ScannerCropSuggestion(
    val selection: ScannerCropSelection,
    val detected: Boolean,
)

data class ScannerCropOutputSize(val width: Int, val height: Int)

object ScannerCropGeometry {
    fun isValid(selection: ScannerCropSelection): Boolean {
        val points = selection.points
        if (points.any { it.x !in 0f..1f || it.y !in 0f..1f }) return false
        val crossProducts = points.indices.map { index ->
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val c = points[(index + 2) % points.size]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        if (crossProducts.any { it <= 0.0005f }) return false
        val area = abs(
            points.indices.sumOf { index ->
                val current = points[index]
                val next = points[(index + 1) % points.size]
                (current.x * next.y - next.x * current.y).toDouble()
            }.toFloat(),
        ) / 2f
        return area >= MinimumArea
    }

    fun outputSize(
        sourceWidth: Int,
        sourceHeight: Int,
        selection: ScannerCropSelection,
        maxDimension: Int = MaxCorrectedDimension,
    ): ScannerCropOutputSize? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || maxDimension <= 0 || !isValid(selection)) {
            return null
        }
        fun distance(first: ScannerCropPoint, second: ScannerCropPoint): Double = hypot(
            ((second.x - first.x) * sourceWidth).toDouble(),
            ((second.y - first.y) * sourceHeight).toDouble(),
        )
        val width = max(
            distance(selection.topLeft, selection.topRight),
            distance(selection.bottomLeft, selection.bottomRight),
        )
        val height = max(
            distance(selection.topLeft, selection.bottomLeft),
            distance(selection.topRight, selection.bottomRight),
        )
        if (width < 2.0 || height < 2.0) return null
        val scale = minOf(1.0, maxDimension / max(width, height))
        return ScannerCropOutputSize(
            width = (width * scale).roundToInt().coerceAtLeast(2),
            height = (height * scale).roundToInt().coerceAtLeast(2),
        )
    }

    const val MaxCorrectedDimension = 3000
    private const val MinimumArea = 0.04f
}

object ScannerDocumentDetector {
    fun detect(bitmap: Bitmap): ScannerCropSuggestion {
        if (bitmap.width < 8 || bitmap.height < 8) {
            return ScannerCropSuggestion(ScannerCropSelection.fullImage(), false)
        }
        val scale = minOf(1f, DetectionMaxDimension / max(bitmap.width, bitmap.height).toFloat())
        val sample = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(8),
                (bitmap.height * scale).roundToInt().coerceAtLeast(8),
                true,
            )
        } else {
            bitmap
        }
        return try {
            detectInSample(sample)
        } finally {
            if (sample !== bitmap) sample.recycle()
        }
    }

    private fun detectInSample(bitmap: Bitmap): ScannerCropSuggestion {
        val width = bitmap.width
        val height = bitmap.height
        val cornerSize = minOf(width, height, 12).coerceAtLeast(2)
        val backgrounds = ArrayList<Int>(cornerSize * cornerSize * 4)
        fun collect(startX: Int, startY: Int) {
            for (y in startY until startY + cornerSize) {
                for (x in startX until startX + cornerSize) backgrounds += bitmap.getPixel(x, y)
            }
        }
        collect(0, 0)
        collect(width - cornerSize, 0)
        collect(0, height - cornerSize)
        collect(width - cornerSize, height - cornerSize)
        val backgroundRed = backgrounds.map(Color::red).average()
        val backgroundGreen = backgrounds.map(Color::green).average()
        val backgroundBlue = backgrounds.map(Color::blue).average()
        val backgroundVariation = backgrounds.maxOf { color ->
            colorDistance(color, backgroundRed, backgroundGreen, backgroundBlue)
        }
        val threshold = (backgroundVariation + 24.0).coerceIn(28.0, 96.0)

        var foregroundCount = 0
        var topLeftScore = Float.POSITIVE_INFINITY
        var topRightScore = Float.NEGATIVE_INFINITY
        var bottomRightScore = Float.NEGATIVE_INFINITY
        var bottomLeftScore = Float.POSITIVE_INFINITY
        var topLeft = ScannerCropPoint(0f, 0f)
        var topRight = ScannerCropPoint(1f, 0f)
        var bottomRight = ScannerCropPoint(1f, 1f)
        var bottomLeft = ScannerCropPoint(0f, 1f)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                if (colorDistance(bitmap.getPixel(x, y), backgroundRed, backgroundGreen, backgroundBlue) < threshold) {
                    continue
                }
                foregroundCount++
                val normalizedX = x / (width - 1f)
                val normalizedY = y / (height - 1f)
                val sum = normalizedX + normalizedY
                val difference = normalizedX - normalizedY
                if (sum < topLeftScore) {
                    topLeftScore = sum
                    topLeft = ScannerCropPoint(normalizedX, normalizedY)
                }
                if (difference > topRightScore) {
                    topRightScore = difference
                    topRight = ScannerCropPoint(normalizedX, normalizedY)
                }
                if (sum > bottomRightScore) {
                    bottomRightScore = sum
                    bottomRight = ScannerCropPoint(normalizedX, normalizedY)
                }
                if (difference < bottomLeftScore) {
                    bottomLeftScore = difference
                    bottomLeft = ScannerCropPoint(normalizedX, normalizedY)
                }
            }
        }
        val candidate = ScannerCropSelection(topLeft, topRight, bottomRight, bottomLeft)
        val foregroundRatio = foregroundCount.toFloat() / (width * height)
        return if (foregroundRatio in MinimumForegroundRatio..MaximumForegroundRatio &&
            ScannerCropGeometry.isValid(candidate)
        ) {
            ScannerCropSuggestion(candidate, true)
        } else {
            ScannerCropSuggestion(ScannerCropSelection.fullImage(), false)
        }
    }

    private fun colorDistance(color: Int, red: Double, green: Double, blue: Double): Double =
        (abs(Color.red(color) - red) + abs(Color.green(color) - green) + abs(Color.blue(color) - blue)) / 3.0

    private const val DetectionMaxDimension = 256f
    private const val MinimumForegroundRatio = 0.08f
    private const val MaximumForegroundRatio = 0.94f
}

sealed interface ScannerCropResult {
    data class Ready(val file: File) : ScannerCropResult
    data object InvalidCrop : ScannerCropResult
    data object InvalidImage : ScannerCropResult
    data object InsufficientMemory : ScannerCropResult
    data object Failed : ScannerCropResult
}

sealed interface ScannerCropPreviewResult {
    data class Ready(val bitmap: Bitmap) : ScannerCropPreviewResult
    data object InvalidCrop : ScannerCropPreviewResult
    data object InsufficientMemory : ScannerCropPreviewResult
    data object Failed : ScannerCropPreviewResult
}

class ScannerCropCorrectionEngine(private val cacheDir: File) {
    suspend fun correctPreview(
        source: Bitmap,
        selection: ScannerCropSelection,
    ): ScannerCropPreviewResult = withContext(Dispatchers.Default) {
        try {
            val corrected = correctBitmap(source, selection, PreviewMaxDimension)
                ?: return@withContext ScannerCropPreviewResult.InvalidCrop
            ScannerCropPreviewResult.Ready(corrected)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OutOfMemoryError) {
            ScannerCropPreviewResult.InsufficientMemory
        } catch (_: Exception) {
            ScannerCropPreviewResult.Failed
        }
    }

    suspend fun correct(captureFile: File, selection: ScannerCropSelection): ScannerCropResult =
        withContext(Dispatchers.IO) {
            if (!captureFile.isFile || captureFile.length() <= 0L) {
                return@withContext ScannerCropResult.InvalidImage
            }
            if (!ScannerCropGeometry.isValid(selection)) return@withContext ScannerCropResult.InvalidCrop
            var source: Bitmap? = null
            var corrected: Bitmap? = null
            var output: File? = null
            var keepOutput = false
            try {
                source = ImageDecoder.decodeBitmap(ImageDecoder.createSource(captureFile)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                    val largest = max(info.size.width, info.size.height)
                    if (largest > ScannerCropGeometry.MaxCorrectedDimension) {
                        decoder.setTargetSampleSize(
                            ceil(largest / ScannerCropGeometry.MaxCorrectedDimension.toDouble()).toInt(),
                        )
                    }
                }
                corrected = correctBitmap(
                    source,
                    selection,
                    ScannerCropGeometry.MaxCorrectedDimension,
                ) ?: return@withContext ScannerCropResult.InvalidCrop
                output = File.createTempFile("scanner-corrected-", ".jpg", cacheDir)
                val written = output.outputStream().use { stream ->
                    corrected.compress(Bitmap.CompressFormat.JPEG, JpegQuality, stream)
                }
                if (!written || output.length() <= 0L) {
                    ScannerCropResult.Failed
                } else {
                    keepOutput = true
                    ScannerCropResult.Ready(output)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                ScannerCropResult.InsufficientMemory
            } catch (_: Exception) {
                ScannerCropResult.InvalidImage
            } finally {
                corrected?.recycle()
                source?.recycle()
                if (!keepOutput) output?.delete()
            }
        }

    private fun correctBitmap(
        source: Bitmap,
        selection: ScannerCropSelection,
        maxDimension: Int,
    ): Bitmap? {
        val size = ScannerCropGeometry.outputSize(
            source.width,
            source.height,
            selection,
            maxDimension,
        ) ?: return null
        val corrected = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val sourcePoints = floatArrayOf(
            selection.topLeft.x * source.width,
            selection.topLeft.y * source.height,
            selection.topRight.x * source.width,
            selection.topRight.y * source.height,
            selection.bottomRight.x * source.width,
            selection.bottomRight.y * source.height,
            selection.bottomLeft.x * source.width,
            selection.bottomLeft.y * source.height,
        )
        val destinationPoints = floatArrayOf(
            0f, 0f,
            size.width.toFloat(), 0f,
            size.width.toFloat(), size.height.toFloat(),
            0f, size.height.toFloat(),
        )
        val transform = Matrix()
        if (!transform.setPolyToPoly(sourcePoints, 0, destinationPoints, 0, 4)) {
            corrected.recycle()
            return null
        }
        Canvas(corrected).apply {
            drawColor(Color.WHITE)
            drawBitmap(
                source,
                transform,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
            )
        }
        return corrected
    }

    private companion object {
        const val PreviewMaxDimension = 1400
        const val JpegQuality = 94
    }
}
