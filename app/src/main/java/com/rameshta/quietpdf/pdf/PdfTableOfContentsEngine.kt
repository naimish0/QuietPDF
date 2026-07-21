package com.rameshta.quietpdf.pdf

import android.content.Context
import android.net.Uri
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.api.Bookmark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfOutlineEntry(
    val title: String,
    val pageIndex: Int,
    val depth: Int,
)

sealed interface PdfTableOfContentsResult {
    data class Entries(val entries: List<PdfOutlineEntry>) : PdfTableOfContentsResult
    data object Empty : PdfTableOfContentsResult
    data object Failed : PdfTableOfContentsResult
}

class PdfTableOfContentsEngine(context: Context) {
    private val appContext = context.applicationContext

    suspend fun load(uri: Uri, pageCount: Int): PdfTableOfContentsResult =
        withContext(Dispatchers.IO) {
            try {
                val descriptor = appContext.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext PdfTableOfContentsResult.Failed
                descriptor.use {
                    PdfiumCore(appContext).newDocument(it).use { document ->
                        val entries = document.getTableOfContents().flatten(pageCount)
                        if (entries.isEmpty()) PdfTableOfContentsResult.Empty
                        else PdfTableOfContentsResult.Entries(entries)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PdfTableOfContentsResult.Failed
            }
        }
}

private fun List<Bookmark>.flatten(pageCount: Int): List<PdfOutlineEntry> {
    val result = mutableListOf<PdfOutlineEntry>()

    fun append(bookmarks: List<Bookmark>, depth: Int) {
        bookmarks.forEach { bookmark ->
            val title = bookmark.title?.trim().orEmpty()
            val pageIndex = bookmark.pageIdx.toInt()
            val hasValidTarget = bookmark.pageIdx in 0 until pageCount.toLong()
            if (title.isNotEmpty() && hasValidTarget) {
                result += PdfOutlineEntry(title, pageIndex, depth)
                append(bookmark.children, depth + 1)
            } else {
                append(bookmark.children, depth)
            }
        }
    }

    append(this, 0)
    return result
}
