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

enum class PageRotation(val quarterTurnsClockwise: Int) {
    Clockwise90(1),
    HalfTurn(2),
    CounterClockwise90(3),
}

sealed interface RotatePagesResult {
    data class Success(val pageCount: Int, val rotatedPageCount: Int) : RotatePagesResult
    data object InvalidDocument : RotatePagesResult
    data object InvalidSelection : RotatePagesResult
    data object PermissionDenied : RotatePagesResult
    data object InsufficientMemory : RotatePagesResult
    data object Failed : RotatePagesResult
}

object RotatePageSelection {
    fun isValid(selectedPageIndices: IntArray, pageCount: Int): Boolean {
        if (selectedPageIndices.isEmpty() || pageCount <= 0) return false
        var previous = -1
        selectedPageIndices.forEach { index ->
            if (index <= previous || index !in 0 until pageCount) return false
            previous = index
        }
        return true
    }
}

class RotatePagesEngine(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    suspend fun rotate(
        sourceUri: Uri,
        outputUri: Uri,
        selectedPageIndices: IntArray,
        rotation: PageRotation,
        expectedSourcePageCount: Int,
    ): RotatePagesResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri) return@withContext RotatePagesResult.InvalidSelection
        val temporary = try {
            File.createTempFile("rotate-pages-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, contentResolver, outputUri)
            return@withContext RotatePagesResult.Failed
        }
        var sourceDescriptor: ParcelFileDescriptor? = null
        var sourceDocument: PdfDocumentU? = null
        var outputCommitted = false
        try {
            sourceDescriptor = try {
                contentResolver.openFileDescriptor(sourceUri, "r")
                    ?: return@withContext RotatePagesResult.InvalidDocument
            } catch (_: SecurityException) {
                return@withContext RotatePagesResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext RotatePagesResult.InvalidDocument
            }
            sourceDocument = try {
                PdfiumCoreU(appContext).newDocument(sourceDescriptor)
            } catch (_: SecurityException) {
                return@withContext RotatePagesResult.PermissionDenied
            } catch (_: Exception) {
                return@withContext RotatePagesResult.InvalidDocument
            }
            val sourcePageCount = sourceDocument.getPageCount()
            if (sourcePageCount != expectedSourcePageCount) {
                return@withContext RotatePagesResult.InvalidDocument
            }
            if (!RotatePageSelection.isValid(selectedPageIndices, sourcePageCount)) {
                return@withContext RotatePagesResult.InvalidSelection
            }

            coroutineContext.ensureActive()
            ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_READ_WRITE,
            ).use { output ->
                if (NativePdfPageRotator.rotatePages(
                        sourceDocument.mNativeDocPtr,
                        selectedPageIndices,
                        rotation.quarterTurnsClockwise,
                        output.fd,
                    ) != 0
                ) return@withContext RotatePagesResult.Failed
            }
            val stagedPageCount = ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { descriptor -> PdfRenderer(descriptor).use(PdfRenderer::getPageCount) }
            if (stagedPageCount != sourcePageCount) return@withContext RotatePagesResult.Failed
            coroutineContext.ensureActive()

            contentResolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext RotatePagesResult.Failed
            val publishedPageCount = contentResolver.openFileDescriptor(outputUri, "r")?.use {
                PdfRenderer(it).use(PdfRenderer::getPageCount)
            } ?: return@withContext RotatePagesResult.Failed
            if (publishedPageCount != sourcePageCount) return@withContext RotatePagesResult.Failed
            coroutineContext.ensureActive()
            outputCommitted = true
            RotatePagesResult.Success(sourcePageCount, selectedPageIndices.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            RotatePagesResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            RotatePagesResult.InsufficientMemory
        } catch (_: LinkageError) {
            RotatePagesResult.Failed
        } catch (_: Exception) {
            RotatePagesResult.Failed
        } finally {
            sourceDocument?.let { runCatching { it.close() } }
            sourceDescriptor?.let { runCatching { it.close() } }
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, contentResolver, outputUri)
        }
    }
}

@Keep
internal object NativePdfPageRotator {
    init {
        System.loadLibrary("quietpdf_merge")
    }

    external fun rotatePages(
        sourcePointer: Long,
        selectedPageIndices: IntArray,
        quarterTurnsClockwise: Int,
        outputFileDescriptor: Int,
    ): Int
}
