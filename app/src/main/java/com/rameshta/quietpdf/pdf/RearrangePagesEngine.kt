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

sealed interface RearrangePagesResult {
    data class Success(val pageCount: Int) : RearrangePagesResult
    data object InvalidDocument : RearrangePagesResult
    data object InvalidOrder : RearrangePagesResult
    data object PermissionDenied : RearrangePagesResult
    data object InsufficientMemory : RearrangePagesResult
    data object Failed : RearrangePagesResult
}

object RearrangePageOrder {
    fun initial(pageCount: Int): List<Int> = if (pageCount > 0) (0 until pageCount).toList() else emptyList()

    fun move(order: List<Int>, fromIndex: Int, toIndex: Int): List<Int> {
        if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) {
            return order
        }
        return order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    fun isCompletePermutation(order: IntArray, pageCount: Int): Boolean {
        if (pageCount < 2 || order.size != pageCount) return false
        val seen = BooleanArray(pageCount)
        order.forEach { pageIndex ->
            if (pageIndex !in 0 until pageCount || seen[pageIndex]) return false
            seen[pageIndex] = true
        }
        return true
    }
}

class RearrangePagesEngine(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    suspend fun rearrange(
        sourceUri: Uri,
        outputUri: Uri,
        pageOrder: IntArray,
        expectedSourcePageCount: Int,
    ): RearrangePagesResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri) return@withContext RearrangePagesResult.InvalidOrder
        val temporary = try {
            File.createTempFile("rearrange-pages-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, contentResolver, outputUri)
            return@withContext RearrangePagesResult.Failed
        }
        var sourceDescriptor: ParcelFileDescriptor? = null
        var sourceDocument: PdfDocumentU? = null
        var outputCommitted = false
        try {
            sourceDescriptor = try {
                contentResolver.openFileDescriptor(sourceUri, "r")
                    ?: return@withContext RearrangePagesResult.InvalidDocument
            } catch (_: SecurityException) {
                return@withContext RearrangePagesResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext RearrangePagesResult.InvalidDocument
            }
            sourceDocument = try {
                PdfiumCoreU(appContext).newDocument(sourceDescriptor)
            } catch (_: SecurityException) {
                return@withContext RearrangePagesResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext RearrangePagesResult.InvalidDocument
            }
            val sourcePageCount = sourceDocument.getPageCount()
            if (sourcePageCount != expectedSourcePageCount) {
                return@withContext RearrangePagesResult.InvalidDocument
            }
            if (!RearrangePageOrder.isCompletePermutation(pageOrder, sourcePageCount)) {
                return@withContext RearrangePagesResult.InvalidOrder
            }

            coroutineContext.ensureActive()
            ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_READ_WRITE,
            ).use { output ->
                if (NativePdfPageRearranger.rearrangePages(
                        sourceDocument.mNativeDocPtr,
                        pageOrder,
                        output.fd,
                    ) != 0
                ) return@withContext RearrangePagesResult.Failed
            }
            val stagedPageCount = ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { descriptor -> PdfRenderer(descriptor).use(PdfRenderer::getPageCount) }
            if (stagedPageCount != sourcePageCount) return@withContext RearrangePagesResult.Failed
            coroutineContext.ensureActive()

            contentResolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext RearrangePagesResult.Failed
            val publishedPageCount = contentResolver.openFileDescriptor(outputUri, "r")?.use {
                PdfRenderer(it).use(PdfRenderer::getPageCount)
            } ?: return@withContext RearrangePagesResult.Failed
            if (publishedPageCount != sourcePageCount) return@withContext RearrangePagesResult.Failed
            coroutineContext.ensureActive()
            outputCommitted = true
            RearrangePagesResult.Success(sourcePageCount)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            RearrangePagesResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            RearrangePagesResult.InsufficientMemory
        } catch (_: LinkageError) {
            RearrangePagesResult.Failed
        } catch (_: Exception) {
            RearrangePagesResult.Failed
        } finally {
            sourceDocument?.let { runCatching { it.close() } }
            sourceDescriptor?.let { runCatching { it.close() } }
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, contentResolver, outputUri)
        }
    }
}

@Keep
internal object NativePdfPageRearranger {
    init {
        System.loadLibrary("quietpdf_merge")
    }

    external fun rearrangePages(
        sourcePointer: Long,
        pageOrder: IntArray,
        outputFileDescriptor: Int,
    ): Int
}
