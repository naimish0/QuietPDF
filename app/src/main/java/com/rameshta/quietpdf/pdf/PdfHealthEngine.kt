package com.rameshta.quietpdf.pdf

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class PdfHealthReport(
    val pageCount: Int,
    val fileSizeBytes: Long?,
    val hasSearchableText: Boolean,
    val hasTableOfContents: Boolean,
    val title: String?,
    val author: String?,
)

sealed interface PdfHealthResult {
    data class Healthy(val report: PdfHealthReport) : PdfHealthResult
    data class UnreadablePage(val pageIndex: Int) : PdfHealthResult
    data object PermissionDenied : PdfHealthResult
    data object Failed : PdfHealthResult
}

class PdfHealthEngine(context: Context) {
    private val appContext = context.applicationContext

    suspend fun inspect(uri: Uri, expectedPageCount: Int): PdfHealthResult =
        withContext(Dispatchers.IO) {
            try {
                val descriptor = appContext.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext PdfHealthResult.Failed
                descriptor.use {
                    val descriptorSize = it.statSize.takeIf { size -> size >= 0 }
                    PdfiumCore(appContext).newDocument(it).use { document ->
                        val pageCount = document.getPageCount()
                        if (pageCount != expectedPageCount || pageCount <= 0) {
                            return@withContext PdfHealthResult.Failed
                        }
                        repeat(pageCount) { pageIndex ->
                            coroutineContext.ensureActive()
                            try {
                                val page = document.openPage(pageIndex)
                                    ?: return@withContext PdfHealthResult.UnreadablePage(pageIndex)
                                val hasValidDimensions = page.use {
                                    page.getPageWidthPoint() > 0 && page.getPageHeightPoint() > 0
                                }
                                if (!hasValidDimensions) {
                                    return@withContext PdfHealthResult.UnreadablePage(pageIndex)
                                }
                            } catch (_: Exception) {
                                return@withContext PdfHealthResult.UnreadablePage(pageIndex)
                            }
                        }
                        val metadata = document.getDocumentMeta()
                        PdfHealthResult.Healthy(
                            PdfHealthReport(
                                pageCount = pageCount,
                                fileSizeBytes = queryFileSize(uri) ?: descriptorSize,
                                hasSearchableText = document.getPageCharCounts().any { it > 0 },
                                hasTableOfContents = document.getTableOfContents().isNotEmpty(),
                                title = metadata.title.cleanMetadata(),
                                author = metadata.author.cleanMetadata(),
                            ),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                PdfHealthResult.PermissionDenied
            } catch (_: Exception) {
                PdfHealthResult.Failed
            }
        }

    private fun queryFileSize(uri: Uri): Long? = try {
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use(Cursor::readSize)
    } catch (_: RuntimeException) {
        null
    }
}

private fun String?.cleanMetadata(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun Cursor.readSize(): Long? {
    if (!moveToFirst()) return null
    val column = getColumnIndex(OpenableColumns.SIZE)
    if (column < 0 || isNull(column)) return null
    return getLong(column).takeIf { it >= 0 }
}
