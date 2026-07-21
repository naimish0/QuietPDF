package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface RemovePasswordAnalysisResult {
    data object Protected : RemovePasswordAnalysisResult
    data object NotProtected : RemovePasswordAnalysisResult
    data object InvalidDocument : RemovePasswordAnalysisResult
    data object PermissionDenied : RemovePasswordAnalysisResult
    data object InsufficientMemory : RemovePasswordAnalysisResult
}

sealed interface RemovePasswordResult {
    data class Success(val pageCount: Int) : RemovePasswordResult
    data object IncorrectPassword : RemovePasswordResult
    data object NotProtected : RemovePasswordResult
    data object InvalidDocument : RemovePasswordResult
    data object PermissionDenied : RemovePasswordResult
    data object InsufficientMemory : RemovePasswordResult
    data object Failed : RemovePasswordResult
}

class RemovePasswordEngine(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun analyze(sourceUri: Uri): RemovePasswordAnalysisResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.numberOfPages <= 0) RemovePasswordAnalysisResult.InvalidDocument
                    else if (document.isEncrypted) RemovePasswordAnalysisResult.Protected
                    else RemovePasswordAnalysisResult.NotProtected
                }
            } ?: RemovePasswordAnalysisResult.InvalidDocument
        } catch (_: InvalidPasswordException) {
            RemovePasswordAnalysisResult.Protected
        } catch (_: SecurityException) {
            RemovePasswordAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            RemovePasswordAnalysisResult.InsufficientMemory
        } catch (_: Exception) {
            RemovePasswordAnalysisResult.InvalidDocument
        }
    }

    suspend fun remove(
        sourceUri: Uri,
        outputUri: Uri,
        password: CharArray,
    ): RemovePasswordResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri) {
            password.fill('\u0000')
            return@withContext RemovePasswordResult.Failed
        }
        val temporary = try {
            File.createTempFile("remove-password-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            password.fill('\u0000')
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext RemovePasswordResult.Failed
        }
        val currentPassword = password.concatToString()
        password.fill('\u0000')
        var outputCommitted = false
        try {
            coroutineContext.ensureActive()
            var pageCount = 0
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input, currentPassword).use { document ->
                    if (!document.isEncrypted) return@withContext RemovePasswordResult.NotProtected
                    pageCount = document.numberOfPages
                    if (pageCount <= 0) return@withContext RemovePasswordResult.InvalidDocument
                    document.isAllSecurityToBeRemoved = true
                    document.save(temporary)
                }
            } ?: return@withContext RemovePasswordResult.InvalidDocument
            coroutineContext.ensureActive()
            if (!validatesUnprotected(temporary, pageCount)) return@withContext RemovePasswordResult.Failed

            resolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext RemovePasswordResult.Failed
            if (!validatesUnprotected(outputUri, pageCount)) return@withContext RemovePasswordResult.Failed
            outputCommitted = true
            RemovePasswordResult.Success(pageCount)
        } catch (_: InvalidPasswordException) {
            RemovePasswordResult.IncorrectPassword
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            RemovePasswordResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            RemovePasswordResult.InsufficientMemory
        } catch (_: Exception) {
            RemovePasswordResult.Failed
        } finally {
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, resolver, outputUri)
        }
    }

    private fun validatesUnprotected(file: File, pageCount: Int): Boolean = runCatching {
        PDDocument.load(file).use { document ->
            !document.isEncrypted && document.numberOfPages == pageCount
        }
    }.getOrDefault(false)

    private fun validatesUnprotected(uri: Uri, pageCount: Int): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                !document.isEncrypted && document.numberOfPages == pageCount
            }
        } ?: false
    }.getOrDefault(false)
}
