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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

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

sealed interface ScannerCaptureState {
    data object Idle : ScannerCaptureState
    data object Camera : ScannerCaptureState
    data object Capturing : ScannerCaptureState
    data object PreparingPreview : ScannerCaptureState
    data class Review(
        val preview: ScannerCapturePreview,
        val crop: ScannerCropSelection = preview.suggestedCrop,
        val enhancement: ScannerEnhancementSettings = ScannerEnhancementSettings(),
        val enhancedPreview: Bitmap? = null,
        val enhancementInProgress: Boolean = false,
        val enhancementFailure: ScannerCaptureFailure? = null,
        val saveFailure: ScannerCaptureFailure? = null,
    ) : ScannerCaptureState
    data object CreatingPdf : ScannerCaptureState
    data class Completed(val outputUri: Uri) : ScannerCaptureState
    data class Failed(val failure: ScannerCaptureFailure) : ScannerCaptureState
}

enum class ScannerCaptureFailure(@get:StringRes val messageResource: Int) {
    PermissionDenied(R.string.scanner_permission_denied),
    CameraUnavailable(R.string.scanner_camera_unavailable),
    CaptureFailed(R.string.scanner_capture_failed),
    InvalidCapture(R.string.scanner_invalid_capture),
    InvalidCrop(R.string.scanner_invalid_crop),
    EnhancementFailed(R.string.scanner_enhancement_failed),
    InsufficientMemory(R.string.scanner_memory_error),
    UnableToSave(R.string.scanner_save_error),
    SavePermissionDenied(R.string.scanner_save_permission_denied),
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

sealed interface ExtractPagesState {
    data object Idle : ExtractPagesState
    data object Preparing : ExtractPagesState
    data class Configuring(
        val sourceUri: Uri,
        val displayName: String,
        val pageCount: Int,
    ) : ExtractPagesState
    data class Extracting(val selectedPageCount: Int) : ExtractPagesState
    data class Failed(val failure: ExtractPagesFailure) : ExtractPagesState
}

sealed interface DeletePagesState {
    data object Idle : DeletePagesState
    data object Preparing : DeletePagesState
    data class Configuring(
        val sourceUri: Uri,
        val displayName: String,
        val pageCount: Int,
    ) : DeletePagesState
    data class Deleting(val deletedPageCount: Int, val remainingPageCount: Int) : DeletePagesState
    data class Failed(val failure: DeletePagesFailure) : DeletePagesState
}

sealed interface RearrangePagesState {
    data object Idle : RearrangePagesState
    data object Preparing : RearrangePagesState
    data class Configuring(
        val sourceUri: Uri,
        val displayName: String,
        val pageCount: Int,
        val pageOrder: List<Int>,
    ) : RearrangePagesState
    data class Rearranging(val pageCount: Int) : RearrangePagesState
    data class Failed(val failure: RearrangePagesFailure) : RearrangePagesState
}

enum class RearrangePagesFailure(@get:StringRes val messageResource: Int) {
    NeedAtLeastTwoPages(R.string.rearrange_pages_need_two_pages),
    InvalidDocument(R.string.rearrange_pages_invalid_document),
    InvalidOrder(R.string.rearrange_pages_invalid_order),
    PermissionDenied(R.string.rearrange_pages_permission_denied),
    InsufficientMemory(R.string.rearrange_pages_memory_error),
    UnableToSave(R.string.rearrange_pages_save_error),
}

sealed interface RotatePagesState {
    data object Idle : RotatePagesState
    data object Preparing : RotatePagesState
    data class Configuring(
        val sourceUri: Uri,
        val displayName: String,
        val pageCount: Int,
    ) : RotatePagesState
    data class Rotating(val selectedPageCount: Int) : RotatePagesState
    data class Failed(val failure: RotatePagesFailure) : RotatePagesState
}

sealed interface DuplicatePagesState {
    data object Idle : DuplicatePagesState
    data object Preparing : DuplicatePagesState
    data class Configuring(
        val sourceUri: Uri,
        val displayName: String,
        val pageCount: Int,
    ) : DuplicatePagesState
    data class Duplicating(val selectedPageCount: Int, val outputPageCount: Int) : DuplicatePagesState
    data class Failed(val failure: DuplicatePagesFailure) : DuplicatePagesState
}

enum class DuplicatePagesFailure(@get:StringRes val messageResource: Int) {
    InvalidDocument(R.string.duplicate_pages_invalid_document),
    InvalidSelection(R.string.duplicate_pages_invalid_selection),
    PermissionDenied(R.string.duplicate_pages_permission_denied),
    InsufficientMemory(R.string.duplicate_pages_memory_error),
    UnableToSave(R.string.duplicate_pages_save_error),
}

sealed interface CompressPdfState {
    data object Idle : CompressPdfState
    data object Preparing : CompressPdfState
    data class Configuring(
        val sourceUri: Uri,
        val displayName: String,
        val analysis: CompressPdfAnalysis,
    ) : CompressPdfState
    data class Compressing(
        val completedPages: Int,
        val totalPages: Int,
        val attempt: Int = 1,
        val totalAttempts: Int = 1,
    ) : CompressPdfState
    data class Completed(
        val outputUri: Uri,
        val originalSizeBytes: Long,
        val outputSizeBytes: Long,
        val recompressedImageCount: Int,
        val targetSizeBytes: Long? = null,
        val targetReached: Boolean = true,
    ) : CompressPdfState
    data class Failed(val failure: CompressPdfFailure) : CompressPdfState
}

enum class CompressPdfFailure(@get:StringRes val messageResource: Int) {
    InvalidDocument(R.string.compress_pdf_invalid_document),
    NotSmaller(R.string.compress_pdf_not_smaller),
    InvalidTargetSize(R.string.compress_pdf_invalid_target),
    PermissionDenied(R.string.compress_pdf_permission_denied),
    InsufficientMemory(R.string.compress_pdf_memory_error),
    UnableToSave(R.string.compress_pdf_save_error),
}

enum class RotatePagesFailure(@get:StringRes val messageResource: Int) {
    InvalidDocument(R.string.rotate_pages_invalid_document),
    InvalidSelection(R.string.rotate_pages_invalid_selection),
    PermissionDenied(R.string.rotate_pages_permission_denied),
    InsufficientMemory(R.string.rotate_pages_memory_error),
    UnableToSave(R.string.rotate_pages_save_error),
}

enum class DeletePagesFailure(@get:StringRes val messageResource: Int) {
    NeedAtLeastTwoPages(R.string.delete_pages_need_two_pages),
    InvalidDocument(R.string.delete_pages_invalid_document),
    InvalidSelection(R.string.delete_pages_invalid_selection),
    PermissionDenied(R.string.delete_pages_permission_denied),
    InsufficientMemory(R.string.delete_pages_memory_error),
    UnableToSave(R.string.delete_pages_save_error),
}

enum class ExtractPagesFailure(@get:StringRes val messageResource: Int) {
    InvalidDocument(R.string.extract_pages_invalid_document),
    InvalidSelection(R.string.extract_pages_invalid_selection),
    PermissionDenied(R.string.extract_pages_permission_denied),
    InsufficientMemory(R.string.extract_pages_memory_error),
    UnableToSave(R.string.extract_pages_save_error),
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
    private val extractPagesEngine = ExtractPagesEngine(application)
    private val deletePagesEngine = DeletePagesEngine(application)
    private val rearrangePagesEngine = RearrangePagesEngine(application)
    private val rotatePagesEngine = RotatePagesEngine(application)
    private val duplicatePagesEngine = DuplicatePagesEngine(application)
    private val compressPdfEngine = CompressPdfEngine(application)
    private val scannerCaptureEngine = ScannerCaptureEngine(application)
    private val scannerCropCorrectionEngine = ScannerCropCorrectionEngine(application.cacheDir)
    private val scannerEnhancementEngine = ScannerEnhancementEngine(application.cacheDir)
    private var openJob: Job? = null
    private var imageCreationJob: Job? = null
    private var mergeJob: Job? = null
    private var splitJob: Job? = null
    private var extractPagesJob: Job? = null
    private var deletePagesJob: Job? = null
    private var rearrangePagesJob: Job? = null
    private var rotatePagesJob: Job? = null
    private var duplicatePagesJob: Job? = null
    private var compressPdfJob: Job? = null
    private var scannerJob: Job? = null
    private var scannerEnhancementJob: Job? = null
    private var scannerCaptureFile: File? = null
    private var scannerPreview: ScannerCapturePreview? = null
    private var scannerEnhancedPreview: Bitmap? = null
    private var selectedImageUris: List<Uri> = emptyList()
    private var selectedImageLayout = ImagePdfLayout()
    private var selectedSplitRanges: List<SplitPageRange> = emptyList()
    private var selectedExtractPageIndices = IntArray(0)
    private var selectedDeletedPageIndices = IntArray(0)
    private var selectedRotatedPageIndices = IntArray(0)
    private var selectedPageRotation = PageRotation.Clockwise90
    private var selectedDuplicatedPageIndices = IntArray(0)
    private var selectedCompressionRequest: PdfCompressionRequest =
        PdfCompressionRequest.Quality(PdfCompressionMode.Balanced)
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

    var extractPagesState: ExtractPagesState by mutableStateOf(ExtractPagesState.Idle)
        private set

    var deletePagesState: DeletePagesState by mutableStateOf(DeletePagesState.Idle)
        private set

    var rearrangePagesState: RearrangePagesState by mutableStateOf(RearrangePagesState.Idle)
        private set

    var rotatePagesState: RotatePagesState by mutableStateOf(RotatePagesState.Idle)
        private set

    var duplicatePagesState: DuplicatePagesState by mutableStateOf(DuplicatePagesState.Idle)
        private set

    var compressPdfState: CompressPdfState by mutableStateOf(CompressPdfState.Idle)
        private set

    var scannerCaptureState: ScannerCaptureState by mutableStateOf(ScannerCaptureState.Idle)
        private set

    fun startScannerCapture() {
        clearScannerCapture()
        scannerCaptureState = ScannerCaptureState.Camera
    }

    fun scannerPermissionDenied() {
        clearScannerCapture()
        scannerCaptureState = ScannerCaptureState.Failed(ScannerCaptureFailure.PermissionDenied)
    }

    fun scannerCameraUnavailable() {
        if (scannerCaptureState !is ScannerCaptureState.Camera) return
        clearScannerCapture()
        scannerCaptureState = ScannerCaptureState.Failed(ScannerCaptureFailure.CameraUnavailable)
    }

    fun beginScannerCapture(): File? {
        if (scannerCaptureState !is ScannerCaptureState.Camera) return null
        return try {
            scannerCaptureEngine.createCaptureFile().also { file ->
                scannerCaptureFile = file
                scannerCaptureState = ScannerCaptureState.Capturing
            }
        } catch (_: Exception) {
            scannerCaptureState = ScannerCaptureState.Failed(ScannerCaptureFailure.CaptureFailed)
            null
        }
    }

    fun scannerCaptureSaved(captureFile: File) {
        if (scannerCaptureState !is ScannerCaptureState.Capturing || scannerCaptureFile != captureFile) {
            scannerCaptureEngine.discard(captureFile)
            return
        }
        scannerCaptureState = ScannerCaptureState.PreparingPreview
        scannerJob?.cancel()
        scannerJob = viewModelScope.launch {
            when (val result = scannerCaptureEngine.preparePreview(captureFile)) {
                is ScannerPreviewResult.Ready -> {
                    scannerPreview = result.preview
                    scannerCaptureState = ScannerCaptureState.Review(result.preview)
                    refreshScannerEnhancementPreview(debounce = false)
                }
                ScannerPreviewResult.InvalidImage -> failScannerCapture(
                    ScannerCaptureFailure.InvalidCapture,
                )
                ScannerPreviewResult.InsufficientMemory -> failScannerCapture(
                    ScannerCaptureFailure.InsufficientMemory,
                )
                ScannerPreviewResult.Failed -> failScannerCapture(
                    ScannerCaptureFailure.CaptureFailed,
                )
            }
        }
    }

    fun scannerCaptureFailed(captureFile: File?) {
        if (captureFile != null && captureFile != scannerCaptureFile) {
            scannerCaptureEngine.discard(captureFile)
            return
        }
        failScannerCapture(ScannerCaptureFailure.CaptureFailed)
    }

    fun retakeScannerCapture() {
        if (scannerCaptureState !is ScannerCaptureState.Review) return
        scannerEnhancementJob?.cancel()
        scannerEnhancementJob = null
        releaseScannerCapture()
        scannerCaptureState = ScannerCaptureState.Camera
    }

    fun updateScannerCrop(crop: ScannerCropSelection) {
        val review = scannerCaptureState as? ScannerCaptureState.Review ?: return
        if (!ScannerCropGeometry.isValid(crop)) return
        scannerCaptureState = review.copy(crop = crop, saveFailure = null)
        refreshScannerEnhancementPreview(debounce = true)
    }

    fun resetScannerCrop() {
        val review = scannerCaptureState as? ScannerCaptureState.Review ?: return
        scannerCaptureState = review.copy(
            crop = review.preview.suggestedCrop,
            saveFailure = null,
        )
    }

    fun updateScannerEnhancement(settings: ScannerEnhancementSettings) {
        val review = scannerCaptureState as? ScannerCaptureState.Review ?: return
        val normalized = settings.normalized()
        if (normalized == review.enhancement) return
        scannerCaptureState = review.copy(
            enhancement = normalized,
            enhancementInProgress = true,
            enhancementFailure = null,
            saveFailure = null,
        )
        refreshScannerEnhancementPreview(debounce = true)
    }

    fun createScannerPdf(outputUri: Uri) {
        val review = scannerCaptureState as? ScannerCaptureState.Review ?: return
        val captureFile = scannerCaptureFile ?: return
        scannerEnhancementJob?.cancel()
        scannerEnhancementJob = null
        scannerCaptureState = ScannerCaptureState.CreatingPdf
        scannerJob?.cancel()
        scannerJob = viewModelScope.launch {
            when (
                scannerCaptureEngine.createSinglePagePdf(
                    captureFile,
                    outputUri,
                    review.crop,
                    review.enhancement,
                )
            ) {
                ScannerPdfResult.Success -> {
                    releaseScannerCapture()
                    scannerCaptureState = ScannerCaptureState.Completed(outputUri)
                }
                ScannerPdfResult.InvalidImage -> failScannerCapture(
                    ScannerCaptureFailure.InvalidCapture,
                )
                ScannerPdfResult.InvalidCrop -> {
                    scannerCaptureState = review.copy(
                        saveFailure = ScannerCaptureFailure.InvalidCrop,
                    )
                }
                ScannerPdfResult.PermissionDenied -> {
                    scannerCaptureState = review.copy(
                        saveFailure = ScannerCaptureFailure.SavePermissionDenied,
                    )
                }
                ScannerPdfResult.InsufficientMemory -> {
                    scannerCaptureState = review.copy(
                        saveFailure = ScannerCaptureFailure.InsufficientMemory,
                    )
                }
                ScannerPdfResult.Failed -> {
                    scannerCaptureState = review.copy(
                        saveFailure = ScannerCaptureFailure.UnableToSave,
                    )
                }
            }
        }
    }

    fun openScannerPdf() {
        val completed = scannerCaptureState as? ScannerCaptureState.Completed ?: return
        scannerCaptureState = ScannerCaptureState.Idle
        open(completed.outputUri)
    }

    fun cancelScannerCapture() {
        clearScannerCapture()
        scannerCaptureState = ScannerCaptureState.Idle
    }

    fun clearScannerResult() {
        clearScannerCapture()
        scannerCaptureState = ScannerCaptureState.Idle
    }

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

    fun selectPdfForExtraction(uri: Uri) {
        extractPagesJob?.cancel()
        selectedExtractPageIndices = IntArray(0)
        extractPagesState = ExtractPagesState.Preparing
        extractPagesJob = viewModelScope.launch {
            extractPagesState = when (val result = withContext(Dispatchers.IO) { opener.open(uri) }) {
                is PdfOpenResult.Success -> ExtractPagesState.Configuring(
                    sourceUri = uri,
                    displayName = result.document.displayName ?: "PDF",
                    pageCount = result.document.pageCount,
                )
                is PdfOpenResult.Failure -> when (result.reason) {
                    PdfOpenFailure.PermissionDenied -> {
                        ExtractPagesState.Failed(ExtractPagesFailure.PermissionDenied)
                    }
                    else -> ExtractPagesState.Failed(ExtractPagesFailure.InvalidDocument)
                }
            }
        }
    }

    fun configurePageExtraction(selectedPageIndices: IntArray) {
        if (extractPagesState !is ExtractPagesState.Configuring) return
        selectedExtractPageIndices = selectedPageIndices.copyOf()
    }

    fun extractSelectedPages(outputUri: Uri) {
        val configuring = extractPagesState as? ExtractPagesState.Configuring ?: return
        val selectedPages = selectedExtractPageIndices.copyOf()
        if (selectedPages.isEmpty()) {
            extractPagesState = ExtractPagesState.Failed(ExtractPagesFailure.InvalidSelection)
            return
        }
        extractPagesJob?.cancel()
        extractPagesState = ExtractPagesState.Extracting(selectedPages.size)
        extractPagesJob = viewModelScope.launch {
            extractPagesState = when (
                val result = extractPagesEngine.extract(
                    sourceUri = configuring.sourceUri,
                    outputUri = outputUri,
                    selectedPageIndices = selectedPages,
                )
            ) {
                is ExtractPagesResult.Success -> {
                    open(outputUri)
                    ExtractPagesState.Idle
                }
                ExtractPagesResult.InvalidDocument -> {
                    ExtractPagesState.Failed(ExtractPagesFailure.InvalidDocument)
                }
                ExtractPagesResult.InvalidSelection -> {
                    ExtractPagesState.Failed(ExtractPagesFailure.InvalidSelection)
                }
                ExtractPagesResult.PermissionDenied -> {
                    ExtractPagesState.Failed(ExtractPagesFailure.PermissionDenied)
                }
                ExtractPagesResult.InsufficientMemory -> {
                    ExtractPagesState.Failed(ExtractPagesFailure.InsufficientMemory)
                }
                ExtractPagesResult.Failed -> {
                    ExtractPagesState.Failed(ExtractPagesFailure.UnableToSave)
                }
            }
            selectedExtractPageIndices = IntArray(0)
        }
    }

    fun cancelExtractPages() {
        extractPagesJob?.cancel()
        selectedExtractPageIndices = IntArray(0)
        extractPagesState = ExtractPagesState.Idle
    }

    fun clearExtractPagesFailure() {
        selectedExtractPageIndices = IntArray(0)
        extractPagesState = ExtractPagesState.Idle
    }

    fun selectPdfForPageDeletion(uri: Uri) {
        deletePagesJob?.cancel()
        selectedDeletedPageIndices = IntArray(0)
        deletePagesState = DeletePagesState.Preparing
        deletePagesJob = viewModelScope.launch {
            deletePagesState = when (val result = withContext(Dispatchers.IO) { opener.open(uri) }) {
                is PdfOpenResult.Success -> {
                    if (result.document.pageCount < 2) {
                        DeletePagesState.Failed(DeletePagesFailure.NeedAtLeastTwoPages)
                    } else {
                        DeletePagesState.Configuring(
                            sourceUri = uri,
                            displayName = result.document.displayName ?: "PDF",
                            pageCount = result.document.pageCount,
                        )
                    }
                }
                is PdfOpenResult.Failure -> when (result.reason) {
                    PdfOpenFailure.PermissionDenied -> {
                        DeletePagesState.Failed(DeletePagesFailure.PermissionDenied)
                    }
                    PdfOpenFailure.Empty -> {
                        DeletePagesState.Failed(DeletePagesFailure.NeedAtLeastTwoPages)
                    }
                    else -> DeletePagesState.Failed(DeletePagesFailure.InvalidDocument)
                }
            }
        }
    }

    fun configurePageDeletion(deletedPageIndices: IntArray) {
        if (deletePagesState !is DeletePagesState.Configuring) return
        selectedDeletedPageIndices = deletedPageIndices.copyOf()
    }

    fun deleteSelectedPages(outputUri: Uri) {
        val configuring = deletePagesState as? DeletePagesState.Configuring ?: return
        val deletedPages = selectedDeletedPageIndices.copyOf()
        val keptPages = DeletePageSelectionPlanner.keptPageIndices(
            configuring.pageCount,
            deletedPages,
        )
        if (keptPages == null) {
            deletePagesState = DeletePagesState.Failed(DeletePagesFailure.InvalidSelection)
            return
        }
        deletePagesJob?.cancel()
        deletePagesState = DeletePagesState.Deleting(deletedPages.size, keptPages.size)
        deletePagesJob = viewModelScope.launch {
            deletePagesState = when (
                val result = deletePagesEngine.deletePages(
                    sourceUri = configuring.sourceUri,
                    outputUri = outputUri,
                    sourcePageCount = configuring.pageCount,
                    deletedPageIndices = deletedPages,
                )
            ) {
                is DeletePagesResult.Success -> {
                    open(outputUri)
                    DeletePagesState.Idle
                }
                DeletePagesResult.InvalidDocument -> {
                    DeletePagesState.Failed(DeletePagesFailure.InvalidDocument)
                }
                DeletePagesResult.InvalidSelection -> {
                    DeletePagesState.Failed(DeletePagesFailure.InvalidSelection)
                }
                DeletePagesResult.PermissionDenied -> {
                    DeletePagesState.Failed(DeletePagesFailure.PermissionDenied)
                }
                DeletePagesResult.InsufficientMemory -> {
                    DeletePagesState.Failed(DeletePagesFailure.InsufficientMemory)
                }
                DeletePagesResult.Failed -> {
                    DeletePagesState.Failed(DeletePagesFailure.UnableToSave)
                }
            }
            selectedDeletedPageIndices = IntArray(0)
        }
    }

    fun cancelDeletePages() {
        deletePagesJob?.cancel()
        selectedDeletedPageIndices = IntArray(0)
        deletePagesState = DeletePagesState.Idle
    }

    fun clearDeletePagesFailure() {
        selectedDeletedPageIndices = IntArray(0)
        deletePagesState = DeletePagesState.Idle
    }

    fun selectPdfForPageRearrangement(uri: Uri) {
        rearrangePagesJob?.cancel()
        rearrangePagesState = RearrangePagesState.Preparing
        rearrangePagesJob = viewModelScope.launch {
            rearrangePagesState = when (val result = withContext(Dispatchers.IO) { opener.open(uri) }) {
                is PdfOpenResult.Success -> {
                    if (result.document.pageCount < 2) {
                        RearrangePagesState.Failed(RearrangePagesFailure.NeedAtLeastTwoPages)
                    } else {
                        RearrangePagesState.Configuring(
                            sourceUri = uri,
                            displayName = result.document.displayName ?: "PDF",
                            pageCount = result.document.pageCount,
                            pageOrder = RearrangePageOrder.initial(result.document.pageCount),
                        )
                    }
                }
                is PdfOpenResult.Failure -> when (result.reason) {
                    PdfOpenFailure.PermissionDenied -> {
                        RearrangePagesState.Failed(RearrangePagesFailure.PermissionDenied)
                    }
                    PdfOpenFailure.Empty -> {
                        RearrangePagesState.Failed(RearrangePagesFailure.NeedAtLeastTwoPages)
                    }
                    else -> RearrangePagesState.Failed(RearrangePagesFailure.InvalidDocument)
                }
            }
        }
    }

    fun moveRearrangedPage(fromIndex: Int, toIndex: Int) {
        val configuring = rearrangePagesState as? RearrangePagesState.Configuring ?: return
        rearrangePagesState = configuring.copy(
            pageOrder = RearrangePageOrder.move(configuring.pageOrder, fromIndex, toIndex),
        )
    }

    fun resetRearrangedPageOrder() {
        val configuring = rearrangePagesState as? RearrangePagesState.Configuring ?: return
        rearrangePagesState = configuring.copy(
            pageOrder = RearrangePageOrder.initial(configuring.pageCount),
        )
    }

    fun rearrangeSelectedPdf(outputUri: Uri) {
        val configuring = rearrangePagesState as? RearrangePagesState.Configuring ?: return
        val pageOrder = configuring.pageOrder.toIntArray()
        if (!RearrangePageOrder.isCompletePermutation(pageOrder, configuring.pageCount)) {
            rearrangePagesState = RearrangePagesState.Failed(RearrangePagesFailure.InvalidOrder)
            return
        }
        rearrangePagesJob?.cancel()
        rearrangePagesState = RearrangePagesState.Rearranging(configuring.pageCount)
        rearrangePagesJob = viewModelScope.launch {
            rearrangePagesState = when (
                val result = rearrangePagesEngine.rearrange(
                    sourceUri = configuring.sourceUri,
                    outputUri = outputUri,
                    pageOrder = pageOrder,
                    expectedSourcePageCount = configuring.pageCount,
                )
            ) {
                is RearrangePagesResult.Success -> {
                    open(outputUri)
                    RearrangePagesState.Idle
                }
                RearrangePagesResult.InvalidDocument -> {
                    RearrangePagesState.Failed(RearrangePagesFailure.InvalidDocument)
                }
                RearrangePagesResult.InvalidOrder -> {
                    RearrangePagesState.Failed(RearrangePagesFailure.InvalidOrder)
                }
                RearrangePagesResult.PermissionDenied -> {
                    RearrangePagesState.Failed(RearrangePagesFailure.PermissionDenied)
                }
                RearrangePagesResult.InsufficientMemory -> {
                    RearrangePagesState.Failed(RearrangePagesFailure.InsufficientMemory)
                }
                RearrangePagesResult.Failed -> {
                    RearrangePagesState.Failed(RearrangePagesFailure.UnableToSave)
                }
            }
        }
    }

    fun cancelRearrangePages() {
        rearrangePagesJob?.cancel()
        rearrangePagesState = RearrangePagesState.Idle
    }

    fun clearRearrangePagesFailure() {
        rearrangePagesState = RearrangePagesState.Idle
    }

    fun selectPdfForPageRotation(uri: Uri) {
        rotatePagesJob?.cancel()
        selectedRotatedPageIndices = IntArray(0)
        selectedPageRotation = PageRotation.Clockwise90
        rotatePagesState = RotatePagesState.Preparing
        rotatePagesJob = viewModelScope.launch {
            rotatePagesState = when (val result = withContext(Dispatchers.IO) { opener.open(uri) }) {
                is PdfOpenResult.Success -> RotatePagesState.Configuring(
                    sourceUri = uri,
                    displayName = result.document.displayName ?: "PDF",
                    pageCount = result.document.pageCount,
                )
                is PdfOpenResult.Failure -> when (result.reason) {
                    PdfOpenFailure.PermissionDenied -> {
                        RotatePagesState.Failed(RotatePagesFailure.PermissionDenied)
                    }
                    else -> RotatePagesState.Failed(RotatePagesFailure.InvalidDocument)
                }
            }
        }
    }

    fun configurePageRotation(selectedPageIndices: IntArray, rotation: PageRotation) {
        if (rotatePagesState !is RotatePagesState.Configuring) return
        selectedRotatedPageIndices = selectedPageIndices.copyOf()
        selectedPageRotation = rotation
    }

    fun rotateSelectedPages(outputUri: Uri) {
        val configuring = rotatePagesState as? RotatePagesState.Configuring ?: return
        val selectedPages = selectedRotatedPageIndices.copyOf()
        if (!RotatePageSelection.isValid(selectedPages, configuring.pageCount)) {
            rotatePagesState = RotatePagesState.Failed(RotatePagesFailure.InvalidSelection)
            return
        }
        val rotation = selectedPageRotation
        rotatePagesJob?.cancel()
        rotatePagesState = RotatePagesState.Rotating(selectedPages.size)
        rotatePagesJob = viewModelScope.launch {
            rotatePagesState = when (
                val result = rotatePagesEngine.rotate(
                    sourceUri = configuring.sourceUri,
                    outputUri = outputUri,
                    selectedPageIndices = selectedPages,
                    rotation = rotation,
                    expectedSourcePageCount = configuring.pageCount,
                )
            ) {
                is RotatePagesResult.Success -> {
                    open(outputUri)
                    RotatePagesState.Idle
                }
                RotatePagesResult.InvalidDocument -> {
                    RotatePagesState.Failed(RotatePagesFailure.InvalidDocument)
                }
                RotatePagesResult.InvalidSelection -> {
                    RotatePagesState.Failed(RotatePagesFailure.InvalidSelection)
                }
                RotatePagesResult.PermissionDenied -> {
                    RotatePagesState.Failed(RotatePagesFailure.PermissionDenied)
                }
                RotatePagesResult.InsufficientMemory -> {
                    RotatePagesState.Failed(RotatePagesFailure.InsufficientMemory)
                }
                RotatePagesResult.Failed -> {
                    RotatePagesState.Failed(RotatePagesFailure.UnableToSave)
                }
            }
            selectedRotatedPageIndices = IntArray(0)
        }
    }

    fun cancelRotatePages() {
        rotatePagesJob?.cancel()
        selectedRotatedPageIndices = IntArray(0)
        rotatePagesState = RotatePagesState.Idle
    }

    fun clearRotatePagesFailure() {
        selectedRotatedPageIndices = IntArray(0)
        rotatePagesState = RotatePagesState.Idle
    }

    fun selectPdfForPageDuplication(uri: Uri) {
        duplicatePagesJob?.cancel()
        selectedDuplicatedPageIndices = IntArray(0)
        duplicatePagesState = DuplicatePagesState.Preparing
        duplicatePagesJob = viewModelScope.launch {
            duplicatePagesState = when (val result = withContext(Dispatchers.IO) { opener.open(uri) }) {
                is PdfOpenResult.Success -> DuplicatePagesState.Configuring(
                    sourceUri = uri,
                    displayName = result.document.displayName ?: "PDF",
                    pageCount = result.document.pageCount,
                )
                is PdfOpenResult.Failure -> when (result.reason) {
                    PdfOpenFailure.PermissionDenied -> {
                        DuplicatePagesState.Failed(DuplicatePagesFailure.PermissionDenied)
                    }
                    else -> DuplicatePagesState.Failed(DuplicatePagesFailure.InvalidDocument)
                }
            }
        }
    }

    fun configurePageDuplication(selectedPageIndices: IntArray) {
        if (duplicatePagesState !is DuplicatePagesState.Configuring) return
        selectedDuplicatedPageIndices = selectedPageIndices.copyOf()
    }

    fun duplicateSelectedPages(outputUri: Uri) {
        val configuring = duplicatePagesState as? DuplicatePagesState.Configuring ?: return
        val selectedPages = selectedDuplicatedPageIndices.copyOf()
        val pageOrder = DuplicatePagePlan.outputPageOrder(selectedPages, configuring.pageCount)
        if (pageOrder == null) {
            duplicatePagesState = DuplicatePagesState.Failed(DuplicatePagesFailure.InvalidSelection)
            return
        }
        duplicatePagesJob?.cancel()
        duplicatePagesState = DuplicatePagesState.Duplicating(selectedPages.size, pageOrder.size)
        duplicatePagesJob = viewModelScope.launch {
            duplicatePagesState = when (
                val result = duplicatePagesEngine.duplicate(
                    sourceUri = configuring.sourceUri,
                    outputUri = outputUri,
                    selectedPageIndices = selectedPages,
                    expectedSourcePageCount = configuring.pageCount,
                )
            ) {
                is DuplicatePagesResult.Success -> {
                    open(outputUri)
                    DuplicatePagesState.Idle
                }
                DuplicatePagesResult.InvalidDocument -> {
                    DuplicatePagesState.Failed(DuplicatePagesFailure.InvalidDocument)
                }
                DuplicatePagesResult.InvalidSelection -> {
                    DuplicatePagesState.Failed(DuplicatePagesFailure.InvalidSelection)
                }
                DuplicatePagesResult.PermissionDenied -> {
                    DuplicatePagesState.Failed(DuplicatePagesFailure.PermissionDenied)
                }
                DuplicatePagesResult.InsufficientMemory -> {
                    DuplicatePagesState.Failed(DuplicatePagesFailure.InsufficientMemory)
                }
                DuplicatePagesResult.Failed -> {
                    DuplicatePagesState.Failed(DuplicatePagesFailure.UnableToSave)
                }
            }
            selectedDuplicatedPageIndices = IntArray(0)
        }
    }

    fun cancelDuplicatePages() {
        duplicatePagesJob?.cancel()
        selectedDuplicatedPageIndices = IntArray(0)
        duplicatePagesState = DuplicatePagesState.Idle
    }

    fun clearDuplicatePagesFailure() {
        selectedDuplicatedPageIndices = IntArray(0)
        duplicatePagesState = DuplicatePagesState.Idle
    }

    fun selectPdfForCompression(uri: Uri) {
        compressPdfJob?.cancel()
        selectedCompressionRequest = PdfCompressionRequest.Quality(PdfCompressionMode.Balanced)
        compressPdfState = CompressPdfState.Preparing
        compressPdfJob = viewModelScope.launch {
            compressPdfState = when (val result = compressPdfEngine.analyze(uri)) {
                is CompressPdfAnalysisResult.Ready -> CompressPdfState.Configuring(
                    sourceUri = uri,
                    displayName = withContext(Dispatchers.IO) { queryDisplayName(uri) } ?: "PDF",
                    analysis = result.analysis,
                )
                CompressPdfAnalysisResult.InvalidDocument -> {
                    CompressPdfState.Failed(CompressPdfFailure.InvalidDocument)
                }
                CompressPdfAnalysisResult.PermissionDenied -> {
                    CompressPdfState.Failed(CompressPdfFailure.PermissionDenied)
                }
                CompressPdfAnalysisResult.InsufficientMemory -> {
                    CompressPdfState.Failed(CompressPdfFailure.InsufficientMemory)
                }
                CompressPdfAnalysisResult.Failed -> {
                    CompressPdfState.Failed(CompressPdfFailure.UnableToSave)
                }
            }
        }
    }

    fun configurePdfCompression(request: PdfCompressionRequest) {
        if (compressPdfState !is CompressPdfState.Configuring) return
        selectedCompressionRequest = request
    }

    fun compressSelectedPdf(outputUri: Uri) {
        val configuring = compressPdfState as? CompressPdfState.Configuring ?: return
        val request = selectedCompressionRequest
        compressPdfJob?.cancel()
        compressPdfState = CompressPdfState.Compressing(0, configuring.analysis.pageCount)
        compressPdfJob = viewModelScope.launch {
            compressPdfState = when (
                val result = compressPdfEngine.compress(
                    sourceUri = configuring.sourceUri,
                    outputUri = outputUri,
                    request = request,
                    expectedPageCount = configuring.analysis.pageCount,
                    expectedOriginalSizeBytes = configuring.analysis.originalSizeBytes,
                ) { progress ->
                    compressPdfState = CompressPdfState.Compressing(
                        progress.completedPages,
                        progress.totalPages,
                        progress.attempt,
                        progress.totalAttempts,
                    )
                }
            ) {
                is CompressPdfResult.Success -> CompressPdfState.Completed(
                    outputUri = outputUri,
                    originalSizeBytes = result.originalSizeBytes,
                    outputSizeBytes = result.outputSizeBytes,
                    recompressedImageCount = result.recompressedImageCount,
                    targetSizeBytes = result.targetSizeBytes,
                    targetReached = result.targetReached,
                )
                is CompressPdfResult.NotSmaller -> {
                    CompressPdfState.Failed(CompressPdfFailure.NotSmaller)
                }
                CompressPdfResult.InvalidDocument -> {
                    CompressPdfState.Failed(CompressPdfFailure.InvalidDocument)
                }
                CompressPdfResult.InvalidTargetSize -> {
                    CompressPdfState.Failed(CompressPdfFailure.InvalidTargetSize)
                }
                CompressPdfResult.PermissionDenied -> {
                    CompressPdfState.Failed(CompressPdfFailure.PermissionDenied)
                }
                CompressPdfResult.InsufficientMemory -> {
                    CompressPdfState.Failed(CompressPdfFailure.InsufficientMemory)
                }
                CompressPdfResult.Failed -> {
                    CompressPdfState.Failed(CompressPdfFailure.UnableToSave)
                }
            }
        }
    }

    fun openCompressedPdf() {
        val completed = compressPdfState as? CompressPdfState.Completed ?: return
        compressPdfState = CompressPdfState.Idle
        open(completed.outputUri)
    }

    fun cancelCompressPdf() {
        compressPdfJob?.cancel()
        compressPdfState = CompressPdfState.Idle
    }

    fun clearCompressPdfResult() {
        compressPdfState = CompressPdfState.Idle
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
        extractPagesJob?.cancel()
        deletePagesJob?.cancel()
        duplicatePagesJob?.cancel()
        compressPdfJob?.cancel()
        scannerJob?.cancel()
        scannerEnhancementJob?.cancel()
        releaseScannerCapture()
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

    private fun failScannerCapture(failure: ScannerCaptureFailure) {
        releaseScannerCapture()
        scannerCaptureState = ScannerCaptureState.Failed(failure)
    }

    private fun clearScannerCapture() {
        scannerJob?.cancel()
        scannerJob = null
        scannerEnhancementJob?.cancel()
        scannerEnhancementJob = null
        releaseScannerCapture()
    }

    private fun releaseScannerCapture() {
        scannerEnhancedPreview?.takeUnless(Bitmap::isRecycled)?.recycle()
        scannerEnhancedPreview = null
        scannerPreview?.bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
        scannerPreview = null
        scannerCaptureEngine.discard(scannerCaptureFile)
        scannerCaptureFile = null
    }

    private fun refreshScannerEnhancementPreview(debounce: Boolean) {
        val review = scannerCaptureState as? ScannerCaptureState.Review ?: return
        val expectedPreview = review.preview
        val expectedCrop = review.crop
        val expectedSettings = review.enhancement
        scannerCaptureState = review.copy(
            enhancementInProgress = true,
            enhancementFailure = null,
        )
        scannerEnhancementJob?.cancel()
        scannerEnhancementJob = viewModelScope.launch {
            if (debounce) delay(120)
            val corrected = when (
                val result = scannerCropCorrectionEngine.correctPreview(
                    expectedPreview.bitmap,
                    expectedCrop,
                )
            ) {
                is ScannerCropPreviewResult.Ready -> result.bitmap
                ScannerCropPreviewResult.InvalidCrop -> {
                    updateScannerPreviewFailure(
                        expectedPreview,
                        expectedCrop,
                        expectedSettings,
                        ScannerCaptureFailure.InvalidCrop,
                    )
                    return@launch
                }
                ScannerCropPreviewResult.InsufficientMemory -> {
                    updateScannerPreviewFailure(
                        expectedPreview,
                        expectedCrop,
                        expectedSettings,
                        ScannerCaptureFailure.InsufficientMemory,
                    )
                    return@launch
                }
                ScannerCropPreviewResult.Failed -> {
                    updateScannerPreviewFailure(
                        expectedPreview,
                        expectedCrop,
                        expectedSettings,
                        ScannerCaptureFailure.EnhancementFailed,
                    )
                    return@launch
                }
            }
            val enhancementResult = try {
                scannerEnhancementEngine.enhancePreview(corrected, expectedSettings)
            } finally {
                corrected.recycle()
            }
            when (val result = enhancementResult) {
                is ScannerEnhancementPreviewResult.Ready -> {
                    val current = scannerCaptureState as? ScannerCaptureState.Review
                    if (current?.preview !== expectedPreview || current.crop != expectedCrop ||
                        current.enhancement != expectedSettings
                    ) {
                        result.bitmap.recycle()
                        return@launch
                    }
                    scannerEnhancedPreview?.takeUnless(Bitmap::isRecycled)?.recycle()
                    scannerEnhancedPreview = result.bitmap
                    scannerCaptureState = current.copy(
                        enhancedPreview = result.bitmap,
                        enhancementInProgress = false,
                        enhancementFailure = null,
                    )
                }
                ScannerEnhancementPreviewResult.InsufficientMemory -> {
                    updateScannerPreviewFailure(
                        expectedPreview,
                        expectedCrop,
                        expectedSettings,
                        ScannerCaptureFailure.InsufficientMemory,
                    )
                }
                ScannerEnhancementPreviewResult.Failed -> {
                    updateScannerPreviewFailure(
                        expectedPreview,
                        expectedCrop,
                        expectedSettings,
                        ScannerCaptureFailure.EnhancementFailed,
                    )
                }
            }
        }
    }

    private fun updateScannerPreviewFailure(
        expectedPreview: ScannerCapturePreview,
        expectedCrop: ScannerCropSelection,
        expectedSettings: ScannerEnhancementSettings,
        failure: ScannerCaptureFailure,
    ) {
        val current = scannerCaptureState as? ScannerCaptureState.Review ?: return
        if (current.preview === expectedPreview && current.crop == expectedCrop &&
            current.enhancement == expectedSettings
        ) {
            scannerCaptureState = current.copy(
                enhancementInProgress = false,
                enhancementFailure = failure,
            )
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
