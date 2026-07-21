package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

object ProtectPdfPassword {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 64

    fun isValid(password: CharSequence): Boolean =
        password.length in MIN_LENGTH..MAX_LENGTH && password.any { !it.isWhitespace() }

    fun isValid(password: CharArray): Boolean =
        password.size in MIN_LENGTH..MAX_LENGTH && password.any { !it.isWhitespace() }
}

sealed interface ProtectPdfAnalysisResult {
    data class Ready(val pageCount: Int) : ProtectPdfAnalysisResult
    data object AlreadyProtected : ProtectPdfAnalysisResult
    data object InvalidDocument : ProtectPdfAnalysisResult
    data object PermissionDenied : ProtectPdfAnalysisResult
    data object InsufficientMemory : ProtectPdfAnalysisResult
    data object Failed : ProtectPdfAnalysisResult
}

sealed interface ProtectPdfResult {
    data class Success(val pageCount: Int) : ProtectPdfResult
    data object AlreadyProtected : ProtectPdfResult
    data object InvalidPassword : ProtectPdfResult
    data object InvalidDocument : ProtectPdfResult
    data object PermissionDenied : ProtectPdfResult
    data object InsufficientMemory : ProtectPdfResult
    data object Failed : ProtectPdfResult
}

class ProtectPdfEngine(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val random = SecureRandom()

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun analyze(sourceUri: Uri): ProtectPdfAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val pageCount = resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) return@withContext ProtectPdfAnalysisResult.AlreadyProtected
                    if (document.numberOfPages <= 0) return@withContext ProtectPdfAnalysisResult.InvalidDocument
                    document.numberOfPages
                }
            } ?: return@withContext ProtectPdfAnalysisResult.InvalidDocument
            ProtectPdfAnalysisResult.Ready(pageCount)
        } catch (_: InvalidPasswordException) {
            ProtectPdfAnalysisResult.AlreadyProtected
        } catch (_: SecurityException) {
            ProtectPdfAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ProtectPdfAnalysisResult.InsufficientMemory
        } catch (_: Exception) {
            ProtectPdfAnalysisResult.InvalidDocument
        }
    }

    suspend fun protect(
        sourceUri: Uri,
        outputUri: Uri,
        password: CharArray,
        expectedPageCount: Int,
    ): ProtectPdfResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri) {
            password.fill('\u0000')
            return@withContext ProtectPdfResult.InvalidPassword
        }
        if (expectedPageCount <= 0 || !ProtectPdfPassword.isValid(password)) {
            password.fill('\u0000')
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext ProtectPdfResult.InvalidPassword
        }
        val temporary = try {
            File.createTempFile("protect-pdf-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            password.fill('\u0000')
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext ProtectPdfResult.Failed
        }
        val userPassword = password.concatToString()
        password.fill('\u0000')
        val ownerBytes = ByteArray(32).also(random::nextBytes)
        val ownerPassword = Base64.getEncoder().withoutPadding().encodeToString(ownerBytes)
        ownerBytes.fill(0)
        var outputCommitted = false
        try {
            coroutineContext.ensureActive()
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) return@withContext ProtectPdfResult.AlreadyProtected
                    if (document.numberOfPages != expectedPageCount) return@withContext ProtectPdfResult.InvalidDocument
                    val policy = StandardProtectionPolicy(
                        ownerPassword,
                        userPassword,
                        AccessPermission(),
                    ).apply {
                        encryptionKeyLength = 256
                        setPreferAES(true)
                    }
                    document.protect(policy)
                    document.save(temporary)
                }
            } ?: return@withContext ProtectPdfResult.InvalidDocument
            coroutineContext.ensureActive()

            if (!rejectsMissingPassword(temporary) ||
                !validatesWithPassword(temporary, userPassword, expectedPageCount)
            ) {
                return@withContext ProtectPdfResult.Failed
            }
            coroutineContext.ensureActive()
            val sink = resolver.openOutputStream(outputUri, "wt")
                ?: return@withContext ProtectPdfResult.Failed
            sink.use { output -> temporary.inputStream().use { it.copyTo(output) } }

            if (!rejectsMissingPassword(outputUri) ||
                !validatesWithPassword(outputUri, userPassword, expectedPageCount)
            ) {
                return@withContext ProtectPdfResult.Failed
            }
            outputCommitted = true
            ProtectPdfResult.Success(expectedPageCount)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: InvalidPasswordException) {
            ProtectPdfResult.AlreadyProtected
        } catch (_: SecurityException) {
            ProtectPdfResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ProtectPdfResult.InsufficientMemory
        } catch (_: Exception) {
            ProtectPdfResult.Failed
        } finally {
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, resolver, outputUri)
        }
    }

    private fun rejectsMissingPassword(file: File): Boolean = try {
        PDDocument.load(file).close()
        false
    } catch (_: InvalidPasswordException) {
        true
    }

    private fun validatesWithPassword(file: File, password: String, pageCount: Int): Boolean =
        runCatching {
            PDDocument.load(file, password).use { document ->
                document.isEncrypted && document.numberOfPages == pageCount
            }
        }.getOrDefault(false)

    private fun rejectsMissingPassword(uri: Uri): Boolean = try {
        resolver.openInputStream(uri)?.use { PDDocument.load(it).close() }
        false
    } catch (_: InvalidPasswordException) {
        true
    }

    private fun validatesWithPassword(uri: Uri, password: String, pageCount: Int): Boolean =
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input, password).use { document ->
                    document.isEncrypted && document.numberOfPages == pageCount
                }
            } ?: false
        }.getOrDefault(false)
}
