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

sealed interface MergePdfResult {
    data class Success(val pageCount: Int) : MergePdfResult
    data class InvalidDocument(val documentIndex: Int) : MergePdfResult
    data object PermissionDenied : MergePdfResult
    data object InsufficientMemory : MergePdfResult
    data object Failed : MergePdfResult
}

class MergePdfEngine(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    suspend fun merge(sourceUris: List<Uri>, outputUri: Uri): MergePdfResult =
        withContext(Dispatchers.IO) {
            if (sourceUris.size < 2 || sourceUris.distinct().size != sourceUris.size ||
                outputUri in sourceUris
            ) {
                return@withContext MergePdfResult.Failed
            }
            val temporary = try {
                File.createTempFile("merge-pdf-", ".pdf", appContext.cacheDir)
            } catch (_: Exception) {
                return@withContext MergePdfResult.Failed
            }
            val descriptors = mutableListOf<ParcelFileDescriptor>()
            val documents = mutableListOf<PdfDocumentU>()
            try {
                val core = PdfiumCoreU(appContext)
                var totalPages = 0
                sourceUris.forEachIndexed { index, uri ->
                    coroutineContext.ensureActive()
                    val descriptor = try {
                        contentResolver.openFileDescriptor(uri, "r")
                            ?: return@withContext MergePdfResult.InvalidDocument(index)
                    } catch (_: SecurityException) {
                        return@withContext MergePdfResult.PermissionDenied
                    } catch (_: Exception) {
                        return@withContext MergePdfResult.InvalidDocument(index)
                    }
                    descriptors += descriptor
                    val document = try {
                        core.newDocument(descriptor)
                    } catch (_: SecurityException) {
                        return@withContext MergePdfResult.PermissionDenied
                    } catch (_: Exception) {
                        return@withContext MergePdfResult.InvalidDocument(index)
                    }
                    documents += document
                    val pageCount = document.getPageCount()
                    if (pageCount <= 0) return@withContext MergePdfResult.InvalidDocument(index)
                    if (totalPages > Int.MAX_VALUE - pageCount) {
                        return@withContext MergePdfResult.Failed
                    }
                    totalPages += pageCount
                }

                ParcelFileDescriptor.open(
                    temporary,
                    ParcelFileDescriptor.MODE_CREATE or
                        ParcelFileDescriptor.MODE_TRUNCATE or
                        ParcelFileDescriptor.MODE_READ_WRITE,
                ).use { output ->
                    val result = NativePdfMerger.merge(
                        documents.map { it.mNativeDocPtr }.toLongArray(),
                        output.fd,
                    )
                    if (result != 0) return@withContext MergePdfResult.Failed
                }
                coroutineContext.ensureActive()

                val writtenPages = ParcelFileDescriptor.open(
                    temporary,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                ).use { descriptor -> PdfRenderer(descriptor).use(PdfRenderer::getPageCount) }
                if (writtenPages != totalPages) return@withContext MergePdfResult.Failed
                coroutineContext.ensureActive()

                val sink = contentResolver.openOutputStream(outputUri, "wt")
                    ?: return@withContext MergePdfResult.Failed
                // Treat the final provider copy as a commit: cancellation is observed before this
                // point so the destination is never left as a partial PDF.
                sink.use { output -> temporary.inputStream().use { it.copyTo(output) } }
                MergePdfResult.Success(totalPages)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                MergePdfResult.PermissionDenied
            } catch (_: OutOfMemoryError) {
                MergePdfResult.InsufficientMemory
            } catch (_: LinkageError) {
                MergePdfResult.Failed
            } catch (_: Exception) {
                MergePdfResult.Failed
            } finally {
                documents.asReversed().forEach { runCatching { it.close() } }
                descriptors.asReversed().forEach { runCatching { it.close() } }
                temporary.delete()
            }
        }
}

@Keep
internal object NativePdfMerger {
    init {
        System.loadLibrary("quietpdf_merge")
    }

    external fun merge(sourcePointers: LongArray, outputFileDescriptor: Int): Int
}
