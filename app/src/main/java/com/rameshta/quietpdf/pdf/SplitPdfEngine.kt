package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.annotation.Keep
import io.legere.pdfiumandroid.core.unlocked.PdfDocumentU
import io.legere.pdfiumandroid.core.unlocked.PdfiumCoreU
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface SplitPdfResult {
    data class Success(val outputs: List<SplitPdfOutput>) : SplitPdfResult {
        val outputCount: Int get() = outputs.size
    }
    data object InvalidDocument : SplitPdfResult
    data object InvalidPlan : SplitPdfResult
    data object PermissionDenied : SplitPdfResult
    data object InsufficientMemory : SplitPdfResult
    data object Failed : SplitPdfResult
}

data class SplitPdfOutput(val uri: Uri, val pageCount: Int)

class SplitPdfEngine(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    suspend fun split(
        sourceUri: Uri,
        outputDirectoryUri: Uri,
        sourceDisplayName: String?,
        ranges: List<SplitPageRange>,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): SplitPdfResult = splitInternal(
        sourceUri = sourceUri,
        ranges = ranges,
        outputName = { index, range -> outputName(sourceDisplayName, index, ranges.size, range) },
        createOutput = { name -> createOutputDocument(outputDirectoryUri, name) },
        deleteOutput = { uri -> DocumentsContract.deleteDocument(contentResolver, uri) },
        onProgress = onProgress,
    )

    internal suspend fun splitToFiles(
        sourceUri: Uri,
        outputFiles: List<File>,
        ranges: List<SplitPageRange>,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): SplitPdfResult {
        if (outputFiles.size != ranges.size || outputFiles.distinct().size != outputFiles.size ||
            outputFiles.any { Uri.fromFile(it) == sourceUri }
        ) {
            return SplitPdfResult.InvalidPlan
        }
        var nextOutput = 0
        return splitInternal(
            sourceUri = sourceUri,
            ranges = ranges,
            outputName = { index, _ -> outputFiles[index].name },
            createOutput = { Uri.fromFile(outputFiles[nextOutput++]) },
            deleteOutput = { uri -> File(requireNotNull(uri.path)).delete() },
            onProgress = onProgress,
        )
    }

    private suspend fun splitInternal(
        sourceUri: Uri,
        ranges: List<SplitPageRange>,
        outputName: (Int, SplitPageRange) -> String,
        createOutput: (String) -> Uri?,
        deleteOutput: (Uri) -> Boolean,
        onProgress: suspend (Int, Int) -> Unit,
    ): SplitPdfResult = withContext(Dispatchers.IO) {
        val temporaryFiles = mutableListOf<File>()
        val createdOutputs = mutableListOf<Uri>()
        var sourceDescriptor: ParcelFileDescriptor? = null
        var sourceDocument: PdfDocumentU? = null
        try {
            sourceDescriptor = try {
                contentResolver.openFileDescriptor(sourceUri, "r")
                    ?: return@withContext SplitPdfResult.InvalidDocument
            } catch (_: SecurityException) {
                return@withContext SplitPdfResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext SplitPdfResult.InvalidDocument
            }
            sourceDocument = try {
                PdfiumCoreU(appContext).newDocument(sourceDescriptor)
            } catch (_: SecurityException) {
                return@withContext SplitPdfResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext SplitPdfResult.InvalidDocument
            }
            val sourcePageCount = sourceDocument.getPageCount()
            if (!isCompletePlan(ranges, sourcePageCount)) {
                return@withContext SplitPdfResult.InvalidPlan
            }

            ranges.forEachIndexed { index, range ->
                coroutineContext.ensureActive()
                val temporary = File.createTempFile("split-pdf-", ".pdf", appContext.cacheDir)
                temporaryFiles += temporary
                ParcelFileDescriptor.open(
                    temporary,
                    ParcelFileDescriptor.MODE_CREATE or
                        ParcelFileDescriptor.MODE_TRUNCATE or
                        ParcelFileDescriptor.MODE_READ_WRITE,
                ).use { output ->
                    val nativeResult = NativePdfSplitter.splitPart(
                        sourcePointer = sourceDocument.mNativeDocPtr,
                        firstPageIndex = range.firstPageIndex,
                        pageCount = range.pageCount,
                        outputFileDescriptor = output.fd,
                    )
                    if (nativeResult != 0) return@withContext SplitPdfResult.Failed
                }
                val writtenPages = ParcelFileDescriptor.open(
                    temporary,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                ).use { descriptor -> PdfRenderer(descriptor).use(PdfRenderer::getPageCount) }
                if (writtenPages != range.pageCount) return@withContext SplitPdfResult.Failed
                onProgress(index + 1, ranges.size)
            }

            coroutineContext.ensureActive()
            ranges.forEachIndexed { index, range ->
                coroutineContext.ensureActive()
                val destination = createOutput(outputName(index, range))
                    ?: throw OutputFailureException()
                createdOutputs += destination
                contentResolver.openOutputStream(destination, "wt")?.use { output ->
                    temporaryFiles[index].inputStream().use { input -> input.copyTo(output) }
                } ?: throw OutputFailureException()
                val publishedPages = contentResolver.openFileDescriptor(destination, "r")?.use {
                    PdfRenderer(it).use(PdfRenderer::getPageCount)
                } ?: throw OutputFailureException()
                if (publishedPages != range.pageCount) throw OutputFailureException()
            }
            SplitPdfResult.Success(
                createdOutputs.zip(ranges) { uri, range ->
                    SplitPdfOutput(uri, range.pageCount)
                },
            )
        } catch (cancelled: CancellationException) {
            createdOutputs.asReversed().forEach { runCatching { deleteOutput(it) } }
            throw cancelled
        } catch (_: SecurityException) {
            createdOutputs.asReversed().forEach { runCatching { deleteOutput(it) } }
            SplitPdfResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            createdOutputs.asReversed().forEach { runCatching { deleteOutput(it) } }
            SplitPdfResult.InsufficientMemory
        } catch (_: LinkageError) {
            createdOutputs.asReversed().forEach { runCatching { deleteOutput(it) } }
            SplitPdfResult.Failed
        } catch (_: OutputFailureException) {
            createdOutputs.asReversed().forEach { runCatching { deleteOutput(it) } }
            SplitPdfResult.Failed
        } catch (_: Exception) {
            createdOutputs.asReversed().forEach { runCatching { deleteOutput(it) } }
            SplitPdfResult.Failed
        } finally {
            sourceDocument?.let { runCatching { it.close() } }
            sourceDescriptor?.let { runCatching { it.close() } }
            temporaryFiles.forEach { it.delete() }
        }
    }

    private fun createOutputDocument(directoryUri: Uri, name: String): Uri? {
        val directoryDocument = DocumentsContract.buildDocumentUriUsingTree(
            directoryUri,
            DocumentsContract.getTreeDocumentId(directoryUri),
        )
        return DocumentsContract.createDocument(
            contentResolver,
            directoryDocument,
            "application/pdf",
            name,
        )
    }

    private fun isCompletePlan(ranges: List<SplitPageRange>, pageCount: Int): Boolean {
        if (pageCount < 2 || ranges.size < 2) return false
        var expectedFirst = 0
        ranges.forEach { range ->
            if (range.firstPageIndex != expectedFirst || range.lastPageIndex < range.firstPageIndex ||
                range.lastPageIndex >= pageCount
            ) {
                return false
            }
            expectedFirst = range.lastPageIndex + 1
        }
        return expectedFirst == pageCount
    }

    private fun outputName(
        sourceDisplayName: String?,
        index: Int,
        outputCount: Int,
        range: SplitPageRange,
    ): String {
        val base = sourceDisplayName
            ?.removeSuffix(".pdf")
            ?.replace(InvalidFilenameCharacters, "_")
            ?.trim(' ', '.')
            ?.take(72)
            ?.takeIf(String::isNotEmpty)
            ?: "QuietPDF-split"
        val numberWidth = outputCount.toString().length
        val part = (index + 1).toString().padStart(numberWidth, '0')
        val pages = if (range.pageCount == 1) "page-${range.firstPageIndex + 1}"
        else "pages-${range.firstPageIndex + 1}-${range.lastPageIndex + 1}"
        return "$base-part-$part-$pages.pdf"
    }

    private companion object {
        val InvalidFilenameCharacters = Regex("[\\\\/:*?\"<>|]")
    }

    private class OutputFailureException : Exception()
}

@Keep
internal object NativePdfSplitter {
    init {
        System.loadLibrary("quietpdf_merge")
    }

    external fun splitPart(
        sourcePointer: Long,
        firstPageIndex: Int,
        pageCount: Int,
        outputFileDescriptor: Int,
    ): Int
}
