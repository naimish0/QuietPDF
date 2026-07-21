package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import io.legere.pdfiumandroid.core.unlocked.PdfDocumentU
import io.legere.pdfiumandroid.core.unlocked.PdfiumCoreU
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface DuplicatePagesResult {
    data class Success(val pageCount: Int, val duplicatedPageCount: Int) : DuplicatePagesResult
    data object InvalidDocument : DuplicatePagesResult
    data object InvalidSelection : DuplicatePagesResult
    data object PermissionDenied : DuplicatePagesResult
    data object InsufficientMemory : DuplicatePagesResult
    data object Failed : DuplicatePagesResult
}

object DuplicatePagePlan {
    fun outputPageOrder(
        selectedPageIndices: IntArray,
        pageCount: Int,
    ): IntArray? {
        if (!RotatePageSelection.isValid(selectedPageIndices, pageCount)) return null
        val selected = BooleanArray(pageCount)
        selectedPageIndices.forEach { selected[it] = true }
        return IntArray(pageCount + selectedPageIndices.size).also { order ->
            var outputIndex = 0
            repeat(pageCount) { sourceIndex ->
                order[outputIndex++] = sourceIndex
                if (selected[sourceIndex]) order[outputIndex++] = sourceIndex
            }
        }
    }
}

class DuplicatePagesEngine(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    suspend fun duplicate(
        sourceUri: Uri,
        outputUri: Uri,
        selectedPageIndices: IntArray,
        expectedSourcePageCount: Int,
    ): DuplicatePagesResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri) return@withContext DuplicatePagesResult.InvalidSelection
        val temporary = try {
            File.createTempFile("duplicate-pages-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, contentResolver, outputUri)
            return@withContext DuplicatePagesResult.Failed
        }
        var sourceDescriptor: ParcelFileDescriptor? = null
        var sourceDocument: PdfDocumentU? = null
        var outputCommitted = false
        try {
            sourceDescriptor = try {
                contentResolver.openFileDescriptor(sourceUri, "r")
                    ?: return@withContext DuplicatePagesResult.InvalidDocument
            } catch (_: SecurityException) {
                return@withContext DuplicatePagesResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext DuplicatePagesResult.InvalidDocument
            }
            sourceDocument = try {
                PdfiumCoreU(appContext).newDocument(sourceDescriptor)
            } catch (_: SecurityException) {
                return@withContext DuplicatePagesResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext DuplicatePagesResult.InvalidDocument
            }
            val sourcePageCount = sourceDocument.getPageCount()
            if (sourcePageCount != expectedSourcePageCount) {
                return@withContext DuplicatePagesResult.InvalidDocument
            }
            val pageOrder = DuplicatePagePlan.outputPageOrder(selectedPageIndices, sourcePageCount)
                ?: return@withContext DuplicatePagesResult.InvalidSelection

            coroutineContext.ensureActive()
            ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_READ_WRITE,
            ).use { output ->
                if (NativePdfPageDuplicator.duplicatePages(
                        sourceDocument.mNativeDocPtr,
                        pageOrder,
                        sourcePageCount,
                        output.fd,
                    ) != 0
                ) return@withContext DuplicatePagesResult.Failed
            }
            val stagedPageCount = ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { descriptor -> PdfRenderer(descriptor).use(PdfRenderer::getPageCount) }
            if (stagedPageCount != pageOrder.size) return@withContext DuplicatePagesResult.Failed
            coroutineContext.ensureActive()

            contentResolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext DuplicatePagesResult.Failed
            val publishedPageCount = contentResolver.openFileDescriptor(outputUri, "r")?.use {
                PdfRenderer(it).use(PdfRenderer::getPageCount)
            } ?: return@withContext DuplicatePagesResult.Failed
            if (publishedPageCount != pageOrder.size) return@withContext DuplicatePagesResult.Failed
            coroutineContext.ensureActive()
            outputCommitted = true
            DuplicatePagesResult.Success(pageOrder.size, selectedPageIndices.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            DuplicatePagesResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            DuplicatePagesResult.InsufficientMemory
        } catch (_: LinkageError) {
            DuplicatePagesResult.Failed
        } catch (_: Exception) {
            DuplicatePagesResult.Failed
        } finally {
            sourceDocument?.let { runCatching { it.close() } }
            sourceDescriptor?.let { runCatching { it.close() } }
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, contentResolver, outputUri)
        }
    }
}

@Keep
internal object NativePdfPageDuplicator {
    init {
        System.loadLibrary("quietpdf_merge")
    }

    external fun duplicatePages(
        sourcePointer: Long,
        pageOrder: IntArray,
        sourcePageCount: Int,
        outputFileDescriptor: Int,
    ): Int
}
