package com.rameshta.quietpdf.pdf

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rameshta.quietpdf.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PdfOpenState {
    data object Idle : PdfOpenState
    data object Opening : PdfOpenState
    data class Opened(
        val uri: Uri,
        val displayName: String?,
        val pageCount: Int,
        val initialPageIndex: Int = 0,
        val bookmarkedPages: Set<Int> = emptySet(),
    ) : PdfOpenState
    data class Failed(val failure: PdfOpenFailure) : PdfOpenState
}

sealed interface PageRenderResult {
    data class Ready(val bitmap: Bitmap) : PageRenderResult
    data class Failed(val reason: PageRenderFailure) : PageRenderResult
}

enum class PageRenderFailure(@get:StringRes val messageResource: Int) {
    UnableToRender(R.string.page_render_error),
    PermissionDenied(R.string.page_render_permission_error),
    InsufficientMemory(R.string.page_render_memory_error),
}

class PdfOpenViewModel(application: Application) : AndroidViewModel(application) {
    private val opener = PdfDocumentOpener(application.contentResolver)
    private val pageRenderer = PdfPageRenderer(application.contentResolver)
    private val readingPositionStore = ReadingPositionStore(application)
    private val searchEngine = PdfSearchEngine(application)
    private val bookmarkStore = PdfBookmarkStore(application)
    private val tableOfContentsEngine = PdfTableOfContentsEngine(application)
    private var openJob: Job? = null
    private var documentGeneration = 0L
    private val pageCache = object : LruCache<PageCacheKey, Bitmap>(pageCacheBytes()) {
        override fun sizeOf(key: PageCacheKey, value: Bitmap): Int = value.allocationByteCount
    }

    var state: PdfOpenState by mutableStateOf(PdfOpenState.Idle)
        private set

    fun open(uri: Uri) {
        openJob?.cancel()
        searchEngine.close()
        documentGeneration++
        pageCache.evictAll()
        state = PdfOpenState.Opening
        openJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { opener.open(uri) }
            state = when (result) {
                is PdfOpenResult.Success -> PdfOpenState.Opened(
                    uri = result.document.uri,
                    displayName = result.document.displayName,
                    pageCount = result.document.pageCount,
                    initialPageIndex = readingPositionStore.restore(
                        result.document.uri,
                        result.document.pageCount,
                    ),
                    bookmarkedPages = bookmarkStore.restore(
                        result.document.uri,
                        result.document.pageCount,
                    ),
                )
                is PdfOpenResult.Failure -> PdfOpenState.Failed(result.reason)
            }
        }
    }

    fun rejectUnsupportedUri() {
        openJob?.cancel()
        searchEngine.close()
        documentGeneration++
        pageCache.evictAll()
        state = PdfOpenState.Failed(PdfOpenFailure.Unsupported)
    }

    fun rememberPage(pageIndex: Int) {
        val opened = state as? PdfOpenState.Opened ?: return
        readingPositionStore.remember(opened.uri, pageIndex, opened.pageCount)
    }

    suspend fun search(query: String): PdfSearchResult {
        val opened = state as? PdfOpenState.Opened ?: return PdfSearchResult.Failed
        return searchEngine.search(opened.uri, opened.pageCount, query)
    }

    suspend fun loadTableOfContents(): PdfTableOfContentsResult {
        val opened = state as? PdfOpenState.Opened ?: return PdfTableOfContentsResult.Failed
        return tableOfContentsEngine.load(opened.uri, opened.pageCount)
    }

    fun toggleBookmark(pageIndex: Int) {
        val opened = state as? PdfOpenState.Opened ?: return
        val updated = bookmarkStore.toggle(opened.uri, pageIndex, opened.pageCount)
        state = opened.copy(bookmarkedPages = updated)
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int): PageRenderResult {
        val opened = state as? PdfOpenState.Opened
            ?: return PageRenderResult.Failed(PageRenderFailure.UnableToRender)
        val generation = documentGeneration
        val key = PageCacheKey(opened.uri, pageIndex, targetWidth)
        synchronized(pageCache) { pageCache.get(key) }?.let {
            return PageRenderResult.Ready(it)
        }

        return try {
            val bitmap = withContext(PageRenderDispatcher) {
                pageRenderer.render(opened.uri, pageIndex, targetWidth)
            }
            if (generation != documentGeneration) {
                bitmap.recycle()
                PageRenderResult.Failed(PageRenderFailure.UnableToRender)
            } else {
                synchronized(pageCache) { pageCache.put(key, bitmap) }
                PageRenderResult.Ready(bitmap)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            PageRenderResult.Failed(PageRenderFailure.PermissionDenied)
        } catch (_: OutOfMemoryError) {
            synchronized(pageCache) { pageCache.evictAll() }
            PageRenderResult.Failed(PageRenderFailure.InsufficientMemory)
        } catch (_: Exception) {
            PageRenderResult.Failed(PageRenderFailure.UnableToRender)
        }
    }

    private data class PageCacheKey(val uri: Uri, val pageIndex: Int, val width: Int)

    override fun onCleared() {
        searchEngine.close()
        super.onCleared()
    }

    private companion object {
        val PageRenderDispatcher = Dispatchers.IO.limitedParallelism(2)

        fun pageCacheBytes(): Int {
            val memoryBudget = Runtime.getRuntime().maxMemory() / 12L
            return memoryBudget.coerceIn(8L * 1024 * 1024, 32L * 1024 * 1024).toInt()
        }
    }
}
