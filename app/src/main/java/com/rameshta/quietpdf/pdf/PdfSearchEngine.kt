package com.rameshta.quietpdf.pdf

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.pdf.PdfDocument
import androidx.pdf.RenderParams
import androidx.pdf.SandboxedPdfLoader
import io.legere.pdfiumandroid.PdfiumCore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class PdfSearchMatch(
    val pageIndex: Int,
    val bounds: List<RectF>,
)

sealed interface PdfSearchResult {
    data class Matches(val matches: List<PdfSearchMatch>) : PdfSearchResult
    data object NoSearchableText : PdfSearchResult
    data object Failed : PdfSearchResult
}

class PdfSearchEngine(
    context: Context,
    descriptorOpener: ((Uri) -> ParcelFileDescriptor?)? = null,
) : AutoCloseable {
    private val loader = SandboxedPdfLoader(context.applicationContext, Dispatchers.IO)
    private val openDescriptor = descriptorOpener
        ?: { uri: Uri -> context.contentResolver.openFileDescriptor(uri, "r") }
    private var openedUri: Uri? = null
    private var document: PdfDocument? = null
    private var documentHasText: Boolean? = null
    private var usePdfiumFallback = false

    suspend fun search(uri: Uri, pageCount: Int, query: String): PdfSearchResult =
        withContext(Dispatchers.IO) {
            if (query.isBlank() || pageCount <= 0) return@withContext PdfSearchResult.Matches(emptyList())

            if (usePdfiumFallback) return@withContext searchWithPdfium(uri, pageCount, query)
            try {
                val activeDocument = documentFor(uri)
                val results = activeDocument.searchDocument(query, 0 until pageCount)
                if (results.size() == 0 && !hasSearchableText(activeDocument, pageCount)) {
                    return@withContext PdfSearchResult.NoSearchableText
                }

                val matches = buildList {
                    for (resultIndex in 0 until results.size()) {
                        val pageIndex = results.keyAt(resultIndex)
                        val pageInfo = activeDocument.getPageInfo(pageIndex)
                        val pageWidth = pageInfo.width.toFloat().coerceAtLeast(1f)
                        val pageHeight = pageInfo.height.toFloat().coerceAtLeast(1f)
                        results.valueAt(resultIndex).forEach { match ->
                            add(
                                PdfSearchMatch(
                                    pageIndex = pageIndex,
                                    bounds = match.bounds.map { bounds ->
                                        RectF(
                                            (bounds.left / pageWidth).coerceIn(0f, 1f),
                                            (bounds.top / pageHeight).coerceIn(0f, 1f),
                                            (bounds.right / pageWidth).coerceIn(0f, 1f),
                                            (bounds.bottom / pageHeight).coerceIn(0f, 1f),
                                        )
                                    },
                                ),
                            )
                        }
                    }
                }
                PdfSearchResult.Matches(matches)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                usePdfiumFallback = true
                searchWithPdfium(uri, pageCount, query)
            }
        }

    private suspend fun searchWithPdfium(
        uri: Uri,
        pageCount: Int,
        query: String,
    ): PdfSearchResult = try {
        val descriptor = openDescriptor(uri) ?: return PdfSearchResult.Failed
        descriptor.use {
            PdfiumCore(loaderContext).newDocument(it).use { pdfiumDocument ->
                if (pdfiumDocument.getPageCount() != pageCount) return PdfSearchResult.Failed
                var hasSearchableText = false
                val matches = buildList {
                    repeat(pageCount) { pageIndex ->
                        coroutineContext.ensureActive()
                        val page = pdfiumDocument.openPage(pageIndex)
                            ?: return PdfSearchResult.Failed
                        page.use {
                            val pageWidth = page.getPageWidthPoint().toFloat().coerceAtLeast(1f)
                            val pageHeight = page.getPageHeightPoint().toFloat().coerceAtLeast(1f)
                            page.openTextPage().use { textPage ->
                                val characterCount = textPage.textPageCountChars()
                                if (characterCount > 0) hasSearchableText = true
                                textPage.findStart(query, emptySet(), 0)?.use { findResult ->
                                    while (findResult.findNext()) {
                                        coroutineContext.ensureActive()
                                        val start = findResult.getSchResultIndex()
                                        val count = findResult.getSchCount()
                                        val bounds = buildList {
                                            val rectangleCount = textPage.textPageCountRects(start, count)
                                            repeat(rectangleCount.coerceAtLeast(0)) { rectangleIndex ->
                                                textPage.textPageGetRect(rectangleIndex)?.let { rectangle ->
                                                    add(normalizePdfiumBounds(rectangle, pageWidth, pageHeight))
                                                }
                                            }
                                        }
                                        add(PdfSearchMatch(pageIndex, bounds))
                                    }
                                }
                            }
                        }
                    }
                }
                if (matches.isEmpty() && !hasSearchableText) {
                    PdfSearchResult.NoSearchableText
                } else {
                    PdfSearchResult.Matches(matches)
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        PdfSearchResult.Failed
    }

    private suspend fun documentFor(uri: Uri): PdfDocument {
        if (openedUri == uri) document?.let { return it }
        document?.close()
        document = null
        openedUri = null
        documentHasText = null
        val descriptor = openDescriptor(uri) ?: throw IOException("Document is unavailable")
        return try {
            loader.openDocument(
                uri,
                descriptor,
                null,
                RenderParams(RenderParams.RENDER_MODE_FOR_DISPLAY),
            ).also {
                document = it
                openedUri = uri
            }
        } catch (failure: Exception) {
            descriptor.close()
            throw failure
        }
    }

    private suspend fun hasSearchableText(document: PdfDocument, pageCount: Int): Boolean {
        documentHasText?.let { return it }
        val hasText = (0 until pageCount).any { pageIndex ->
            document.getPageContent(pageIndex)?.textContents.orEmpty().any { it.text.isNotBlank() }
        }
        documentHasText = hasText
        return hasText
    }

    override fun close() {
        document?.close()
        document = null
        openedUri = null
        documentHasText = null
        usePdfiumFallback = false
    }

    private val loaderContext: Context = context.applicationContext
}

private fun normalizePdfiumBounds(bounds: RectF, pageWidth: Float, pageHeight: Float): RectF {
    val left = minOf(bounds.left, bounds.right)
    val right = maxOf(bounds.left, bounds.right)
    val pdfBottom = minOf(bounds.top, bounds.bottom)
    val pdfTop = maxOf(bounds.top, bounds.bottom)
    return RectF(
        (left / pageWidth).coerceIn(0f, 1f),
        ((pageHeight - pdfTop) / pageHeight).coerceIn(0f, 1f),
        (right / pageWidth).coerceIn(0f, 1f),
        ((pageHeight - pdfBottom) / pageHeight).coerceIn(0f, 1f),
    )
}
