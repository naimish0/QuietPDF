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

sealed interface ExtractPagesResult {
    data class Success(val pageCount: Int) : ExtractPagesResult
    data object InvalidDocument : ExtractPagesResult
    data object InvalidSelection : ExtractPagesResult
    data object PermissionDenied : ExtractPagesResult
    data object InsufficientMemory : ExtractPagesResult
    data object Failed : ExtractPagesResult
}

class ExtractPagesEngine(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    suspend fun extract(
        sourceUri: Uri,
        outputUri: Uri,
        selectedPageIndices: IntArray,
        expectedSourcePageCount: Int? = null,
    ): ExtractPagesResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri) return@withContext ExtractPagesResult.InvalidSelection
        val temporary = try {
            File.createTempFile("extract-pages-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, contentResolver, outputUri)
            return@withContext ExtractPagesResult.Failed
        }
        var sourceDescriptor: ParcelFileDescriptor? = null
        var sourceDocument: PdfDocumentU? = null
        var outputCommitted = false
        try {
            sourceDescriptor = try {
                contentResolver.openFileDescriptor(sourceUri, "r")
                    ?: return@withContext ExtractPagesResult.InvalidDocument
            } catch (_: SecurityException) {
                return@withContext ExtractPagesResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext ExtractPagesResult.InvalidDocument
            }
            sourceDocument = try {
                PdfiumCoreU(appContext).newDocument(sourceDescriptor)
            } catch (_: SecurityException) {
                return@withContext ExtractPagesResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext ExtractPagesResult.InvalidDocument
            }
            val sourcePageCount = sourceDocument.getPageCount()
            if (expectedSourcePageCount != null && sourcePageCount != expectedSourcePageCount) {
                return@withContext ExtractPagesResult.InvalidDocument
            }
            if (!isValidSelection(selectedPageIndices, sourcePageCount)) {
                return@withContext ExtractPagesResult.InvalidSelection
            }

            coroutineContext.ensureActive()
            ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_READ_WRITE,
            ).use { output ->
                val nativeResult = NativePdfPageExtractor.extractPages(
                    sourcePointer = sourceDocument.mNativeDocPtr,
                    selectedPageIndices = selectedPageIndices,
                    outputFileDescriptor = output.fd,
                )
                if (nativeResult != 0) return@withContext ExtractPagesResult.Failed
            }
            val stagedPageCount = ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { descriptor -> PdfRenderer(descriptor).use(PdfRenderer::getPageCount) }
            if (stagedPageCount != selectedPageIndices.size) {
                return@withContext ExtractPagesResult.Failed
            }
            coroutineContext.ensureActive()

            contentResolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext ExtractPagesResult.Failed
            val publishedPageCount = contentResolver.openFileDescriptor(outputUri, "r")?.use {
                PdfRenderer(it).use(PdfRenderer::getPageCount)
            } ?: return@withContext ExtractPagesResult.Failed
            if (publishedPageCount != selectedPageIndices.size) {
                return@withContext ExtractPagesResult.Failed
            }
            coroutineContext.ensureActive()
            outputCommitted = true
            ExtractPagesResult.Success(selectedPageIndices.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            ExtractPagesResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ExtractPagesResult.InsufficientMemory
        } catch (_: LinkageError) {
            ExtractPagesResult.Failed
        } catch (_: Exception) {
            ExtractPagesResult.Failed
        } finally {
            sourceDocument?.let { runCatching { it.close() } }
            sourceDescriptor?.let { runCatching { it.close() } }
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, contentResolver, outputUri)
        }
    }

    private fun isValidSelection(indices: IntArray, pageCount: Int): Boolean {
        if (indices.isEmpty() || pageCount <= 0) return false
        var previous = -1
        indices.forEach { index ->
            if (index <= previous || index !in 0 until pageCount) return false
            previous = index
        }
        return true
    }

}

internal fun cleanupNewPdfOutput(
    context: Context,
    contentResolver: ContentResolver,
    uri: Uri,
): Boolean = runCatching {
    when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> uri.path?.let(::File)?.delete() == true
        ContentResolver.SCHEME_CONTENT -> DocumentsContract.isDocumentUri(context, uri) &&
            DocumentsContract.deleteDocument(contentResolver, uri)
        else -> false
    }
}.getOrDefault(false)

@Keep
internal object NativePdfPageExtractor {
    init {
        System.loadLibrary("quietpdf_merge")
    }

    external fun extractPages(
        sourcePointer: Long,
        selectedPageIndices: IntArray,
        outputFileDescriptor: Int,
    ): Int
}
