package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.rameshta.quietpdf.R
import java.io.FileNotFoundException
import java.io.IOException

data class OpenedPdf(
    val uri: Uri,
    val displayName: String?,
    val pageCount: Int,
)

enum class PdfOpenFailure(val messageResource: Int) {
    PermissionDenied(R.string.error_permission_denied),
    FileUnavailable(R.string.error_file_unavailable),
    Corrupted(R.string.error_corrupted_pdf),
    PasswordProtected(R.string.error_password_protected),
    Unsupported(R.string.error_unsupported_pdf),
    Empty(R.string.error_empty_pdf),
}

sealed interface PdfOpenResult {
    data class Success(val document: OpenedPdf) : PdfOpenResult
    data class Failure(val reason: PdfOpenFailure) : PdfOpenResult
}

class PdfDocumentOpener(private val contentResolver: ContentResolver) {
    fun open(uri: Uri): PdfOpenResult {
        val descriptor = try {
            contentResolver.openFileDescriptor(uri, "r")
                ?: return PdfOpenResult.Failure(PdfOpenFailure.FileUnavailable)
        } catch (_: SecurityException) {
            return PdfOpenResult.Failure(PdfOpenFailure.PermissionDenied)
        } catch (_: FileNotFoundException) {
            return PdfOpenResult.Failure(PdfOpenFailure.FileUnavailable)
        } catch (_: IOException) {
            return PdfOpenResult.Failure(PdfOpenFailure.FileUnavailable)
        } catch (_: IllegalArgumentException) {
            return PdfOpenResult.Failure(PdfOpenFailure.FileUnavailable)
        }

        val pageCount = try {
            descriptor.use(PdfInspector::pageCount)
        } catch (_: SecurityException) {
            return PdfOpenResult.Failure(PdfOpenFailure.PasswordProtected)
        } catch (_: IOException) {
            return PdfOpenResult.Failure(PdfOpenFailure.Corrupted)
        } catch (_: IllegalArgumentException) {
            return PdfOpenResult.Failure(PdfOpenFailure.Unsupported)
        }

        if (pageCount <= 0) return PdfOpenResult.Failure(PdfOpenFailure.Empty)

        return PdfOpenResult.Success(
            OpenedPdf(
                uri = uri,
                displayName = queryDisplayName(uri),
                pageCount = pageCount,
            ),
        )
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use(Cursor::readDisplayName)
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}

internal object PdfInspector {
    fun pageCount(descriptor: ParcelFileDescriptor): Int =
        PdfRenderer(descriptor).use(PdfRenderer::getPageCount)
}

private fun Cursor.readDisplayName(): String? {
    if (!moveToFirst()) return null
    val column = getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (column < 0 || isNull(column)) return null
    return getString(column)?.trim()?.takeIf(String::isNotEmpty)
}
