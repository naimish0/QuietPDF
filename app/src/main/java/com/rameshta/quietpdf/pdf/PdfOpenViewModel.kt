package com.rameshta.quietpdf.pdf

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
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

sealed interface ImagesToPdfState {
    data object Idle : ImagesToPdfState
    data class Configuring(val imageCount: Int) : ImagesToPdfState
    data class Creating(val imageCount: Int) : ImagesToPdfState
    data class Failed(val failure: ImagesToPdfFailure) : ImagesToPdfState
}

data class MergePdfItem(val uri: Uri, val displayName: String)

sealed interface MergePdfState {
    data object Idle : MergePdfState
    data object Preparing : MergePdfState
    data class Configuring(val documents: List<MergePdfItem>) : MergePdfState
    data class Merging(val documentCount: Int) : MergePdfState
    data class Failed(val failure: MergePdfFailure) : MergePdfState
}

sealed interface SplitPdfState {
    data object Idle : SplitPdfState
    data object Preparing : SplitPdfState
    data class Configuring(
        val sourceUri: Uri,
        val displayName: String,
        val pageCount: Int,
    ) : SplitPdfState
    data class Splitting(val completedOutputs: Int, val totalOutputs: Int) : SplitPdfState
    data class Completed(val outputCount: Int) : SplitPdfState
    data class Failed(val failure: SplitPdfFailure) : SplitPdfState
}

enum class SplitPdfFailure(@get:StringRes val messageResource: Int) {
    NeedAtLeastTwoPages(R.string.split_pdf_need_two_pages),
    InvalidDocument(R.string.split_pdf_invalid_document),
    InvalidPlan(R.string.split_pdf_invalid_plan),
    PermissionDenied(R.string.split_pdf_permission_denied),
    InsufficientMemory(R.string.split_pdf_memory_error),
    UnableToSave(R.string.split_pdf_save_error),
}

enum class MergePdfFailure(@get:StringRes val messageResource: Int) {
    NeedAtLeastTwo(R.string.merge_pdf_need_two),
    InvalidDocument(R.string.merge_pdf_invalid_document),
    PermissionDenied(R.string.merge_pdf_permission_denied),
    InsufficientMemory(R.string.merge_pdf_memory_error),
    UnableToSave(R.string.merge_pdf_save_error),
}

enum class ImagesToPdfFailure(@get:StringRes val messageResource: Int) {
    InvalidImage(R.string.images_to_pdf_invalid_image),
    PermissionDenied(R.string.images_to_pdf_permission_denied),
    InsufficientMemory(R.string.images_to_pdf_memory_error),
    UnableToSave(R.string.images_to_pdf_save_error),
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
    private val healthEngine = PdfHealthEngine(application)
    private val imagesToPdfEngine = ImagesToPdfEngine(application.contentResolver, application.cacheDir)
    private val mergePdfEngine = MergePdfEngine(application)
    private val splitPdfEngine = SplitPdfEngine(application)
    private var openJob: Job? = null
    private var imageCreationJob: Job? = null
    private var mergeJob: Job? = null
    private var splitJob: Job? = null
    private var selectedImageUris: List<Uri> = emptyList()
    private var selectedImageLayout = ImagePdfLayout()
    private var selectedSplitRanges: List<SplitPageRange> = emptyList()
    private var documentGeneration = 0L
    private val pageCache = object : LruCache<PageCacheKey, Bitmap>(pageCacheBytes()) {
        override fun sizeOf(key: PageCacheKey, value: Bitmap): Int = value.allocationByteCount
    }

    var state: PdfOpenState by mutableStateOf(PdfOpenState.Idle)
        private set

    var imagesToPdfState: ImagesToPdfState by mutableStateOf(ImagesToPdfState.Idle)
        private set

    var mergePdfState: MergePdfState by mutableStateOf(MergePdfState.Idle)
        private set

    var splitPdfState: SplitPdfState by mutableStateOf(SplitPdfState.Idle)
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

    fun selectImagesForPdf(imageUris: List<Uri>) {
        selectedImageUris = imageUris
        selectedImageLayout = ImagePdfLayout()
        imagesToPdfState = if (imageUris.isEmpty()) ImagesToPdfState.Idle
        else ImagesToPdfState.Configuring(imageUris.size)
    }

    fun configureImagesPdfLayout(layout: ImagePdfLayout) {
        if (selectedImageUris.isNotEmpty()) selectedImageLayout = layout
    }

    fun discardSelectedImages() {
        selectedImageUris = emptyList()
        imagesToPdfState = ImagesToPdfState.Idle
    }

    fun createPdfFromSelectedImages(outputUri: Uri) {
        val imageUris = selectedImageUris
        val layout = selectedImageLayout
        selectedImageUris = emptyList()
        if (imageUris.isEmpty()) return
        imageCreationJob?.cancel()
        imagesToPdfState = ImagesToPdfState.Creating(imageUris.size)
        imageCreationJob = viewModelScope.launch {
            when (val result = imagesToPdfEngine.create(imageUris, outputUri, layout)) {
                is ImagesToPdfResult.Success -> {
                    imagesToPdfState = ImagesToPdfState.Idle
                    open(outputUri)
                }
                is ImagesToPdfResult.InvalidImage -> {
                    imagesToPdfState = ImagesToPdfState.Failed(ImagesToPdfFailure.InvalidImage)
                }
                ImagesToPdfResult.PermissionDenied -> {
                    imagesToPdfState = ImagesToPdfState.Failed(ImagesToPdfFailure.PermissionDenied)
                }
                ImagesToPdfResult.InsufficientMemory -> {
                    imagesToPdfState = ImagesToPdfState.Failed(ImagesToPdfFailure.InsufficientMemory)
                }
                ImagesToPdfResult.Failed -> {
                    imagesToPdfState = ImagesToPdfState.Failed(ImagesToPdfFailure.UnableToSave)
                }
            }
        }
    }

    fun clearImagesToPdfFailure() {
        imagesToPdfState = ImagesToPdfState.Idle
    }

    fun selectPdfsForMerge(uris: List<Uri>) {
        mergeJob?.cancel()
        val uniqueUris = uris.distinct()
        if (uniqueUris.size < 2) {
            mergePdfState = MergePdfState.Failed(MergePdfFailure.NeedAtLeastTwo)
            return
        }
        mergePdfState = MergePdfState.Preparing
        mergeJob = viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                uniqueUris.mapIndexed { index, uri ->
                    MergePdfItem(uri, queryDisplayName(uri) ?: "PDF ${index + 1}")
                }
            }
            mergePdfState = MergePdfState.Configuring(items)
        }
    }

    fun moveMergeDocument(fromIndex: Int, toIndex: Int) {
        val configuring = mergePdfState as? MergePdfState.Configuring ?: return
        if (fromIndex !in configuring.documents.indices || toIndex !in configuring.documents.indices) return
        val reordered = configuring.documents.toMutableList()
        val item = reordered.removeAt(fromIndex)
        reordered.add(toIndex, item)
        mergePdfState = configuring.copy(documents = reordered)
    }

    fun removeMergeDocument(index: Int) {
        val configuring = mergePdfState as? MergePdfState.Configuring ?: return
        if (index !in configuring.documents.indices) return
        val updated = configuring.documents.toMutableList().apply { removeAt(index) }
        mergePdfState = if (updated.size < 2) {
            MergePdfState.Failed(MergePdfFailure.NeedAtLeastTwo)
        } else {
            configuring.copy(documents = updated)
        }
    }

    fun mergeSelectedPdfs(outputUri: Uri) {
        val configuring = mergePdfState as? MergePdfState.Configuring ?: return
        val sourceUris = configuring.documents.map(MergePdfItem::uri)
        mergePdfState = MergePdfState.Merging(sourceUris.size)
        mergeJob?.cancel()
        mergeJob = viewModelScope.launch {
            mergePdfState = when (val result = mergePdfEngine.merge(sourceUris, outputUri)) {
                is MergePdfResult.Success -> {
                    open(outputUri)
                    MergePdfState.Idle
                }
                is MergePdfResult.InvalidDocument -> {
                    MergePdfState.Failed(MergePdfFailure.InvalidDocument)
                }
                MergePdfResult.PermissionDenied -> {
                    MergePdfState.Failed(MergePdfFailure.PermissionDenied)
                }
                MergePdfResult.InsufficientMemory -> {
                    MergePdfState.Failed(MergePdfFailure.InsufficientMemory)
                }
                MergePdfResult.Failed -> MergePdfState.Failed(MergePdfFailure.UnableToSave)
            }
        }
    }

    fun cancelMergePdf() {
        mergeJob?.cancel()
        mergePdfState = MergePdfState.Idle
    }

    fun clearMergePdfFailure() {
        mergePdfState = MergePdfState.Idle
    }

    fun selectPdfForSplit(uri: Uri) {
        splitJob?.cancel()
        selectedSplitRanges = emptyList()
        splitPdfState = SplitPdfState.Preparing
        splitJob = viewModelScope.launch {
            splitPdfState = when (val result = withContext(Dispatchers.IO) { opener.open(uri) }) {
                is PdfOpenResult.Success -> {
                    if (result.document.pageCount < 2) {
                        SplitPdfState.Failed(SplitPdfFailure.NeedAtLeastTwoPages)
                    } else {
                        SplitPdfState.Configuring(
                            sourceUri = uri,
                            displayName = result.document.displayName ?: "PDF",
                            pageCount = result.document.pageCount,
                        )
                    }
                }
                is PdfOpenResult.Failure -> when (result.reason) {
                    PdfOpenFailure.PermissionDenied -> {
                        SplitPdfState.Failed(SplitPdfFailure.PermissionDenied)
                    }
                    PdfOpenFailure.Empty -> {
                        SplitPdfState.Failed(SplitPdfFailure.NeedAtLeastTwoPages)
                    }
                    else -> SplitPdfState.Failed(SplitPdfFailure.InvalidDocument)
                }
            }
        }
    }

    fun configureSplitPdf(ranges: List<SplitPageRange>) {
        val configuring = splitPdfState as? SplitPdfState.Configuring ?: return
        selectedSplitRanges = ranges
        splitPdfState = configuring
    }

    fun splitSelectedPdf(outputDirectoryUri: Uri) {
        val configuring = splitPdfState as? SplitPdfState.Configuring ?: return
        val ranges = selectedSplitRanges
        if (ranges.isEmpty()) {
            splitPdfState = SplitPdfState.Failed(SplitPdfFailure.InvalidPlan)
            return
        }
        splitJob?.cancel()
        splitPdfState = SplitPdfState.Splitting(0, ranges.size)
        splitJob = viewModelScope.launch {
            splitPdfState = when (
                val result = splitPdfEngine.split(
                    sourceUri = configuring.sourceUri,
                    outputDirectoryUri = outputDirectoryUri,
                    sourceDisplayName = configuring.displayName,
                    ranges = ranges,
                    onProgress = { completed, total ->
                        withContext(Dispatchers.Main.immediate) {
                            splitPdfState = SplitPdfState.Splitting(completed, total)
                        }
                    },
                )
            ) {
                is SplitPdfResult.Success -> SplitPdfState.Completed(result.outputCount)
                SplitPdfResult.InvalidDocument -> {
                    SplitPdfState.Failed(SplitPdfFailure.InvalidDocument)
                }
                SplitPdfResult.InvalidPlan -> SplitPdfState.Failed(SplitPdfFailure.InvalidPlan)
                SplitPdfResult.PermissionDenied -> {
                    SplitPdfState.Failed(SplitPdfFailure.PermissionDenied)
                }
                SplitPdfResult.InsufficientMemory -> {
                    SplitPdfState.Failed(SplitPdfFailure.InsufficientMemory)
                }
                SplitPdfResult.Failed -> SplitPdfState.Failed(SplitPdfFailure.UnableToSave)
            }
            selectedSplitRanges = emptyList()
        }
    }

    fun cancelSplitPdf() {
        splitJob?.cancel()
        selectedSplitRanges = emptyList()
        splitPdfState = SplitPdfState.Idle
    }

    fun clearSplitPdfResult() {
        selectedSplitRanges = emptyList()
        splitPdfState = SplitPdfState.Idle
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

    suspend fun inspectHealth(): PdfHealthResult {
        val opened = state as? PdfOpenState.Opened ?: return PdfHealthResult.Failed
        return healthEngine.inspect(opened.uri, opened.pageCount)
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
        imageCreationJob?.cancel()
        mergeJob?.cancel()
        splitJob?.cancel()
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

    private fun queryDisplayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column < 0 || cursor.isNull(column)) null
            else cursor.getString(column)?.trim()?.takeIf(String::isNotEmpty)
        }
    } catch (_: RuntimeException) {
        null
    }
}
