package com.rameshta.quietpdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt

enum class ScannerColorMode { Color, Grayscale, BlackAndWhite }

data class ScannerEnhancementSettings(
    val mode: ScannerColorMode = ScannerColorMode.Color,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val shadowReduction: Boolean = true,
) {
    fun normalized(): ScannerEnhancementSettings = copy(
        brightness = brightness.coerceIn(MinBrightness, MaxBrightness),
        contrast = contrast.coerceIn(MinContrast, MaxContrast),
    )

    companion object {
        const val MinBrightness = -0.4f
        const val MaxBrightness = 0.4f
        const val MinContrast = 0.6f
        const val MaxContrast = 1.8f
    }
}

sealed interface ScannerEnhancementPreviewResult {
    data class Ready(val bitmap: Bitmap) : ScannerEnhancementPreviewResult
    data object InsufficientMemory : ScannerEnhancementPreviewResult
    data object Failed : ScannerEnhancementPreviewResult
}

sealed interface ScannerEnhancementFileResult {
    data class Ready(val file: File) : ScannerEnhancementFileResult
    data object InvalidImage : ScannerEnhancementFileResult
    data object InsufficientMemory : ScannerEnhancementFileResult
    data object Failed : ScannerEnhancementFileResult
}

class ScannerEnhancementEngine(private val cacheDir: File) {
    suspend fun enhancePreview(
        source: Bitmap,
        settings: ScannerEnhancementSettings,
    ): ScannerEnhancementPreviewResult = withContext(Dispatchers.Default) {
        try {
            ScannerEnhancementPreviewResult.Ready(process(source, settings.normalized()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OutOfMemoryError) {
            ScannerEnhancementPreviewResult.InsufficientMemory
        } catch (_: Exception) {
            ScannerEnhancementPreviewResult.Failed
        }
    }

    suspend fun enhanceFile(
        inputFile: File,
        settings: ScannerEnhancementSettings,
    ): ScannerEnhancementFileResult = withContext(Dispatchers.IO) {
        if (!inputFile.isFile || inputFile.length() <= 0L) {
            return@withContext ScannerEnhancementFileResult.InvalidImage
        }
        var source: Bitmap? = null
        var enhanced: Bitmap? = null
        var output: File? = null
        var keepOutput = false
        try {
            source = ImageDecoder.decodeBitmap(ImageDecoder.createSource(inputFile)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
            }
            enhanced = process(source, settings.normalized())
            val blackAndWhite = settings.mode == ScannerColorMode.BlackAndWhite
            output = File.createTempFile(
                "scanner-enhanced-",
                if (blackAndWhite) ".png" else ".jpg",
                cacheDir,
            )
            val written = output.outputStream().use { stream ->
                if (blackAndWhite) enhanced.compress(Bitmap.CompressFormat.PNG, 100, stream)
                else enhanced.compress(Bitmap.CompressFormat.JPEG, JpegQuality, stream)
            }
            if (!written || output.length() <= 0L) {
                ScannerEnhancementFileResult.Failed
            } else {
                keepOutput = true
                ScannerEnhancementFileResult.Ready(output)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OutOfMemoryError) {
            ScannerEnhancementFileResult.InsufficientMemory
        } catch (_: Exception) {
            ScannerEnhancementFileResult.InvalidImage
        } finally {
            enhanced?.recycle()
            source?.recycle()
            if (!keepOutput) output?.delete()
        }
    }

    private suspend fun process(
        source: Bitmap,
        settings: ScannerEnhancementSettings,
    ): Bitmap {
        require(source.width > 0 && source.height > 0)
        val illumination = if (settings.shadowReduction) estimateIllumination(source) else null
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val row = IntArray(source.width)
        try {
            for (y in 0 until source.height) {
                currentCoroutineContext().ensureActive()
                source.getPixels(row, 0, source.width, 0, y, source.width, 1)
                for (x in row.indices) {
                    val color = row[x]
                    val illuminationScale = illumination?.scaleAt(x, y, source.width, source.height) ?: 1f
                    var red = Color.red(color) * illuminationScale
                    var green = Color.green(color) * illuminationScale
                    var blue = Color.blue(color) * illuminationScale
                    val brightnessOffset = settings.brightness * 255f
                    red = ((red - 128f) * settings.contrast + 128f + brightnessOffset).coerceIn(0f, 255f)
                    green = ((green - 128f) * settings.contrast + 128f + brightnessOffset).coerceIn(0f, 255f)
                    blue = ((blue - 128f) * settings.contrast + 128f + brightnessOffset).coerceIn(0f, 255f)
                    row[x] = when (settings.mode) {
                        ScannerColorMode.Color -> Color.rgb(red.roundToInt(), green.roundToInt(), blue.roundToInt())
                        ScannerColorMode.Grayscale -> {
                            val gray = luminance(red, green, blue).roundToInt().coerceIn(0, 255)
                            Color.rgb(gray, gray, gray)
                        }
                        ScannerColorMode.BlackAndWhite -> {
                            val value = if (luminance(red, green, blue) >= BlackWhiteThreshold) 255 else 0
                            Color.rgb(value, value, value)
                        }
                    }
                }
                output.setPixels(row, 0, source.width, 0, y, source.width, 1)
            }
            return output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }

    private suspend fun estimateIllumination(source: Bitmap): IlluminationGrid {
        val values = FloatArray(GridSize * GridSize)
        val cellWidth = source.width / GridSize.toFloat()
        val cellHeight = source.height / GridSize.toFloat()
        for (gridY in 0 until GridSize) {
            currentCoroutineContext().ensureActive()
            for (gridX in 0 until GridSize) {
                val startX = floor(gridX * cellWidth).toInt()
                val endX = floor((gridX + 1) * cellWidth).toInt().coerceAtMost(source.width)
                val startY = floor(gridY * cellHeight).toInt()
                val endY = floor((gridY + 1) * cellHeight).toInt().coerceAtMost(source.height)
                var brightSum = 0f
                var brightCount = 0
                var allSum = 0f
                var allCount = 0
                val step = maxOf(1, minOf(endX - startX, endY - startY) / 24)
                for (y in startY until endY step step) {
                    for (x in startX until endX step step) {
                        val color = source.getPixel(x, y)
                        val light = luminance(
                            Color.red(color).toFloat(),
                            Color.green(color).toFloat(),
                            Color.blue(color).toFloat(),
                        )
                        allSum += light
                        allCount++
                        if (light >= BrightBackgroundFloor) {
                            brightSum += light
                            brightCount++
                        }
                    }
                }
                values[gridY * GridSize + gridX] = when {
                    brightCount > 0 -> brightSum / brightCount
                    allCount > 0 -> allSum / allCount
                    else -> TargetBackground
                }
            }
        }
        return IlluminationGrid(values)
    }

    private data class IlluminationGrid(val values: FloatArray) {
        fun scaleAt(x: Int, y: Int, width: Int, height: Int): Float {
            val gridX = if (width <= 1) 0f else x * (GridSize - 1f) / (width - 1f)
            val gridY = if (height <= 1) 0f else y * (GridSize - 1f) / (height - 1f)
            val left = floor(gridX).toInt().coerceIn(0, GridSize - 1)
            val top = floor(gridY).toInt().coerceIn(0, GridSize - 1)
            val right = (left + 1).coerceAtMost(GridSize - 1)
            val bottom = (top + 1).coerceAtMost(GridSize - 1)
            val horizontal = gridX - left
            val vertical = gridY - top
            val upper = values[top * GridSize + left] * (1f - horizontal) +
                values[top * GridSize + right] * horizontal
            val lower = values[bottom * GridSize + left] * (1f - horizontal) +
                values[bottom * GridSize + right] * horizontal
            val localBackground = upper * (1f - vertical) + lower * vertical
            return (TargetBackground / localBackground.coerceAtLeast(32f)).coerceIn(0.85f, 1.5f)
        }
    }

    private fun luminance(red: Float, green: Float, blue: Float): Float =
        0.299f * red + 0.587f * green + 0.114f * blue

    private companion object {
        const val GridSize = 12
        const val BrightBackgroundFloor = 145f
        const val TargetBackground = 238f
        const val BlackWhiteThreshold = 168f
        const val JpegQuality = 94
    }
}
