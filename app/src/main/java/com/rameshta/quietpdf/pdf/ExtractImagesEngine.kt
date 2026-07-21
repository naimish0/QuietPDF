package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

data class EmbeddedImagePreview(
    val index: Int,
    val pageNumber: Int,
    val width: Int,
    val height: Int,
    val bitmap: Bitmap?,
    val extractable: Boolean,
)

data class ExtractImagesAnalysis(
    val pageCount: Int,
    val images: List<EmbeddedImagePreview>,
) {
    val extractableIndices: Set<Int>
        get() = images.filter(EmbeddedImagePreview::extractable).mapTo(linkedSetOf()) { it.index }
}

sealed interface ExtractImagesAnalysisResult {
    data class Ready(val analysis: ExtractImagesAnalysis) : ExtractImagesAnalysisResult
    data object NoImages : ExtractImagesAnalysisResult
    data object PasswordProtected : ExtractImagesAnalysisResult
    data object InvalidDocument : ExtractImagesAnalysisResult
    data object PermissionDenied : ExtractImagesAnalysisResult
    data object InsufficientMemory : ExtractImagesAnalysisResult
}

sealed interface ExtractImagesResult {
    data class Success(val imageCount: Int) : ExtractImagesResult
    data object InvalidSelection : ExtractImagesResult
    data object InvalidDocument : ExtractImagesResult
    data object PasswordProtected : ExtractImagesResult
    data object PermissionDenied : ExtractImagesResult
    data object InsufficientMemory : ExtractImagesResult
    data object Failed : ExtractImagesResult
}

sealed interface ExtractImagesShareResult {
    data class Ready(val files: List<File>) : ExtractImagesShareResult
    data object InvalidSelection : ExtractImagesShareResult
    data object InvalidDocument : ExtractImagesShareResult
    data object PasswordProtected : ExtractImagesShareResult
    data object PermissionDenied : ExtractImagesShareResult
    data object InsufficientMemory : ExtractImagesShareResult
    data object Failed : ExtractImagesShareResult
}

class ExtractImagesEngine(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val shareDirectory = File(appContext.cacheDir, "shared-extracted-images")

    init {
        PDFBoxResourceLoader.init(appContext)
        clearShareFiles()
    }

    suspend fun analyze(sourceUri: Uri): ExtractImagesAnalysisResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) return@withContext ExtractImagesAnalysisResult.PasswordProtected
                    val images = collectImages(document)
                    if (images.isEmpty()) return@withContext ExtractImagesAnalysisResult.NoImages
                    val previews = images.mapIndexed { index, found ->
                        coroutineContext.ensureActive()
                        val bounded = isBounded(found.image)
                        val preview = if (bounded) runCatching { thumbnail(found.image.image) }.getOrNull() else null
                        EmbeddedImagePreview(
                            index = index,
                            pageNumber = found.pageIndex + 1,
                            width = found.image.width,
                            height = found.image.height,
                            bitmap = preview,
                            extractable = bounded && preview != null,
                        )
                    }
                    ExtractImagesAnalysisResult.Ready(
                        ExtractImagesAnalysis(document.numberOfPages, previews),
                    )
                }
            } ?: ExtractImagesAnalysisResult.InvalidDocument
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            ExtractImagesAnalysisResult.PasswordProtected
        } catch (_: SecurityException) {
            ExtractImagesAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ExtractImagesAnalysisResult.InsufficientMemory
        } catch (_: Exception) {
            ExtractImagesAnalysisResult.InvalidDocument
        }
    }

    suspend fun exportToDirectory(
        sourceUri: Uri,
        directoryUri: Uri,
        selectedIndices: Set<Int>,
    ): ExtractImagesResult = withContext(Dispatchers.IO) {
        val created = mutableListOf<Uri>()
        var committed = false
        try {
            withSelectedImages(sourceUri, selectedIndices) { selected ->
                selected.forEachIndexed { outputIndex, found ->
                    coroutineContext.ensureActive()
                    val outputUri = createImageDocument(directoryUri, imageName(outputIndex + 1, found))
                        ?: throw IllegalStateException("Output provider refused image")
                    created += outputUri
                    writePng(found.image, outputUri)
                    if (!validPng(outputUri)) throw IllegalStateException("Invalid image output")
                }
                committed = true
                ExtractImagesResult.Success(selected.size)
            }
        } catch (invalid: InvalidImageSelectionException) {
            ExtractImagesResult.InvalidSelection
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            ExtractImagesResult.PasswordProtected
        } catch (_: ProtectedPdfException) {
            ExtractImagesResult.PasswordProtected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            ExtractImagesResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ExtractImagesResult.InsufficientMemory
        } catch (_: Exception) {
            ExtractImagesResult.Failed
        } finally {
            if (!committed) created.forEach(::deleteDocumentQuietly)
        }
    }

    suspend fun exportToZip(
        sourceUri: Uri,
        outputUri: Uri,
        selectedIndices: Set<Int>,
    ): ExtractImagesResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri) return@withContext ExtractImagesResult.InvalidSelection
        var committed = false
        try {
            val result = withSelectedImages(sourceUri, selectedIndices) { selected ->
                resolver.openOutputStream(outputUri, "wt")?.use { output ->
                    ZipOutputStream(output.buffered()).use { zip ->
                        selected.forEachIndexed { outputIndex, found ->
                            coroutineContext.ensureActive()
                            zip.putNextEntry(ZipEntry(imageName(outputIndex + 1, found)))
                            writePng(found.image, zip)
                            zip.closeEntry()
                        }
                    }
                } ?: throw IllegalStateException("Output provider refused ZIP")
                if (!validZip(outputUri, selected.size)) throw IllegalStateException("Invalid ZIP output")
                committed = true
                ExtractImagesResult.Success(selected.size)
            }
            result
        } catch (invalid: InvalidImageSelectionException) {
            ExtractImagesResult.InvalidSelection
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            ExtractImagesResult.PasswordProtected
        } catch (_: ProtectedPdfException) {
            ExtractImagesResult.PasswordProtected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            ExtractImagesResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ExtractImagesResult.InsufficientMemory
        } catch (_: Exception) {
            ExtractImagesResult.Failed
        } finally {
            if (!committed) cleanupNewPdfOutput(appContext, resolver, outputUri)
        }
    }

    suspend fun prepareShare(
        sourceUri: Uri,
        selectedIndices: Set<Int>,
    ): ExtractImagesShareResult = withContext(Dispatchers.IO) {
        var ready = false
        try {
            clearShareFiles()
            if (!shareDirectory.exists() && !shareDirectory.mkdirs()) {
                return@withContext ExtractImagesShareResult.Failed
            }
            withSelectedImages(sourceUri, selectedIndices) { selected ->
                val files = selected.mapIndexed { outputIndex, found ->
                    coroutineContext.ensureActive()
                    File(shareDirectory, imageName(outputIndex + 1, found)).also { file ->
                        file.outputStream().use { writePng(found.image, it) }
                        if (!validPng(file)) throw IllegalStateException("Invalid shared image")
                    }
                }
                ready = true
                ExtractImagesShareResult.Ready(files)
            }
        } catch (invalid: InvalidImageSelectionException) {
            ExtractImagesShareResult.InvalidSelection
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            ExtractImagesShareResult.PasswordProtected
        } catch (_: ProtectedPdfException) {
            ExtractImagesShareResult.PasswordProtected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            ExtractImagesShareResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ExtractImagesShareResult.InsufficientMemory
        } catch (_: Exception) {
            ExtractImagesShareResult.Failed
        } finally {
            if (!ready) clearShareFiles()
        }
    }

    fun clearShareFiles() {
        shareDirectory.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
        shareDirectory.delete()
    }

    private fun <T> withSelectedImages(
        sourceUri: Uri,
        selectedIndices: Set<Int>,
        block: (List<FoundImage>) -> T,
    ): T {
        if (selectedIndices.isEmpty()) throw InvalidImageSelectionException()
        val input = resolver.openInputStream(sourceUri) ?: throw IllegalStateException("Missing PDF")
        input.use {
            PDDocument.load(it).use { document ->
                if (document.isEncrypted) throw ProtectedPdfException()
                val images = collectImages(document)
                if (selectedIndices.any { index -> index !in images.indices } ||
                    selectedIndices.any { index -> !isBounded(images[index].image) }
                ) throw InvalidImageSelectionException()
                return block(selectedIndices.sorted().map(images::get))
            }
        }
    }

    private fun collectImages(document: PDDocument): List<FoundImage> {
        val found = mutableListOf<FoundImage>()
        val seenImages = IdentityHashMap<COSBase, Boolean>()
        document.pages.forEachIndexed { pageIndex, page ->
            val seenForms = IdentityHashMap<COSBase, Boolean>()
            collectFromResources(page.resources, pageIndex, 0, seenImages, seenForms, found)
        }
        return found
    }

    private fun collectFromResources(
        resources: PDResources?,
        pageIndex: Int,
        depth: Int,
        seenImages: IdentityHashMap<COSBase, Boolean>,
        seenForms: IdentityHashMap<COSBase, Boolean>,
        output: MutableList<FoundImage>,
    ) {
        if (resources == null || depth > MAX_FORM_DEPTH) return
        resources.xObjectNames.forEach { name ->
            val xObject: PDXObject = runCatching { resources.getXObject(name) }.getOrNull() ?: return@forEach
            when (xObject) {
                is PDImageXObject -> if (seenImages.put(xObject.cosObject, true) == null) {
                    output += FoundImage(pageIndex, xObject)
                }
                is PDFormXObject -> if (seenForms.put(xObject.cosObject, true) == null) {
                    collectFromResources(
                        xObject.resources, pageIndex, depth + 1, seenImages, seenForms, output,
                    )
                }
            }
        }
    }

    private fun isBounded(image: PDImageXObject): Boolean {
        val width = image.width.toLong()
        val height = image.height.toLong()
        return width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION &&
            width * height <= MAX_PIXELS
    }

    private fun thumbnail(source: Bitmap): Bitmap {
        try {
            val scale = minOf(1f, PREVIEW_SIDE / maxOf(source.width, source.height).toFloat())
            return if (scale >= 1f) source.copy(Bitmap.Config.ARGB_8888, false) else {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).roundToInt().coerceAtLeast(1),
                    (source.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
            }
        } finally {
            source.recycle()
        }
    }

    private fun writePng(image: PDImageXObject, uri: Uri) {
        resolver.openOutputStream(uri, "wt")?.use { writePng(image, it) }
            ?: throw IllegalStateException("Output provider refused image")
    }

    private fun writePng(image: PDImageXObject, output: java.io.OutputStream) {
        val bitmap = image.image
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalStateException("PNG encoder failed")
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun validPng(uri: Uri): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            options.outWidth > 0 && options.outHeight > 0
        } ?: false
    }.getOrDefault(false)

    private fun validPng(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun validZip(uri: Uri, expectedCount: Int): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var count = 0
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) count++
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                count == expectedCount
            }
        } ?: false
    }.getOrDefault(false)

    private fun createImageDocument(directoryUri: Uri, name: String): Uri? {
        val directory = DocumentsContract.buildDocumentUriUsingTree(
            directoryUri,
            DocumentsContract.getTreeDocumentId(directoryUri),
        )
        return DocumentsContract.createDocument(resolver, directory, "image/png", name)
    }

    private fun deleteDocumentQuietly(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }

    private fun imageName(outputIndex: Int, found: FoundImage): String =
        "image-${outputIndex.toString().padStart(3, '0')}-page-${(found.pageIndex + 1).toString().padStart(3, '0')}.png"

    private data class FoundImage(val pageIndex: Int, val image: PDImageXObject)
    private class InvalidImageSelectionException : Exception()
    private class ProtectedPdfException : Exception()

    companion object {
        private const val PREVIEW_SIDE = 220f
        private const val MAX_DIMENSION = 10_000L
        private const val MAX_PIXELS = 16_000_000L
        private const val MAX_FORM_DEPTH = 20
    }
}
