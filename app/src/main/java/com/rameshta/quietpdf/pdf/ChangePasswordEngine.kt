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

sealed interface ChangePasswordResult {
    data class Success(val pageCount: Int) : ChangePasswordResult
    data object IncorrectCurrentPassword : ChangePasswordResult
    data object InvalidNewPassword : ChangePasswordResult
    data object NotProtected : ChangePasswordResult
    data object InvalidDocument : ChangePasswordResult
    data object PermissionDenied : ChangePasswordResult
    data object InsufficientMemory : ChangePasswordResult
    data object Failed : ChangePasswordResult
}

class ChangePasswordEngine(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val random = SecureRandom()

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun change(
        sourceUri: Uri,
        outputUri: Uri,
        currentPassword: CharArray,
        newPassword: CharArray,
    ): ChangePasswordResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri) {
            clearPasswords(currentPassword, newPassword)
            return@withContext ChangePasswordResult.Failed
        }
        if (!ProtectPdfPassword.isValid(newPassword) || currentPassword.contentEquals(newPassword)) {
            clearPasswords(currentPassword, newPassword)
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext ChangePasswordResult.InvalidNewPassword
        }
        val temporary = try {
            File.createTempFile("change-password-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            clearPasswords(currentPassword, newPassword)
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext ChangePasswordResult.Failed
        }
        val currentPasswordText = currentPassword.concatToString()
        val newPasswordText = newPassword.concatToString()
        clearPasswords(currentPassword, newPassword)
        val ownerBytes = ByteArray(32).also(random::nextBytes)
        val ownerPassword = Base64.getEncoder().withoutPadding().encodeToString(ownerBytes)
        ownerBytes.fill(0)
        var outputCommitted = false
        try {
            coroutineContext.ensureActive()
            var pageCount = 0
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input, currentPasswordText).use { document ->
                    if (!document.isEncrypted) return@withContext ChangePasswordResult.NotProtected
                    pageCount = document.numberOfPages
                    if (pageCount <= 0) return@withContext ChangePasswordResult.InvalidDocument
                    document.protect(
                        StandardProtectionPolicy(
                            ownerPassword,
                            newPasswordText,
                            AccessPermission(),
                        ).apply {
                            encryptionKeyLength = 256
                            setPreferAES(true)
                        },
                    )
                    document.save(temporary)
                }
            } ?: return@withContext ChangePasswordResult.InvalidDocument
            coroutineContext.ensureActive()
            if (!validatesChangedPassword(temporary, currentPasswordText, newPasswordText, pageCount)) {
                return@withContext ChangePasswordResult.Failed
            }

            resolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext ChangePasswordResult.Failed
            if (!validatesChangedPassword(outputUri, currentPasswordText, newPasswordText, pageCount)) {
                return@withContext ChangePasswordResult.Failed
            }
            outputCommitted = true
            ChangePasswordResult.Success(pageCount)
        } catch (_: InvalidPasswordException) {
            ChangePasswordResult.IncorrectCurrentPassword
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            ChangePasswordResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            ChangePasswordResult.InsufficientMemory
        } catch (_: Exception) {
            ChangePasswordResult.Failed
        } finally {
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, resolver, outputUri)
        }
    }

    private fun validatesChangedPassword(
        file: File,
        currentPassword: String,
        newPassword: String,
        pageCount: Int,
    ): Boolean = rejectsPassword(file, "") && rejectsPassword(file, currentPassword) &&
        runCatching {
            PDDocument.load(file, newPassword).use { document ->
                document.isEncrypted && document.encryption.length == 256 &&
                    document.numberOfPages == pageCount
            }
        }.getOrDefault(false)

    private fun validatesChangedPassword(
        uri: Uri,
        currentPassword: String,
        newPassword: String,
        pageCount: Int,
    ): Boolean = rejectsPassword(uri, "") && rejectsPassword(uri, currentPassword) &&
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input, newPassword).use { document ->
                    document.isEncrypted && document.encryption.length == 256 &&
                        document.numberOfPages == pageCount
                }
            } ?: false
        }.getOrDefault(false)

    private fun rejectsPassword(file: File, password: String): Boolean = try {
        PDDocument.load(file, password).close()
        false
    } catch (_: InvalidPasswordException) {
        true
    }

    private fun rejectsPassword(uri: Uri, password: String): Boolean = try {
        resolver.openInputStream(uri)?.use { input -> PDDocument.load(input, password).close() }
        false
    } catch (_: InvalidPasswordException) {
        true
    }

    private fun clearPasswords(currentPassword: CharArray, newPassword: CharArray) {
        currentPassword.fill('\u0000')
        newPassword.fill('\u0000')
    }
}
