package com.rameshta.quietpdf

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.rameshta.quietpdf.pdf.PdfOpenFailure
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.PdfOpenViewModel
import com.rameshta.quietpdf.pdf.PdfSearchResult
import com.rameshta.quietpdf.pdf.PdfTableOfContentsResult
import com.rameshta.quietpdf.pdf.PdfHealthResult
import com.rameshta.quietpdf.pdf.ImagesToPdfState
import com.rameshta.quietpdf.pdf.ImagePdfLayout
import com.rameshta.quietpdf.pdf.ImagePdfMargin
import com.rameshta.quietpdf.pdf.ImagePdfOrientation
import com.rameshta.quietpdf.pdf.ImagePdfPageSize
import com.rameshta.quietpdf.pdf.ImagePdfScaleMode
import com.rameshta.quietpdf.pdf.MergePdfItem
import com.rameshta.quietpdf.pdf.MergePdfState
import com.rameshta.quietpdf.pdf.SplitPageRange
import com.rameshta.quietpdf.pdf.SplitPdfMode
import com.rameshta.quietpdf.pdf.SplitPdfPlanner
import com.rameshta.quietpdf.pdf.SplitPdfState
import com.rameshta.quietpdf.pdf.ExtractPageSelectionParser
import com.rameshta.quietpdf.pdf.ExtractPagesState
import com.rameshta.quietpdf.pdf.DeletePageSelectionPlanner
import com.rameshta.quietpdf.pdf.DeletePagesState
import com.rameshta.quietpdf.pdf.RearrangePagesState
import com.rameshta.quietpdf.pdf.PageRotation
import com.rameshta.quietpdf.pdf.RotatePagesState
import com.rameshta.quietpdf.pdf.DuplicatePagesState
import com.rameshta.quietpdf.pdf.CompressPdfState
import com.rameshta.quietpdf.pdf.ProtectPdfPassword
import com.rameshta.quietpdf.pdf.ProtectPdfState
import com.rameshta.quietpdf.pdf.RemovePasswordState
import com.rameshta.quietpdf.pdf.ChangePasswordState
import com.rameshta.quietpdf.pdf.PdfCompressionMode
import com.rameshta.quietpdf.pdf.PdfCompressionRequest
import com.rameshta.quietpdf.pdf.TargetFileSize
import com.rameshta.quietpdf.pdf.ScannerCaptureState
import com.rameshta.quietpdf.pdf.ScannerCropPoint
import com.rameshta.quietpdf.pdf.ScannerCropSelection
import com.rameshta.quietpdf.pdf.ScannerColorMode
import com.rameshta.quietpdf.pdf.ScannerEnhancementSettings
import com.rameshta.quietpdf.ui.reader.PdfReaderScreen
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: PdfOpenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) openFromIntent(intent)

        setContent {
            QuietPDFTheme {
                var pendingProtectPassword by remember { mutableStateOf<CharArray?>(null) }
                var pendingRemovalPassword by remember { mutableStateOf<CharArray?>(null) }
                var pendingPasswordChange by remember {
                    mutableStateOf<Pair<CharArray, CharArray>?>(null)
                }
                val picker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.open(uri)
                    }
                }
                val createImagesPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    if (uri == null) viewModel.discardSelectedImages()
                    else viewModel.createPdfFromSelectedImages(uri)
                }
                val imagePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        uris.forEach(::retainReadPermission)
                        viewModel.selectImagesForPdf(uris)
                    }
                }
                val createMergedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    if (uri == null) viewModel.cancelMergePdf()
                    else viewModel.mergeSelectedPdfs(uri)
                }
                val mergePdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        uris.forEach(::retainReadPermission)
                        viewModel.selectPdfsForMerge(uris)
                    }
                }
                val splitOutputFolder = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri == null) viewModel.cancelSplitPdf()
                    else {
                        retainDirectoryPermission(uri)
                        viewModel.splitSelectedPdf(uri)
                    }
                }
                val splitPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForSplit(uri)
                    }
                }
                val createExtractedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    if (uri == null) viewModel.cancelExtractPages()
                    else viewModel.extractSelectedPages(uri)
                }
                val extractPagesPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForExtraction(uri)
                    }
                }
                val createDeletedPagesPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    if (uri == null) viewModel.cancelDeletePages()
                    else viewModel.deleteSelectedPages(uri)
                }
                val deletePagesPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForPageDeletion(uri)
                    }
                }
                val createRearrangedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    if (uri == null) viewModel.cancelRearrangePages()
                    else viewModel.rearrangeSelectedPdf(uri)
                }
                val rearrangePagesPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForPageRearrangement(uri)
                    }
                }
                val createRotatedPagesPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    if (uri == null) viewModel.cancelRotatePages()
                    else viewModel.rotateSelectedPages(uri)
                }
                val rotatePagesPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForPageRotation(uri)
                    }
                }
                val createDuplicatedPagesPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    if (uri == null) viewModel.cancelDuplicatePages()
                    else viewModel.duplicateSelectedPages(uri)
                }
                val duplicatePagesPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForPageDuplication(uri)
                    }
                }
                val createCompressedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    if (uri == null) viewModel.cancelCompressPdf()
                    else viewModel.compressSelectedPdf(uri)
                }
                val compressPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForCompression(uri)
                    }
                }
                val createProtectedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    val password = pendingProtectPassword
                    pendingProtectPassword = null
                    if (uri == null || password == null) {
                        password?.fill('\u0000')
                        viewModel.cancelProtectPdf()
                    } else {
                        viewModel.protectSelectedPdf(uri, password)
                    }
                }
                val protectPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForProtection(uri)
                    }
                }
                val createPasswordRemovedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    val password = pendingRemovalPassword
                    pendingRemovalPassword = null
                    if (uri == null || password == null) {
                        password?.fill('\u0000')
                        viewModel.cancelRemovePassword()
                    } else {
                        viewModel.removeSelectedPdfPassword(uri, password)
                    }
                }
                val removePasswordPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForPasswordRemoval(uri)
                    }
                }
                val createPasswordChangedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    val passwords = pendingPasswordChange
                    pendingPasswordChange = null
                    if (uri == null || passwords == null) {
                        passwords?.first?.fill('\u0000')
                        passwords?.second?.fill('\u0000')
                        viewModel.cancelChangePassword()
                    } else {
                        viewModel.changeSelectedPdfPassword(uri, passwords.first, passwords.second)
                    }
                }
                val changePasswordPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForPasswordChange(uri)
                    }
                }
                val createScannedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    if (uri != null) viewModel.createScannerPdf(uri)
                }
                val cameraPermission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) viewModel.startScannerCapture()
                    else viewModel.scannerPermissionDenied()
                }

                QuietPdfApp(
                    state = viewModel.state,
                    onOpenPdf = { picker.launch(arrayOf("application/pdf")) },
                    renderPage = viewModel::renderPage,
                    onPageChanged = viewModel::rememberPage,
                    searchDocument = viewModel::search,
                    onToggleBookmark = viewModel::toggleBookmark,
                    loadTableOfContents = viewModel::loadTableOfContents,
                    inspectHealth = viewModel::inspectHealth,
                    imagesToPdfState = viewModel.imagesToPdfState,
                    onImagesToPdf = { imagePicker.launch(arrayOf("image/*")) },
                    onDismissImagesToPdfFailure = viewModel::clearImagesToPdfFailure,
                    onConfirmImagesPdfLayout = { layout ->
                        viewModel.configureImagesPdfLayout(layout)
                        createImagesPdf.launch("QuietPDF-images.pdf")
                    },
                    onCancelImagesPdfLayout = viewModel::discardSelectedImages,
                    mergePdfState = viewModel.mergePdfState,
                    onMergePdfs = { mergePdfPicker.launch(arrayOf("application/pdf")) },
                    onMoveMergeDocument = viewModel::moveMergeDocument,
                    onRemoveMergeDocument = viewModel::removeMergeDocument,
                    onConfirmMerge = { createMergedPdf.launch("QuietPDF-merged.pdf") },
                    onCancelMerge = viewModel::cancelMergePdf,
                    onDismissMergeFailure = viewModel::clearMergePdfFailure,
                    splitPdfState = viewModel.splitPdfState,
                    onSplitPdf = { splitPdfPicker.launch(arrayOf("application/pdf")) },
                    onConfirmSplit = { ranges ->
                        viewModel.configureSplitPdf(ranges)
                        splitOutputFolder.launch(null)
                    },
                    onCancelSplit = viewModel::cancelSplitPdf,
                    onDismissSplitResult = viewModel::clearSplitPdfResult,
                    extractPagesState = viewModel.extractPagesState,
                    onExtractPages = {
                        extractPagesPicker.launch(arrayOf("application/pdf"))
                    },
                    onConfirmPageExtraction = { selectedPages ->
                        viewModel.configurePageExtraction(selectedPages)
                        createExtractedPdf.launch("QuietPDF-extracted-pages.pdf")
                    },
                    onCancelPageExtraction = viewModel::cancelExtractPages,
                    onDismissPageExtractionFailure = viewModel::clearExtractPagesFailure,
                    deletePagesState = viewModel.deletePagesState,
                    onDeletePages = { deletePagesPicker.launch(arrayOf("application/pdf")) },
                    onConfirmPageDeletion = { deletedPages ->
                        viewModel.configurePageDeletion(deletedPages)
                        createDeletedPagesPdf.launch("QuietPDF-pages-removed.pdf")
                    },
                    onCancelPageDeletion = viewModel::cancelDeletePages,
                    onDismissPageDeletionFailure = viewModel::clearDeletePagesFailure,
                    rearrangePagesState = viewModel.rearrangePagesState,
                    onRearrangePages = {
                        rearrangePagesPicker.launch(arrayOf("application/pdf"))
                    },
                    onMoveRearrangedPage = viewModel::moveRearrangedPage,
                    onResetRearrangedPages = viewModel::resetRearrangedPageOrder,
                    onConfirmPageRearrangement = {
                        createRearrangedPdf.launch("QuietPDF-rearranged-pages.pdf")
                    },
                    onCancelPageRearrangement = viewModel::cancelRearrangePages,
                    onDismissPageRearrangementFailure = viewModel::clearRearrangePagesFailure,
                    rotatePagesState = viewModel.rotatePagesState,
                    onRotatePages = { rotatePagesPicker.launch(arrayOf("application/pdf")) },
                    onConfirmPageRotation = { selectedPages, rotation ->
                        viewModel.configurePageRotation(selectedPages, rotation)
                        createRotatedPagesPdf.launch("QuietPDF-rotated-pages.pdf")
                    },
                    onCancelPageRotation = viewModel::cancelRotatePages,
                    onDismissPageRotationFailure = viewModel::clearRotatePagesFailure,
                    duplicatePagesState = viewModel.duplicatePagesState,
                    onDuplicatePages = { duplicatePagesPicker.launch(arrayOf("application/pdf")) },
                    onConfirmPageDuplication = { selectedPages ->
                        viewModel.configurePageDuplication(selectedPages)
                        createDuplicatedPagesPdf.launch("QuietPDF-duplicated-pages.pdf")
                    },
                    onCancelPageDuplication = viewModel::cancelDuplicatePages,
                    onDismissPageDuplicationFailure = viewModel::clearDuplicatePagesFailure,
                    compressPdfState = viewModel.compressPdfState,
                    onCompressPdf = { compressPdfPicker.launch(arrayOf("application/pdf")) },
                    onConfirmPdfCompression = { mode ->
                        viewModel.configurePdfCompression(mode)
                        createCompressedPdf.launch("QuietPDF-compressed.pdf")
                    },
                    onCancelPdfCompression = viewModel::cancelCompressPdf,
                    onOpenCompressedPdf = viewModel::openCompressedPdf,
                    onDismissPdfCompressionResult = viewModel::clearCompressPdfResult,
                    protectPdfState = viewModel.protectPdfState,
                    onProtectPdf = { protectPdfPicker.launch(arrayOf("application/pdf")) },
                    onConfirmPdfProtection = { password ->
                        pendingProtectPassword?.fill('\u0000')
                        pendingProtectPassword = password.copyOf()
                        password.fill('\u0000')
                        createProtectedPdf.launch("QuietPDF-protected.pdf")
                    },
                    onCancelPdfProtection = {
                        pendingProtectPassword?.fill('\u0000')
                        pendingProtectPassword = null
                        viewModel.cancelProtectPdf()
                    },
                    onDismissPdfProtectionResult = viewModel::clearProtectPdfResult,
                    removePasswordState = viewModel.removePasswordState,
                    onRemovePassword = {
                        removePasswordPicker.launch(arrayOf("application/pdf"))
                    },
                    onConfirmPasswordRemoval = { password ->
                        pendingRemovalPassword?.fill('\u0000')
                        pendingRemovalPassword = password.copyOf()
                        password.fill('\u0000')
                        createPasswordRemovedPdf.launch("QuietPDF-unlocked.pdf")
                    },
                    onCancelPasswordRemoval = {
                        pendingRemovalPassword?.fill('\u0000')
                        pendingRemovalPassword = null
                        viewModel.cancelRemovePassword()
                    },
                    onOpenPasswordRemovedPdf = viewModel::openPasswordRemovedPdf,
                    onDismissPasswordRemovalResult = viewModel::clearRemovePasswordResult,
                    changePasswordState = viewModel.changePasswordState,
                    onChangePassword = {
                        changePasswordPicker.launch(arrayOf("application/pdf"))
                    },
                    onConfirmPasswordChange = { currentPassword, newPassword ->
                        pendingPasswordChange?.first?.fill('\u0000')
                        pendingPasswordChange?.second?.fill('\u0000')
                        pendingPasswordChange = currentPassword.copyOf() to newPassword.copyOf()
                        currentPassword.fill('\u0000')
                        newPassword.fill('\u0000')
                        createPasswordChangedPdf.launch("QuietPDF-password-changed.pdf")
                    },
                    onCancelPasswordChange = {
                        pendingPasswordChange?.first?.fill('\u0000')
                        pendingPasswordChange?.second?.fill('\u0000')
                        pendingPasswordChange = null
                        viewModel.cancelChangePassword()
                    },
                    onDismissPasswordChangeResult = viewModel::clearChangePasswordResult,
                    scannerCaptureState = viewModel.scannerCaptureState,
                    onScanDocument = {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.startScannerCapture()
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onBeginScannerCapture = viewModel::beginScannerCapture,
                    onScannerCaptureSaved = viewModel::scannerCaptureSaved,
                    onScannerCaptureFailed = viewModel::scannerCaptureFailed,
                    onScannerCameraUnavailable = viewModel::scannerCameraUnavailable,
                    onRetakeScannerCapture = viewModel::retakeScannerCapture,
                    onUpdateScannerCrop = viewModel::updateScannerCrop,
                    onResetScannerCrop = viewModel::resetScannerCrop,
                    onUpdateScannerEnhancement = viewModel::updateScannerEnhancement,
                    onAddScannerPage = viewModel::addScannerPage,
                    onSelectScannerPage = viewModel::selectScannerPage,
                    onMoveScannerPage = viewModel::moveScannerPage,
                    onDeleteScannerPage = viewModel::deleteScannerPage,
                    onSaveScannerPdf = { createScannedPdf.launch("QuietPDF-scan.pdf") },
                    onCancelScannerCapture = viewModel::cancelScannerCapture,
                    onOpenScannerPdf = viewModel::openScannerPdf,
                    onDismissScannerResult = viewModel::clearScannerResult,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntent(intent)
    }

    private fun openFromIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme != ContentResolverScheme) {
            viewModel.rejectUnsupportedUri()
            return
        }
        if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
            retainReadPermission(uri)
        }
        viewModel.open(uri)
    }

    private fun retainReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers grant temporary access only. Opening can still proceed safely.
        }
    }

    private fun retainDirectoryPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // The active picker grant may still be sufficient for this split operation.
        }
    }

    private companion object {
        const val ContentResolverScheme = "content"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietPdfApp(
    state: PdfOpenState,
    onOpenPdf: () -> Unit,
    renderPage: suspend (pageIndex: Int, targetWidth: Int) -> PageRenderResult,
    onPageChanged: (pageIndex: Int) -> Unit = {},
    searchDocument: suspend (query: String) -> PdfSearchResult = {
        PdfSearchResult.Failed
    },
    onToggleBookmark: (pageIndex: Int) -> Unit = {},
    loadTableOfContents: suspend () -> PdfTableOfContentsResult = {
        PdfTableOfContentsResult.Failed
    },
    inspectHealth: suspend () -> PdfHealthResult = { PdfHealthResult.Failed },
    imagesToPdfState: ImagesToPdfState = ImagesToPdfState.Idle,
    onImagesToPdf: () -> Unit = {},
    onDismissImagesToPdfFailure: () -> Unit = {},
    onConfirmImagesPdfLayout: (ImagePdfLayout) -> Unit = {},
    onCancelImagesPdfLayout: () -> Unit = {},
    mergePdfState: MergePdfState = MergePdfState.Idle,
    onMergePdfs: () -> Unit = {},
    onMoveMergeDocument: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onRemoveMergeDocument: (index: Int) -> Unit = {},
    onConfirmMerge: () -> Unit = {},
    onCancelMerge: () -> Unit = {},
    onDismissMergeFailure: () -> Unit = {},
    splitPdfState: SplitPdfState = SplitPdfState.Idle,
    onSplitPdf: () -> Unit = {},
    onConfirmSplit: (List<SplitPageRange>) -> Unit = {},
    onCancelSplit: () -> Unit = {},
    onDismissSplitResult: () -> Unit = {},
    extractPagesState: ExtractPagesState = ExtractPagesState.Idle,
    onExtractPages: () -> Unit = {},
    onConfirmPageExtraction: (IntArray) -> Unit = {},
    onCancelPageExtraction: () -> Unit = {},
    onDismissPageExtractionFailure: () -> Unit = {},
    deletePagesState: DeletePagesState = DeletePagesState.Idle,
    onDeletePages: () -> Unit = {},
    onConfirmPageDeletion: (IntArray) -> Unit = {},
    onCancelPageDeletion: () -> Unit = {},
    onDismissPageDeletionFailure: () -> Unit = {},
    rearrangePagesState: RearrangePagesState = RearrangePagesState.Idle,
    onRearrangePages: () -> Unit = {},
    onMoveRearrangedPage: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onResetRearrangedPages: () -> Unit = {},
    onConfirmPageRearrangement: () -> Unit = {},
    onCancelPageRearrangement: () -> Unit = {},
    onDismissPageRearrangementFailure: () -> Unit = {},
    rotatePagesState: RotatePagesState = RotatePagesState.Idle,
    onRotatePages: () -> Unit = {},
    onConfirmPageRotation: (IntArray, PageRotation) -> Unit = { _, _ -> },
    onCancelPageRotation: () -> Unit = {},
    onDismissPageRotationFailure: () -> Unit = {},
    duplicatePagesState: DuplicatePagesState = DuplicatePagesState.Idle,
    onDuplicatePages: () -> Unit = {},
    onConfirmPageDuplication: (IntArray) -> Unit = {},
    onCancelPageDuplication: () -> Unit = {},
    onDismissPageDuplicationFailure: () -> Unit = {},
    compressPdfState: CompressPdfState = CompressPdfState.Idle,
    onCompressPdf: () -> Unit = {},
    onConfirmPdfCompression: (PdfCompressionRequest) -> Unit = {},
    onCancelPdfCompression: () -> Unit = {},
    onOpenCompressedPdf: () -> Unit = {},
    onDismissPdfCompressionResult: () -> Unit = {},
    protectPdfState: ProtectPdfState = ProtectPdfState.Idle,
    onProtectPdf: () -> Unit = {},
    onConfirmPdfProtection: (CharArray) -> Unit = {},
    onCancelPdfProtection: () -> Unit = {},
    onDismissPdfProtectionResult: () -> Unit = {},
    removePasswordState: RemovePasswordState = RemovePasswordState.Idle,
    onRemovePassword: () -> Unit = {},
    onConfirmPasswordRemoval: (CharArray) -> Unit = {},
    onCancelPasswordRemoval: () -> Unit = {},
    onOpenPasswordRemovedPdf: () -> Unit = {},
    onDismissPasswordRemovalResult: () -> Unit = {},
    changePasswordState: ChangePasswordState = ChangePasswordState.Idle,
    onChangePassword: () -> Unit = {},
    onConfirmPasswordChange: (CharArray, CharArray) -> Unit = { _, _ -> },
    onCancelPasswordChange: () -> Unit = {},
    onDismissPasswordChangeResult: () -> Unit = {},
    scannerCaptureState: ScannerCaptureState = ScannerCaptureState.Idle,
    onScanDocument: () -> Unit = {},
    onBeginScannerCapture: () -> File? = { null },
    onScannerCaptureSaved: (File) -> Unit = {},
    onScannerCaptureFailed: (File?) -> Unit = {},
    onScannerCameraUnavailable: () -> Unit = {},
    onRetakeScannerCapture: () -> Unit = {},
    onUpdateScannerCrop: (ScannerCropSelection) -> Unit = {},
    onResetScannerCrop: () -> Unit = {},
    onUpdateScannerEnhancement: (ScannerEnhancementSettings) -> Unit = {},
    onAddScannerPage: () -> Unit = {},
    onSelectScannerPage: (Int) -> Unit = {},
    onMoveScannerPage: (Int, Int) -> Unit = { _, _ -> },
    onDeleteScannerPage: () -> Unit = {},
    onSaveScannerPdf: () -> Unit = {},
    onCancelScannerCapture: () -> Unit = {},
    onOpenScannerPdf: () -> Unit = {},
    onDismissScannerResult: () -> Unit = {},
) {
    if (state is PdfOpenState.Opened) {
        PdfReaderScreen(
            document = state,
            onOpenAnother = onOpenPdf,
            renderPage = renderPage,
            onPageChanged = onPageChanged,
            searchDocument = searchDocument,
            onToggleBookmark = onToggleBookmark,
            loadTableOfContents = loadTableOfContents,
            inspectHealth = inspectHealth,
        )
        return
    }
    if (scannerCaptureState is ScannerCaptureState.Camera ||
        scannerCaptureState is ScannerCaptureState.Capturing ||
        scannerCaptureState is ScannerCaptureState.PreparingPreview ||
        scannerCaptureState is ScannerCaptureState.Review ||
        scannerCaptureState is ScannerCaptureState.CreatingPdf
    ) {
        ScannerCaptureScreen(
            state = scannerCaptureState,
            onBeginCapture = onBeginScannerCapture,
            onCaptureSaved = onScannerCaptureSaved,
            onCaptureFailed = onScannerCaptureFailed,
            onCameraUnavailable = onScannerCameraUnavailable,
            onRetake = onRetakeScannerCapture,
            onUpdateCrop = onUpdateScannerCrop,
            onResetCrop = onResetScannerCrop,
            onUpdateEnhancement = onUpdateScannerEnhancement,
            onAddPage = onAddScannerPage,
            onSelectPage = onSelectScannerPage,
            onMovePage = onMoveScannerPage,
            onDeletePage = onDeleteScannerPage,
            onSavePdf = onSaveScannerPdf,
            onCancel = onCancelScannerCapture,
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 2.dp,
            ) {
                OpenPdfContent(
                    state = state,
                    onOpenPdf = onOpenPdf,
                    imagesToPdfState = imagesToPdfState,
                    onImagesToPdf = onImagesToPdf,
                    onDismissImagesToPdfFailure = onDismissImagesToPdfFailure,
                    mergePdfState = mergePdfState,
                    onMergePdfs = onMergePdfs,
                    onDismissMergeFailure = onDismissMergeFailure,
                    onCancelMerge = onCancelMerge,
                    splitPdfState = splitPdfState,
                    onSplitPdf = onSplitPdf,
                    onCancelSplit = onCancelSplit,
                    onDismissSplitResult = onDismissSplitResult,
                    extractPagesState = extractPagesState,
                    onExtractPages = onExtractPages,
                    onCancelPageExtraction = onCancelPageExtraction,
                    onDismissPageExtractionFailure = onDismissPageExtractionFailure,
                    deletePagesState = deletePagesState,
                    onDeletePages = onDeletePages,
                    onCancelPageDeletion = onCancelPageDeletion,
                    onDismissPageDeletionFailure = onDismissPageDeletionFailure,
                    rearrangePagesState = rearrangePagesState,
                    onRearrangePages = onRearrangePages,
                    onCancelPageRearrangement = onCancelPageRearrangement,
                    onDismissPageRearrangementFailure = onDismissPageRearrangementFailure,
                    rotatePagesState = rotatePagesState,
                    onRotatePages = onRotatePages,
                    onCancelPageRotation = onCancelPageRotation,
                    onDismissPageRotationFailure = onDismissPageRotationFailure,
                    duplicatePagesState = duplicatePagesState,
                    onDuplicatePages = onDuplicatePages,
                    onCancelPageDuplication = onCancelPageDuplication,
                    onDismissPageDuplicationFailure = onDismissPageDuplicationFailure,
                    compressPdfState = compressPdfState,
                    onCompressPdf = onCompressPdf,
                    onCancelPdfCompression = onCancelPdfCompression,
                    onOpenCompressedPdf = onOpenCompressedPdf,
                    onDismissPdfCompressionResult = onDismissPdfCompressionResult,
                    protectPdfState = protectPdfState,
                    onProtectPdf = onProtectPdf,
                    onCancelPdfProtection = onCancelPdfProtection,
                    onDismissPdfProtectionResult = onDismissPdfProtectionResult,
                    removePasswordState = removePasswordState,
                    onRemovePassword = onRemovePassword,
                    onCancelPasswordRemoval = onCancelPasswordRemoval,
                    onOpenPasswordRemovedPdf = onOpenPasswordRemovedPdf,
                    onDismissPasswordRemovalResult = onDismissPasswordRemovalResult,
                    changePasswordState = changePasswordState,
                    onChangePassword = onChangePassword,
                    onCancelPasswordChange = onCancelPasswordChange,
                    onDismissPasswordChangeResult = onDismissPasswordChangeResult,
                    scannerCaptureState = scannerCaptureState,
                    onScanDocument = onScanDocument,
                    onOpenScannerPdf = onOpenScannerPdf,
                    onDismissScannerResult = onDismissScannerResult,
                    contentPadding = PaddingValues(24.dp),
                )
            }
        }
    }
    if (imagesToPdfState is ImagesToPdfState.Configuring) {
        ImagesPdfLayoutDialog(
            imageCount = imagesToPdfState.imageCount,
            onConfirm = onConfirmImagesPdfLayout,
            onCancel = onCancelImagesPdfLayout,
        )
    }
    if (mergePdfState is MergePdfState.Configuring) {
        MergePdfOrderDialog(
            documents = mergePdfState.documents,
            onMove = onMoveMergeDocument,
            onRemove = onRemoveMergeDocument,
            onConfirm = onConfirmMerge,
            onCancel = onCancelMerge,
        )
    }
    if (splitPdfState is SplitPdfState.Configuring) {
        SplitPdfDialog(
            documentName = splitPdfState.displayName,
            pageCount = splitPdfState.pageCount,
            onConfirm = onConfirmSplit,
            onCancel = onCancelSplit,
        )
    }
    if (extractPagesState is ExtractPagesState.Configuring) {
        ExtractPagesDialog(
            documentName = extractPagesState.displayName,
            pageCount = extractPagesState.pageCount,
            onConfirm = onConfirmPageExtraction,
            onCancel = onCancelPageExtraction,
        )
    }
    if (deletePagesState is DeletePagesState.Configuring) {
        DeletePagesDialog(
            documentName = deletePagesState.displayName,
            pageCount = deletePagesState.pageCount,
            onConfirm = onConfirmPageDeletion,
            onCancel = onCancelPageDeletion,
        )
    }
    if (rearrangePagesState is RearrangePagesState.Configuring) {
        RearrangePagesDialog(
            documentName = rearrangePagesState.displayName,
            pageOrder = rearrangePagesState.pageOrder,
            onMove = onMoveRearrangedPage,
            onReset = onResetRearrangedPages,
            onConfirm = onConfirmPageRearrangement,
            onCancel = onCancelPageRearrangement,
        )
    }
    if (rotatePagesState is RotatePagesState.Configuring) {
        RotatePagesDialog(
            documentName = rotatePagesState.displayName,
            pageCount = rotatePagesState.pageCount,
            onConfirm = onConfirmPageRotation,
            onCancel = onCancelPageRotation,
        )
    }
    if (duplicatePagesState is DuplicatePagesState.Configuring) {
        DuplicatePagesDialog(
            documentName = duplicatePagesState.displayName,
            pageCount = duplicatePagesState.pageCount,
            onConfirm = onConfirmPageDuplication,
            onCancel = onCancelPageDuplication,
        )
    }
    if (compressPdfState is CompressPdfState.Configuring) {
        CompressPdfDialog(
            documentName = compressPdfState.displayName,
            analysis = compressPdfState.analysis,
            onConfirm = onConfirmPdfCompression,
            onCancel = onCancelPdfCompression,
        )
    }
    if (protectPdfState is ProtectPdfState.Configuring) {
        ProtectPdfDialog(
            documentName = protectPdfState.displayName,
            pageCount = protectPdfState.pageCount,
            onConfirm = onConfirmPdfProtection,
            onCancel = onCancelPdfProtection,
        )
    }
    if (removePasswordState is RemovePasswordState.Configuring) {
        RemovePasswordDialog(
            documentName = removePasswordState.displayName,
            passwordError = removePasswordState.passwordError,
            onConfirm = onConfirmPasswordRemoval,
            onCancel = onCancelPasswordRemoval,
        )
    }
    if (changePasswordState is ChangePasswordState.Configuring) {
        ChangePasswordDialog(
            documentName = changePasswordState.displayName,
            currentPasswordError = changePasswordState.currentPasswordError,
            onConfirm = onConfirmPasswordChange,
            onCancel = onCancelPasswordChange,
        )
    }
}

@Composable
private fun CompressPdfDialog(
    documentName: String,
    analysis: com.rameshta.quietpdf.pdf.CompressPdfAnalysis,
    onConfirm: (PdfCompressionRequest) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedMode by remember(documentName, analysis) { mutableStateOf(1) }
    var targetMegabytes by remember(documentName, analysis) { mutableStateOf("") }
    val context = LocalContext.current
    val qualityMode = PdfCompressionMode.entries.getOrNull(selectedMode)
    val targetSize = TargetFileSize.parseMegabytes(targetMegabytes, analysis.originalSizeBytes)
    val request = when {
        qualityMode != null -> PdfCompressionRequest.Quality(qualityMode)
        targetSize != null -> PdfCompressionRequest.TargetSize(targetSize)
        else -> null
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.compress_pdf_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.compress_pdf_document_summary, documentName, analysis.pageCount))
                Text(
                    text = stringResource(
                        R.string.compress_pdf_original_size,
                        android.text.format.Formatter.formatShortFileSize(context, analysis.originalSizeBytes),
                    ),
                    modifier = Modifier.padding(top = 12.dp),
                )
                LayoutChoiceRow(
                    label = stringResource(R.string.compression_quality),
                    options = listOf(
                        stringResource(R.string.compression_high_quality),
                        stringResource(R.string.compression_balanced),
                        stringResource(R.string.compression_maximum),
                        stringResource(R.string.compression_target_size),
                    ),
                    selectedIndex = selectedMode,
                    tagPrefix = "compression_mode",
                    onSelect = { selectedMode = it },
                )
                if (qualityMode != null) {
                    val estimatedSize = analysis.estimatedOutputSize(qualityMode)
                    Text(
                        text = stringResource(
                            R.string.compress_pdf_estimated_size,
                            android.text.format.Formatter.formatShortFileSize(context, estimatedSize),
                            percentageSaved(analysis.originalSizeBytes, estimatedSize),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    OutlinedTextField(
                        value = targetMegabytes,
                        onValueChange = { value ->
                            if (value.length <= 10 && value.count { it == '.' } <= 1 &&
                                value.all { it.isDigit() || it == '.' }
                            ) targetMegabytes = value
                        },
                        label = { Text(stringResource(R.string.target_file_size_megabytes)) },
                        supportingText = {
                            Text(
                                if (targetMegabytes.isBlank() || targetSize != null) {
                                    stringResource(R.string.target_file_size_help)
                                } else {
                                    stringResource(R.string.compress_pdf_invalid_target)
                                },
                            )
                        },
                        isError = targetMegabytes.isNotBlank() && targetSize == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("target_file_size_input"),
                    )
                    Text(
                        text = stringResource(R.string.target_file_size_best_effort),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = if (analysis.compressibleImages.isEmpty()) {
                        stringResource(R.string.compress_pdf_no_eligible_images)
                    } else {
                        stringResource(R.string.compress_pdf_tradeoff)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { request?.let(onConfirm) },
                enabled = analysis.compressibleImages.isNotEmpty() && request != null,
                modifier = Modifier.testTag("compress_pdf_confirm"),
            ) {
                Text(stringResource(R.string.save_compressed_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("compress_pdf_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("compress_pdf_dialog"),
    )
}

private fun percentageSaved(originalSize: Long, outputSize: Long): Int {
    if (originalSize <= 0L || outputSize >= originalSize) return 0
    return (((originalSize - outputSize) * 100.0) / originalSize).toInt().coerceIn(0, 100)
}

@Composable
private fun ProtectPdfDialog(
    documentName: String,
    pageCount: Int,
    onConfirm: (CharArray) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember(documentName, pageCount) { mutableStateOf("") }
    var confirmation by remember(documentName, pageCount) { mutableStateOf("") }
    val passwordIsValid = ProtectPdfPassword.isValid(password)
    val passwordsMatch = password == confirmation
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.protect_pdf_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.protect_pdf_document_summary, documentName, pageCount))
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= ProtectPdfPassword.MAX_LENGTH) password = it },
                    label = { Text(stringResource(R.string.protect_pdf_password)) },
                    supportingText = {
                        Text(stringResource(R.string.protect_pdf_password_help, ProtectPdfPassword.MIN_LENGTH))
                    },
                    isError = password.isNotEmpty() && !passwordIsValid,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("protect_pdf_password"),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { if (it.length <= ProtectPdfPassword.MAX_LENGTH) confirmation = it },
                    label = { Text(stringResource(R.string.protect_pdf_confirm_password)) },
                    supportingText = {
                        if (confirmation.isNotEmpty() && !passwordsMatch) {
                            Text(stringResource(R.string.protect_pdf_password_mismatch))
                        }
                    },
                    isError = confirmation.isNotEmpty() && !passwordsMatch,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .testTag("protect_pdf_confirm_password"),
                )
                Text(
                    text = stringResource(R.string.protect_pdf_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val passwordChars = password.toCharArray()
                    password = ""
                    confirmation = ""
                    onConfirm(passwordChars)
                },
                enabled = passwordIsValid && passwordsMatch,
                modifier = Modifier.testTag("protect_pdf_confirm"),
            ) {
                Text(stringResource(R.string.save_protected_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("protect_pdf_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("protect_pdf_dialog"),
    )
}

@Composable
private fun RemovePasswordDialog(
    documentName: String,
    passwordError: Boolean,
    onConfirm: (CharArray) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember(documentName, passwordError) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.remove_password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.remove_password_document_summary, documentName))
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= 127) password = it },
                    label = { Text(stringResource(R.string.current_password)) },
                    supportingText = {
                        Text(
                            if (passwordError) stringResource(R.string.remove_password_incorrect)
                            else stringResource(R.string.remove_password_help),
                        )
                    },
                    isError = passwordError,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .testTag("remove_password_input"),
                )
                Text(
                    text = stringResource(R.string.remove_password_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val passwordChars = password.toCharArray()
                    password = ""
                    onConfirm(passwordChars)
                },
                modifier = Modifier.testTag("remove_password_confirm"),
            ) {
                Text(stringResource(R.string.save_unlocked_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("remove_password_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("remove_password_dialog"),
    )
}

@Composable
private fun ChangePasswordDialog(
    documentName: String,
    currentPasswordError: Boolean,
    onConfirm: (CharArray, CharArray) -> Unit,
    onCancel: () -> Unit,
) {
    var currentPassword by remember(documentName, currentPasswordError) { mutableStateOf("") }
    var newPassword by remember(documentName, currentPasswordError) { mutableStateOf("") }
    var confirmation by remember(documentName, currentPasswordError) { mutableStateOf("") }
    val newPasswordIsValid = ProtectPdfPassword.isValid(newPassword)
    val passwordsMatch = newPassword == confirmation
    val passwordChanged = currentPassword != newPassword
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.change_password_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.change_password_document_summary, documentName))
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { if (it.length <= 127) currentPassword = it },
                    label = { Text(stringResource(R.string.current_password)) },
                    supportingText = {
                        if (currentPasswordError) Text(stringResource(R.string.change_password_incorrect))
                    },
                    isError = currentPasswordError,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .testTag("change_password_current"),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { if (it.length <= ProtectPdfPassword.MAX_LENGTH) newPassword = it },
                    label = { Text(stringResource(R.string.new_password)) },
                    supportingText = {
                        Text(
                            when {
                                newPassword.isNotEmpty() && !newPasswordIsValid -> {
                                    stringResource(R.string.protect_pdf_invalid_password)
                                }
                                newPassword.isNotEmpty() && !passwordChanged -> {
                                    stringResource(R.string.change_password_must_differ)
                                }
                                else -> stringResource(
                                    R.string.protect_pdf_password_help,
                                    ProtectPdfPassword.MIN_LENGTH,
                                )
                            },
                        )
                    },
                    isError = newPassword.isNotEmpty() && (!newPasswordIsValid || !passwordChanged),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .testTag("change_password_new"),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { if (it.length <= ProtectPdfPassword.MAX_LENGTH) confirmation = it },
                    label = { Text(stringResource(R.string.protect_pdf_confirm_password)) },
                    supportingText = {
                        if (confirmation.isNotEmpty() && !passwordsMatch) {
                            Text(stringResource(R.string.protect_pdf_password_mismatch))
                        }
                    },
                    isError = confirmation.isNotEmpty() && !passwordsMatch,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .testTag("change_password_confirm_new"),
                )
                Text(
                    text = stringResource(R.string.change_password_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val currentChars = currentPassword.toCharArray()
                    val newChars = newPassword.toCharArray()
                    currentPassword = ""
                    newPassword = ""
                    confirmation = ""
                    onConfirm(currentChars, newChars)
                },
                enabled = newPasswordIsValid && passwordsMatch && passwordChanged,
                modifier = Modifier.testTag("change_password_confirm"),
            ) {
                Text(stringResource(R.string.save_changed_password_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("change_password_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("change_password_dialog"),
    )
}

@Composable
private fun DuplicatePagesDialog(
    documentName: String,
    pageCount: Int,
    onConfirm: (IntArray) -> Unit,
    onCancel: () -> Unit,
) {
    var pageSelection by remember(documentName, pageCount) { mutableStateOf("") }
    val selectedPages = ExtractPageSelectionParser.parse(pageSelection, pageCount)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.duplicate_pages_title)) },
        text = {
            Column {
                Text(stringResource(R.string.duplicate_pages_document_summary, documentName, pageCount))
                OutlinedTextField(
                    value = pageSelection,
                    onValueChange = { pageSelection = it },
                    label = { Text(stringResource(R.string.duplicate_pages_selection_label)) },
                    supportingText = {
                        Text(stringResource(R.string.duplicate_pages_selection_help, pageCount))
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("duplicate_pages_input"),
                )
                if (selectedPages != null) {
                    Text(
                        text = stringResource(
                            R.string.duplicate_pages_selection_count,
                            selectedPages.size,
                            pageCount + selectedPages.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedPages?.let(onConfirm) },
                enabled = selectedPages != null,
                modifier = Modifier.testTag("duplicate_pages_confirm"),
            ) { Text(stringResource(R.string.save_duplicated_pdf)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("duplicate_pages_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("duplicate_pages_dialog"),
    )
}

@Composable
private fun RotatePagesDialog(
    documentName: String,
    pageCount: Int,
    onConfirm: (IntArray, PageRotation) -> Unit,
    onCancel: () -> Unit,
) {
    var pageSelection by remember(documentName, pageCount) { mutableStateOf("") }
    var rotation by remember(documentName, pageCount) { mutableStateOf(PageRotation.Clockwise90) }
    val selectedPages = ExtractPageSelectionParser.parse(pageSelection, pageCount)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.rotate_pages_title)) },
        text = {
            Column {
                Text(stringResource(R.string.rotate_pages_document_summary, documentName, pageCount))
                OutlinedTextField(
                    value = pageSelection,
                    onValueChange = { pageSelection = it },
                    label = { Text(stringResource(R.string.rotate_pages_selection_label)) },
                    supportingText = {
                        Text(stringResource(R.string.rotate_pages_selection_help, pageCount))
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("rotate_pages_input"),
                )
                if (selectedPages != null) {
                    Text(
                        text = stringResource(
                            R.string.rotate_pages_selection_count,
                            selectedPages.size,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp).testTag("rotate_pages_count"),
                    )
                } else if (pageSelection.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.rotate_pages_invalid_selection),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp).testTag("rotate_pages_selection_error"),
                    )
                }
                LayoutChoiceRow(
                    label = stringResource(R.string.rotate_pages_direction),
                    options = listOf(
                        stringResource(R.string.rotate_clockwise_90),
                        stringResource(R.string.rotate_180),
                        stringResource(R.string.rotate_counterclockwise_90),
                    ),
                    selectedIndex = rotation.ordinal,
                    tagPrefix = "rotate_direction",
                    onSelect = { rotation = PageRotation.entries[it] },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedPages?.let { onConfirm(it, rotation) } },
                enabled = selectedPages != null,
                modifier = Modifier.testTag("rotate_pages_confirm"),
            ) { Text(stringResource(R.string.save_rotated_pdf)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("rotate_pages_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("rotate_pages_dialog"),
    )
}

@Composable
private fun RearrangePagesDialog(
    documentName: String,
    pageOrder: List<Int>,
    onMove: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.rearrange_pages_title)) },
        text = {
            Column {
                Text(stringResource(R.string.rearrange_pages_document_summary, documentName))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = 12.dp)
                        .testTag("rearrange_pages_list"),
                ) {
                    itemsIndexed(pageOrder, key = { _, pageIndex -> pageIndex }) { position, pageIndex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.rearrange_pages_position,
                                    position + 1,
                                    pageIndex + 1,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { onMove(position, position - 1) },
                                enabled = position > 0,
                                modifier = Modifier.testTag("rearrange_page_${pageIndex}_up"),
                            ) { Text(stringResource(R.string.move_up)) }
                            TextButton(
                                onClick = { onMove(position, position + 1) },
                                enabled = position < pageOrder.lastIndex,
                                modifier = Modifier.testTag("rearrange_page_${pageIndex}_down"),
                            ) { Text(stringResource(R.string.move_down)) }
                        }
                    }
                }
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("rearrange_pages_reset"),
                ) { Text(stringResource(R.string.reset_order)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("rearrange_pages_confirm"),
            ) { Text(stringResource(R.string.save_rearranged_pdf)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("rearrange_pages_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("rearrange_pages_dialog"),
    )
}

@Composable
private fun DeletePagesDialog(
    documentName: String,
    pageCount: Int,
    onConfirm: (IntArray) -> Unit,
    onCancel: () -> Unit,
) {
    var pageSelection by remember(documentName, pageCount) { mutableStateOf("") }
    val deletedPages = ExtractPageSelectionParser.parse(pageSelection, pageCount)
    val keptPages = deletedPages?.let {
        DeletePageSelectionPlanner.keptPageIndices(pageCount, it)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.delete_pages_title)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_pages_document_summary, documentName, pageCount))
                OutlinedTextField(
                    value = pageSelection,
                    onValueChange = { pageSelection = it },
                    label = { Text(stringResource(R.string.delete_pages_selection_label)) },
                    supportingText = {
                        Text(stringResource(R.string.delete_pages_selection_help, pageCount))
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("delete_pages_input"),
                )
                when {
                    deletedPages != null && keptPages != null -> Text(
                        text = stringResource(
                            R.string.delete_pages_selection_count,
                            deletedPages.size,
                            keptPages.size,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("delete_pages_count"),
                    )
                    pageSelection.isNotBlank() -> Text(
                        text = stringResource(R.string.delete_pages_invalid_selection),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("delete_pages_selection_error"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (keptPages != null) onConfirm(deletedPages) },
                enabled = keptPages != null,
                modifier = Modifier.testTag("delete_pages_confirm"),
            ) { Text(stringResource(R.string.save_without_pages)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("delete_pages_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("delete_pages_dialog"),
    )
}

@Composable
private fun ExtractPagesDialog(
    documentName: String,
    pageCount: Int,
    onConfirm: (IntArray) -> Unit,
    onCancel: () -> Unit,
) {
    var pageSelection by remember(documentName, pageCount) { mutableStateOf("") }
    val selectedPages = ExtractPageSelectionParser.parse(pageSelection, pageCount)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.extract_pages_title)) },
        text = {
            Column {
                Text(stringResource(R.string.extract_pages_document_summary, documentName, pageCount))
                OutlinedTextField(
                    value = pageSelection,
                    onValueChange = { pageSelection = it },
                    label = { Text(stringResource(R.string.extract_pages_selection_label)) },
                    supportingText = {
                        Text(stringResource(R.string.extract_pages_selection_help, pageCount))
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("extract_pages_input"),
                )
                when {
                    selectedPages != null -> Text(
                        text = stringResource(
                            R.string.extract_pages_selection_count,
                            selectedPages.size,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("extract_pages_count"),
                    )
                    pageSelection.isNotBlank() -> Text(
                        text = stringResource(R.string.extract_pages_invalid_selection),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("extract_pages_selection_error"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedPages?.let(onConfirm) },
                enabled = selectedPages != null,
                modifier = Modifier.testTag("extract_pages_confirm"),
            ) { Text(stringResource(R.string.save_extracted_pdf)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("extract_pages_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("extract_pages_dialog"),
    )
}

@Composable
private fun SplitPdfDialog(
    documentName: String,
    pageCount: Int,
    onConfirm: (List<SplitPageRange>) -> Unit,
    onCancel: () -> Unit,
) {
    var mode by remember(documentName, pageCount) { mutableStateOf(SplitPdfMode.EachPage) }
    var customRanges by remember(documentName, pageCount) { mutableStateOf("") }
    var everyPages by remember(documentName, pageCount) { mutableStateOf("") }
    val plan = SplitPdfPlanner.createPlan(mode, pageCount, customRanges, everyPages)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.split_pdf_options_title)) },
        text = {
            Column {
                Text(stringResource(R.string.split_pdf_document_summary, documentName, pageCount))
                LayoutChoiceRow(
                    label = stringResource(R.string.split_pdf_mode),
                    options = listOf(
                        stringResource(R.string.split_pdf_each_page),
                        stringResource(R.string.split_pdf_custom_ranges),
                        stringResource(R.string.split_pdf_every_pages),
                    ),
                    selectedIndex = mode.ordinal,
                    tagPrefix = "split_mode",
                    onSelect = { mode = SplitPdfMode.entries[it] },
                )
                when (mode) {
                    SplitPdfMode.EachPage -> {
                        Text(stringResource(R.string.split_pdf_each_page_description))
                    }
                    SplitPdfMode.CustomRanges -> {
                        OutlinedTextField(
                            value = customRanges,
                            onValueChange = { customRanges = it },
                            label = { Text(stringResource(R.string.split_pdf_ranges_label)) },
                            supportingText = {
                                Text(stringResource(R.string.split_pdf_ranges_help, pageCount))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("split_ranges_input"),
                        )
                    }
                    SplitPdfMode.EveryPages -> {
                        OutlinedTextField(
                            value = everyPages,
                            onValueChange = { everyPages = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.split_pdf_every_label)) },
                            supportingText = {
                                Text(stringResource(R.string.split_pdf_every_help, pageCount - 1))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("split_every_input"),
                        )
                    }
                }
                if (plan != null) {
                    Text(
                        text = stringResource(R.string.split_pdf_output_count, plan.size),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp).testTag("split_output_count"),
                    )
                } else if (mode != SplitPdfMode.EachPage) {
                    Text(
                        text = stringResource(R.string.split_pdf_invalid_plan),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp).testTag("split_plan_error"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { plan?.let(onConfirm) },
                enabled = plan != null,
                modifier = Modifier.testTag("split_confirm"),
            ) { Text(stringResource(R.string.choose_output_folder)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("split_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("split_pdf_dialog"),
    )
}

@Composable
private fun MergePdfOrderDialog(
    documents: List<MergePdfItem>,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.merge_pdf_order_title)) },
        text = {
            Column {
                Text(stringResource(R.string.merge_pdf_order_description))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    itemsIndexed(documents, key = { _, item -> item.uri }) { index, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .testTag("merge_document_$index"),
                        ) {
                            Text(
                                text = "${index + 1}. ${item.displayName}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Row {
                                TextButton(
                                    onClick = { onMove(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.testTag("merge_move_up_$index"),
                                ) { Text(stringResource(R.string.move_up)) }
                                TextButton(
                                    onClick = { onMove(index, index + 1) },
                                    enabled = index < documents.lastIndex,
                                    modifier = Modifier.testTag("merge_move_down_$index"),
                                ) { Text(stringResource(R.string.move_down)) }
                                TextButton(
                                    onClick = { onRemove(index) },
                                    modifier = Modifier.testTag("merge_remove_$index"),
                                ) { Text(stringResource(R.string.remove)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = documents.size >= 2,
                modifier = Modifier.testTag("merge_confirm"),
            ) {
                Text(stringResource(R.string.merge_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("merge_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("merge_order_dialog"),
    )
}

@Composable
private fun ImagesPdfLayoutDialog(
    imageCount: Int,
    onConfirm: (ImagePdfLayout) -> Unit,
    onCancel: () -> Unit,
) {
    var layout by remember(imageCount) { mutableStateOf(ImagePdfLayout()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.images_pdf_layout)) },
        text = {
            Column {
                Text(stringResource(R.string.images_pdf_layout_count, imageCount))
                LayoutChoiceRow(
                    label = stringResource(R.string.page_size),
                    options = listOf(stringResource(R.string.a4), stringResource(R.string.letter)),
                    selectedIndex = layout.pageSize.ordinal,
                    tagPrefix = "layout_page_size",
                    onSelect = { layout = layout.copy(pageSize = ImagePdfPageSize.entries[it]) },
                )
                LayoutChoiceRow(
                    label = stringResource(R.string.orientation),
                    options = listOf(
                        stringResource(R.string.automatic),
                        stringResource(R.string.portrait),
                        stringResource(R.string.landscape),
                    ),
                    selectedIndex = layout.orientation.ordinal,
                    tagPrefix = "layout_orientation",
                    onSelect = { layout = layout.copy(orientation = ImagePdfOrientation.entries[it]) },
                )
                LayoutChoiceRow(
                    label = stringResource(R.string.image_scaling),
                    options = listOf(stringResource(R.string.fit), stringResource(R.string.fill)),
                    selectedIndex = layout.scaleMode.ordinal,
                    tagPrefix = "layout_scale",
                    onSelect = { layout = layout.copy(scaleMode = ImagePdfScaleMode.entries[it]) },
                )
                LayoutChoiceRow(
                    label = stringResource(R.string.margins),
                    options = listOf(
                        stringResource(R.string.none),
                        stringResource(R.string.standard),
                        stringResource(R.string.wide),
                    ),
                    selectedIndex = layout.margin.ordinal,
                    tagPrefix = "layout_margin",
                    onSelect = { layout = layout.copy(margin = ImagePdfMargin.entries[it]) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(layout) },
                modifier = Modifier.testTag("layout_confirm"),
            ) { Text(stringResource(R.string.continue_action)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("layout_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("images_pdf_layout_dialog"),
    )
}

@Composable
private fun LayoutChoiceRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    tagPrefix: String,
    onSelect: (Int) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            TextButton(
                onClick = { onSelect(index) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { selected = index == selectedIndex }
                    .testTag("${tagPrefix}_$index"),
            ) {
                Text(
                    text = option,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun OpenPdfContent(
    state: PdfOpenState,
    onOpenPdf: () -> Unit,
    imagesToPdfState: ImagesToPdfState,
    onImagesToPdf: () -> Unit,
    onDismissImagesToPdfFailure: () -> Unit,
    mergePdfState: MergePdfState,
    onMergePdfs: () -> Unit,
    onDismissMergeFailure: () -> Unit,
    onCancelMerge: () -> Unit,
    splitPdfState: SplitPdfState,
    onSplitPdf: () -> Unit,
    onCancelSplit: () -> Unit,
    onDismissSplitResult: () -> Unit,
    extractPagesState: ExtractPagesState,
    onExtractPages: () -> Unit,
    onCancelPageExtraction: () -> Unit,
    onDismissPageExtractionFailure: () -> Unit,
    deletePagesState: DeletePagesState,
    onDeletePages: () -> Unit,
    onCancelPageDeletion: () -> Unit,
    onDismissPageDeletionFailure: () -> Unit,
    rearrangePagesState: RearrangePagesState,
    onRearrangePages: () -> Unit,
    onCancelPageRearrangement: () -> Unit,
    onDismissPageRearrangementFailure: () -> Unit,
    rotatePagesState: RotatePagesState,
    onRotatePages: () -> Unit,
    onCancelPageRotation: () -> Unit,
    onDismissPageRotationFailure: () -> Unit,
    duplicatePagesState: DuplicatePagesState,
    onDuplicatePages: () -> Unit,
    onCancelPageDuplication: () -> Unit,
    onDismissPageDuplicationFailure: () -> Unit,
    compressPdfState: CompressPdfState,
    onCompressPdf: () -> Unit,
    onCancelPdfCompression: () -> Unit,
    onOpenCompressedPdf: () -> Unit,
    onDismissPdfCompressionResult: () -> Unit,
    protectPdfState: ProtectPdfState,
    onProtectPdf: () -> Unit,
    onCancelPdfProtection: () -> Unit,
    onDismissPdfProtectionResult: () -> Unit,
    removePasswordState: RemovePasswordState,
    onRemovePassword: () -> Unit,
    onCancelPasswordRemoval: () -> Unit,
    onOpenPasswordRemovedPdf: () -> Unit,
    onDismissPasswordRemovalResult: () -> Unit,
    changePasswordState: ChangePasswordState,
    onChangePassword: () -> Unit,
    onCancelPasswordChange: () -> Unit,
    onDismissPasswordChangeResult: () -> Unit,
    scannerCaptureState: ScannerCaptureState,
    onScanDocument: () -> Unit,
    onOpenScannerPdf: () -> Unit,
    onDismissScannerResult: () -> Unit,
    contentPadding: PaddingValues,
) {
    val contentScroll = rememberScrollState()
    val hasResult = imagesToPdfState is ImagesToPdfState.Failed ||
        mergePdfState is MergePdfState.Failed ||
        splitPdfState is SplitPdfState.Failed || splitPdfState is SplitPdfState.Completed ||
        extractPagesState is ExtractPagesState.Failed ||
        deletePagesState is DeletePagesState.Failed ||
        rearrangePagesState is RearrangePagesState.Failed ||
        rotatePagesState is RotatePagesState.Failed ||
        duplicatePagesState is DuplicatePagesState.Failed ||
        compressPdfState is CompressPdfState.Failed || compressPdfState is CompressPdfState.Completed
        || protectPdfState is ProtectPdfState.Failed || protectPdfState is ProtectPdfState.Completed
        || removePasswordState is RemovePasswordState.Failed ||
        removePasswordState is RemovePasswordState.Completed
        || changePasswordState is ChangePasswordState.Failed ||
        changePasswordState is ChangePasswordState.Completed
        || scannerCaptureState is ScannerCaptureState.Failed ||
        scannerCaptureState is ScannerCaptureState.Completed
    LaunchedEffect(hasResult) {
        if (hasResult) {
            withFrameNanos { }
            contentScroll.scrollTo(contentScroll.maxValue)
        }
    }
    Column(
        modifier = Modifier
            .verticalScroll(contentScroll)
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (imagesToPdfState is ImagesToPdfState.Creating) {
            ImagesToPdfCreatingContent(imagesToPdfState.imageCount)
            return@Column
        }
        when (mergePdfState) {
            MergePdfState.Preparing -> {
                OperationProgressContent(R.string.merge_pdf_preparing)
                return@Column
            }
            is MergePdfState.Merging -> {
                OperationProgressContent(
                    messageResource = R.string.merge_pdf_merging,
                    argument = mergePdfState.documentCount,
                    onCancel = onCancelMerge,
                )
                return@Column
            }
            else -> Unit
        }
        when (splitPdfState) {
            SplitPdfState.Preparing -> {
                OperationProgressContent(R.string.split_pdf_preparing)
                return@Column
            }
            is SplitPdfState.Splitting -> {
                OperationProgressContent(
                    messageResource = R.string.split_pdf_splitting,
                    firstArgument = splitPdfState.completedOutputs,
                    secondArgument = splitPdfState.totalOutputs,
                    onCancel = onCancelSplit,
                )
                return@Column
            }
            else -> Unit
        }
        when (extractPagesState) {
            ExtractPagesState.Preparing -> {
                OperationProgressContent(R.string.extract_pages_preparing)
                return@Column
            }
            is ExtractPagesState.Extracting -> {
                OperationProgressContent(
                    messageResource = R.string.extract_pages_extracting,
                    argument = extractPagesState.selectedPageCount,
                    onCancel = onCancelPageExtraction,
                )
                return@Column
            }
            else -> Unit
        }
        when (deletePagesState) {
            DeletePagesState.Preparing -> {
                OperationProgressContent(R.string.delete_pages_preparing)
                return@Column
            }
            is DeletePagesState.Deleting -> {
                OperationProgressContent(
                    messageResource = R.string.delete_pages_deleting,
                    firstArgument = deletePagesState.deletedPageCount,
                    secondArgument = deletePagesState.remainingPageCount,
                    onCancel = onCancelPageDeletion,
                )
                return@Column
            }
            else -> Unit
        }
        when (rearrangePagesState) {
            RearrangePagesState.Preparing -> {
                OperationProgressContent(R.string.rearrange_pages_preparing)
                return@Column
            }
            is RearrangePagesState.Rearranging -> {
                OperationProgressContent(
                    messageResource = R.string.rearrange_pages_rearranging,
                    argument = rearrangePagesState.pageCount,
                    onCancel = onCancelPageRearrangement,
                )
                return@Column
            }
            else -> Unit
        }
        when (rotatePagesState) {
            RotatePagesState.Preparing -> {
                OperationProgressContent(R.string.rotate_pages_preparing)
                return@Column
            }
            is RotatePagesState.Rotating -> {
                OperationProgressContent(
                    messageResource = R.string.rotate_pages_rotating,
                    argument = rotatePagesState.selectedPageCount,
                    onCancel = onCancelPageRotation,
                )
                return@Column
            }
            else -> Unit
        }
        when (duplicatePagesState) {
            DuplicatePagesState.Preparing -> {
                OperationProgressContent(R.string.duplicate_pages_preparing)
                return@Column
            }
            is DuplicatePagesState.Duplicating -> {
                OperationProgressContent(
                    messageResource = R.string.duplicate_pages_duplicating,
                    firstArgument = duplicatePagesState.selectedPageCount,
                    secondArgument = duplicatePagesState.outputPageCount,
                    onCancel = onCancelPageDuplication,
                )
                return@Column
            }
            else -> Unit
        }
        when (compressPdfState) {
            CompressPdfState.Preparing -> {
                OperationProgressContent(R.string.compress_pdf_preparing)
                return@Column
            }
            is CompressPdfState.Compressing -> {
                OperationProgressContent(
                    messageResource = if (compressPdfState.totalAttempts > 1) {
                        R.string.target_file_size_compressing
                    } else {
                        R.string.compress_pdf_compressing
                    },
                    firstArgument = compressPdfState.completedPages,
                    secondArgument = compressPdfState.totalPages,
                    thirdArgument = compressPdfState.attempt.takeIf {
                        compressPdfState.totalAttempts > 1
                    },
                    fourthArgument = compressPdfState.totalAttempts.takeIf { it > 1 },
                    onCancel = onCancelPdfCompression,
                )
                return@Column
            }
            else -> Unit
        }
        when (protectPdfState) {
            ProtectPdfState.Preparing -> {
                OperationProgressContent(R.string.protect_pdf_preparing)
                return@Column
            }
            is ProtectPdfState.Protecting -> {
                OperationProgressContent(
                    messageResource = R.string.protect_pdf_protecting,
                    argument = protectPdfState.pageCount,
                    onCancel = onCancelPdfProtection,
                )
                return@Column
            }
            else -> Unit
        }
        when (removePasswordState) {
            RemovePasswordState.Preparing -> {
                OperationProgressContent(R.string.remove_password_preparing)
                return@Column
            }
            RemovePasswordState.Removing -> {
                OperationProgressContent(
                    messageResource = R.string.remove_password_removing,
                    onCancel = onCancelPasswordRemoval,
                )
                return@Column
            }
            else -> Unit
        }
        when (changePasswordState) {
            ChangePasswordState.Preparing -> {
                OperationProgressContent(R.string.change_password_preparing)
                return@Column
            }
            ChangePasswordState.Changing -> {
                OperationProgressContent(
                    messageResource = R.string.change_password_changing,
                    onCancel = onCancelPasswordChange,
                )
                return@Column
            }
            else -> Unit
        }
        when (state) {
            PdfOpenState.Idle -> IdleContent(
                onOpenPdf = onOpenPdf,
                onImagesToPdf = onImagesToPdf,
                imagesToPdfState = imagesToPdfState,
                onDismissImagesToPdfFailure = onDismissImagesToPdfFailure,
                mergePdfState = mergePdfState,
                onMergePdfs = onMergePdfs,
                onDismissMergeFailure = onDismissMergeFailure,
                splitPdfState = splitPdfState,
                onSplitPdf = onSplitPdf,
                onDismissSplitResult = onDismissSplitResult,
                extractPagesState = extractPagesState,
                onExtractPages = onExtractPages,
                onDismissPageExtractionFailure = onDismissPageExtractionFailure,
                deletePagesState = deletePagesState,
                onDeletePages = onDeletePages,
                onDismissPageDeletionFailure = onDismissPageDeletionFailure,
                rearrangePagesState = rearrangePagesState,
                onRearrangePages = onRearrangePages,
                onDismissPageRearrangementFailure = onDismissPageRearrangementFailure,
                rotatePagesState = rotatePagesState,
                onRotatePages = onRotatePages,
                onDismissPageRotationFailure = onDismissPageRotationFailure,
                duplicatePagesState = duplicatePagesState,
                onDuplicatePages = onDuplicatePages,
                onDismissPageDuplicationFailure = onDismissPageDuplicationFailure,
                compressPdfState = compressPdfState,
                onCompressPdf = onCompressPdf,
                onOpenCompressedPdf = onOpenCompressedPdf,
                onDismissPdfCompressionResult = onDismissPdfCompressionResult,
                protectPdfState = protectPdfState,
                onProtectPdf = onProtectPdf,
                onDismissPdfProtectionResult = onDismissPdfProtectionResult,
                removePasswordState = removePasswordState,
                onRemovePassword = onRemovePassword,
                onOpenPasswordRemovedPdf = onOpenPasswordRemovedPdf,
                onDismissPasswordRemovalResult = onDismissPasswordRemovalResult,
                changePasswordState = changePasswordState,
                onChangePassword = onChangePassword,
                onDismissPasswordChangeResult = onDismissPasswordChangeResult,
                scannerCaptureState = scannerCaptureState,
                onScanDocument = onScanDocument,
                onOpenScannerPdf = onOpenScannerPdf,
                onDismissScannerResult = onDismissScannerResult,
            )
            PdfOpenState.Opening -> OpeningContent()
            is PdfOpenState.Opened -> Unit
            is PdfOpenState.Failed -> FailedContent(state.failure, onOpenPdf)
        }
    }
}

@Composable
private fun IdleContent(
    onOpenPdf: () -> Unit,
    onImagesToPdf: () -> Unit,
    imagesToPdfState: ImagesToPdfState,
    onDismissImagesToPdfFailure: () -> Unit,
    mergePdfState: MergePdfState,
    onMergePdfs: () -> Unit,
    onDismissMergeFailure: () -> Unit,
    splitPdfState: SplitPdfState,
    onSplitPdf: () -> Unit,
    onDismissSplitResult: () -> Unit,
    extractPagesState: ExtractPagesState,
    onExtractPages: () -> Unit,
    onDismissPageExtractionFailure: () -> Unit,
    deletePagesState: DeletePagesState,
    onDeletePages: () -> Unit,
    onDismissPageDeletionFailure: () -> Unit,
    rearrangePagesState: RearrangePagesState,
    onRearrangePages: () -> Unit,
    onDismissPageRearrangementFailure: () -> Unit,
    rotatePagesState: RotatePagesState,
    onRotatePages: () -> Unit,
    onDismissPageRotationFailure: () -> Unit,
    duplicatePagesState: DuplicatePagesState,
    onDuplicatePages: () -> Unit,
    onDismissPageDuplicationFailure: () -> Unit,
    compressPdfState: CompressPdfState,
    onCompressPdf: () -> Unit,
    onOpenCompressedPdf: () -> Unit,
    onDismissPdfCompressionResult: () -> Unit,
    protectPdfState: ProtectPdfState,
    onProtectPdf: () -> Unit,
    onDismissPdfProtectionResult: () -> Unit,
    removePasswordState: RemovePasswordState,
    onRemovePassword: () -> Unit,
    onOpenPasswordRemovedPdf: () -> Unit,
    onDismissPasswordRemovalResult: () -> Unit,
    changePasswordState: ChangePasswordState,
    onChangePassword: () -> Unit,
    onDismissPasswordChangeResult: () -> Unit,
    scannerCaptureState: ScannerCaptureState,
    onScanDocument: () -> Unit,
    onOpenScannerPdf: () -> Unit,
    onDismissScannerResult: () -> Unit,
) {
    Text(
        text = stringResource(R.string.home_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.home_description),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    OpenButton(label = stringResource(R.string.open_pdf), onClick = onOpenPdf)
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.scan_document),
        onClick = onScanDocument,
        testTag = "scan_document_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.images_to_pdf),
        onClick = onImagesToPdf,
        testTag = "images_to_pdf_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.merge_pdf),
        onClick = onMergePdfs,
        testTag = "merge_pdf_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.split_pdf),
        onClick = onSplitPdf,
        testTag = "split_pdf_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.extract_pages),
        onClick = onExtractPages,
        testTag = "extract_pages_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.delete_pages),
        onClick = onDeletePages,
        testTag = "delete_pages_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.rearrange_pages),
        onClick = onRearrangePages,
        testTag = "rearrange_pages_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.rotate_pages),
        onClick = onRotatePages,
        testTag = "rotate_pages_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.duplicate_pages),
        onClick = onDuplicatePages,
        testTag = "duplicate_pages_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.compress_pdf),
        onClick = onCompressPdf,
        testTag = "compress_pdf_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.protect_pdf),
        onClick = onProtectPdf,
        testTag = "protect_pdf_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.remove_password),
        onClick = onRemovePassword,
        testTag = "remove_password_button",
    )
    Spacer(Modifier.height(4.dp))
    OpenButton(
        label = stringResource(R.string.change_password),
        onClick = onChangePassword,
        testTag = "change_password_button",
    )
    if (imagesToPdfState is ImagesToPdfState.Failed) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(imagesToPdfState.failure.messageResource),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("images_to_pdf_error"),
        )
        TextButton(onClick = onDismissImagesToPdfFailure) {
            Text(stringResource(R.string.dismiss))
        }
    }
    if (mergePdfState is MergePdfState.Failed) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(mergePdfState.failure.messageResource),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("merge_pdf_error"),
        )
        TextButton(onClick = onDismissMergeFailure) {
            Text(stringResource(R.string.dismiss))
        }
    }
    when (splitPdfState) {
        is SplitPdfState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(splitPdfState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("split_pdf_error"),
            )
            TextButton(onClick = onDismissSplitResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is SplitPdfState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.split_pdf_completed, splitPdfState.outputCount),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("split_pdf_success"),
            )
            TextButton(onClick = onDismissSplitResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        else -> Unit
    }
    if (extractPagesState is ExtractPagesState.Failed) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(extractPagesState.failure.messageResource),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("extract_pages_error"),
        )
        TextButton(onClick = onDismissPageExtractionFailure) {
            Text(stringResource(R.string.dismiss))
        }
    }
    if (deletePagesState is DeletePagesState.Failed) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(deletePagesState.failure.messageResource),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("delete_pages_error"),
        )
        TextButton(onClick = onDismissPageDeletionFailure) {
            Text(stringResource(R.string.dismiss))
        }
    }
    if (rearrangePagesState is RearrangePagesState.Failed) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(rearrangePagesState.failure.messageResource),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("rearrange_pages_error"),
        )
        TextButton(onClick = onDismissPageRearrangementFailure) {
            Text(stringResource(R.string.dismiss))
        }
    }
    if (rotatePagesState is RotatePagesState.Failed) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(rotatePagesState.failure.messageResource),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("rotate_pages_error"),
        )
        TextButton(onClick = onDismissPageRotationFailure) {
            Text(stringResource(R.string.dismiss))
        }
    }
    if (duplicatePagesState is DuplicatePagesState.Failed) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(duplicatePagesState.failure.messageResource),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("duplicate_pages_error"),
        )
        TextButton(onClick = onDismissPageDuplicationFailure) {
            Text(stringResource(R.string.dismiss))
        }
    }
    when (compressPdfState) {
        is CompressPdfState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(compressPdfState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("compress_pdf_error"),
            )
            TextButton(onClick = onDismissPdfCompressionResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is CompressPdfState.Completed -> {
            val context = LocalContext.current
            Spacer(Modifier.height(20.dp))
            Text(
                text = when {
                    compressPdfState.targetSizeBytes == null -> stringResource(
                        R.string.compress_pdf_completed,
                        android.text.format.Formatter.formatShortFileSize(context, compressPdfState.originalSizeBytes),
                        android.text.format.Formatter.formatShortFileSize(context, compressPdfState.outputSizeBytes),
                        percentageSaved(compressPdfState.originalSizeBytes, compressPdfState.outputSizeBytes),
                    )
                    compressPdfState.targetReached -> stringResource(
                        R.string.target_file_size_reached,
                        android.text.format.Formatter.formatShortFileSize(context, compressPdfState.originalSizeBytes),
                        android.text.format.Formatter.formatShortFileSize(context, compressPdfState.outputSizeBytes),
                        android.text.format.Formatter.formatShortFileSize(context, compressPdfState.targetSizeBytes),
                        percentageSaved(compressPdfState.originalSizeBytes, compressPdfState.outputSizeBytes),
                    )
                    else -> stringResource(
                        R.string.target_file_size_not_reached,
                        android.text.format.Formatter.formatShortFileSize(context, compressPdfState.originalSizeBytes),
                        android.text.format.Formatter.formatShortFileSize(context, compressPdfState.outputSizeBytes),
                        android.text.format.Formatter.formatShortFileSize(context, compressPdfState.targetSizeBytes),
                        percentageSaved(compressPdfState.originalSizeBytes, compressPdfState.outputSizeBytes),
                    )
                },
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("compress_pdf_success"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onOpenCompressedPdf,
                    modifier = Modifier.testTag("open_compressed_pdf"),
                ) {
                    Text(stringResource(R.string.open_pdf))
                }
                TextButton(onClick = onDismissPdfCompressionResult) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
        else -> Unit
    }
    when (protectPdfState) {
        is ProtectPdfState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(protectPdfState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("protect_pdf_error"),
            )
            TextButton(onClick = onDismissPdfProtectionResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is ProtectPdfState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.protect_pdf_completed, protectPdfState.pageCount),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("protect_pdf_success"),
            )
            TextButton(onClick = onDismissPdfProtectionResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        else -> Unit
    }
    when (removePasswordState) {
        is RemovePasswordState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(removePasswordState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("remove_password_error"),
            )
            TextButton(onClick = onDismissPasswordRemovalResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is RemovePasswordState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.remove_password_completed, removePasswordState.pageCount),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("remove_password_success"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onOpenPasswordRemovedPdf,
                    modifier = Modifier.testTag("open_password_removed_pdf"),
                ) {
                    Text(stringResource(R.string.open_pdf))
                }
                TextButton(onClick = onDismissPasswordRemovalResult) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
        else -> Unit
    }
    when (changePasswordState) {
        is ChangePasswordState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(changePasswordState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("change_password_error"),
            )
            TextButton(onClick = onDismissPasswordChangeResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is ChangePasswordState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.change_password_completed, changePasswordState.pageCount),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("change_password_success"),
            )
            TextButton(onClick = onDismissPasswordChangeResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        else -> Unit
    }
    when (scannerCaptureState) {
        is ScannerCaptureState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(scannerCaptureState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("scanner_error"),
            )
            TextButton(onClick = onDismissScannerResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is ScannerCaptureState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.scanner_pdf_saved),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("scanner_success"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onOpenScannerPdf,
                    modifier = Modifier.testTag("open_scanner_pdf"),
                ) {
                    Text(stringResource(R.string.open_pdf))
                }
                TextButton(onClick = onDismissScannerResult) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerCaptureScreen(
    state: ScannerCaptureState,
    onBeginCapture: () -> File?,
    onCaptureSaved: (File) -> Unit,
    onCaptureFailed: (File?) -> Unit,
    onCameraUnavailable: () -> Unit,
    onRetake: () -> Unit,
    onUpdateCrop: (ScannerCropSelection) -> Unit,
    onResetCrop: () -> Unit,
    onUpdateEnhancement: (ScannerEnhancementSettings) -> Unit,
    onAddPage: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onDeletePage: () -> Unit,
    onSavePdf: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner_title)) },
                navigationIcon = {
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("scanner_cancel")) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            ScannerCaptureState.Camera, ScannerCaptureState.Capturing -> ScannerCameraPreview(
                capturing = state is ScannerCaptureState.Capturing,
                onBeginCapture = onBeginCapture,
                onCaptureSaved = onCaptureSaved,
                onCaptureFailed = onCaptureFailed,
                onCameraUnavailable = onCameraUnavailable,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            ScannerCaptureState.PreparingPreview -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OperationProgressContent(R.string.scanner_preparing_preview, onCancel = onCancel)
                }
            }
            is ScannerCaptureState.Review -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(
                        R.string.scanner_page_position,
                        state.pageIndex + 1,
                        state.pageCount,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("scanner_page_position"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    TextButton(
                        enabled = state.pageIndex > 0,
                        onClick = { onSelectPage(state.pageIndex - 1) },
                        modifier = Modifier.testTag("scanner_previous_page"),
                    ) { Text(stringResource(R.string.previous_page)) }
                    TextButton(
                        enabled = state.pageIndex < state.pageCount - 1,
                        onClick = { onSelectPage(state.pageIndex + 1) },
                        modifier = Modifier.testTag("scanner_next_page"),
                    ) { Text(stringResource(R.string.next_page)) }
                }
                ScannerCropEditor(
                    bitmap = state.preview.bitmap,
                    crop = state.crop,
                    onCropChanged = onUpdateCrop,
                    modifier = Modifier.testTag("scanner_review_image"),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        R.string.scanner_capture_dimensions,
                        state.preview.sourceWidth,
                        state.preview.sourceHeight,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        if (state.preview.automaticCropDetected) R.string.scanner_crop_detected
                        else R.string.scanner_crop_manual,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(
                    onClick = onResetCrop,
                    modifier = Modifier.testTag("scanner_crop_reset"),
                ) {
                    Text(stringResource(R.string.scanner_crop_reset))
                }
                Text(
                    text = stringResource(R.string.scanner_capture_scope_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                ScannerEnhancementControls(
                    settings = state.enhancement,
                    processing = state.enhancementInProgress,
                    failure = state.enhancementFailure,
                    onSettingsChanged = onUpdateEnhancement,
                )
                state.enhancedPreview?.let { preview ->
                    Text(
                        text = stringResource(R.string.scanner_result_preview),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = stringResource(R.string.scanner_result_preview_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 420.dp)
                            .padding(top = 8.dp)
                            .testTag("scanner_enhanced_preview"),
                    )
                }
                state.saveFailure?.let { failure ->
                    Text(
                        text = stringResource(failure.messageResource),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp).testTag("scanner_save_error"),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    TextButton(
                        enabled = state.pageIndex > 0,
                        onClick = { onMovePage(state.pageIndex, state.pageIndex - 1) },
                        modifier = Modifier.testTag("scanner_move_page_left"),
                    ) { Text(stringResource(R.string.move_left)) }
                    TextButton(
                        enabled = state.pageIndex < state.pageCount - 1,
                        onClick = { onMovePage(state.pageIndex, state.pageIndex + 1) },
                        modifier = Modifier.testTag("scanner_move_page_right"),
                    ) { Text(stringResource(R.string.move_right)) }
                    TextButton(
                        onClick = onDeletePage,
                        modifier = Modifier.testTag("scanner_delete_page"),
                    ) { Text(stringResource(R.string.delete_page)) }
                }
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onRetake, modifier = Modifier.testTag("scanner_retake")) {
                        Text(stringResource(R.string.scanner_retake))
                    }
                    TextButton(onClick = onAddPage, modifier = Modifier.testTag("scanner_add_page")) {
                        Text(stringResource(R.string.scanner_add_page))
                    }
                    Button(onClick = onSavePdf, modifier = Modifier.testTag("scanner_save_pdf")) {
                        Text(stringResource(R.string.scanner_save_pdf))
                    }
                }
            }
            ScannerCaptureState.CreatingPdf -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OperationProgressContent(R.string.scanner_creating_pdf, onCancel = onCancel)
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ScannerEnhancementControls(
    settings: ScannerEnhancementSettings,
    processing: Boolean,
    failure: com.rameshta.quietpdf.pdf.ScannerCaptureFailure?,
    onSettingsChanged: (ScannerEnhancementSettings) -> Unit,
) {
    Text(
        text = stringResource(R.string.scanner_enhancement_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        listOf(
            ScannerColorMode.Color to R.string.scanner_mode_color,
            ScannerColorMode.Grayscale to R.string.scanner_mode_grayscale,
            ScannerColorMode.BlackAndWhite to R.string.scanner_mode_black_white,
        ).forEach { (mode, label) ->
            FilterChip(
                selected = settings.mode == mode,
                onClick = { onSettingsChanged(settings.copy(mode = mode)) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.testTag("scanner_mode_${mode.name}"),
            )
        }
    }
    Text(
        text = stringResource(
            R.string.scanner_brightness,
            (settings.brightness * 100).roundToInt(),
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Slider(
        value = settings.brightness,
        onValueChange = { onSettingsChanged(settings.copy(brightness = it)) },
        valueRange = ScannerEnhancementSettings.MinBrightness..ScannerEnhancementSettings.MaxBrightness,
        modifier = Modifier.fillMaxWidth().testTag("scanner_brightness_slider"),
    )
    Text(
        text = stringResource(
            R.string.scanner_contrast,
            (settings.contrast * 100).roundToInt(),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Slider(
        value = settings.contrast,
        onValueChange = { onSettingsChanged(settings.copy(contrast = it)) },
        valueRange = ScannerEnhancementSettings.MinContrast..ScannerEnhancementSettings.MaxContrast,
        modifier = Modifier.fillMaxWidth().testTag("scanner_contrast_slider"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.scanner_shadow_reduction))
            Text(
                stringResource(R.string.scanner_shadow_reduction_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = settings.shadowReduction,
            onCheckedChange = { onSettingsChanged(settings.copy(shadowReduction = it)) },
            modifier = Modifier.testTag("scanner_shadow_reduction"),
        )
    }
    if (processing) {
        Row(
            modifier = Modifier.padding(top = 8.dp).testTag("scanner_enhancement_progress"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.scanner_updating_preview))
        }
    }
    failure?.let {
        Text(
            text = stringResource(it.messageResource),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).testTag("scanner_enhancement_error"),
        )
    }
}

@Composable
private fun ScannerCropEditor(
    bitmap: Bitmap,
    crop: ScannerCropSelection,
    onCropChanged: (ScannerCropSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleRadius = with(density) { 24.dp.roundToPx() }
    val lineColor = MaterialTheme.colorScheme.primary
    val shadeColor = Color.Black.copy(alpha = 0.38f)
    val cornerLabels = listOf(
        stringResource(R.string.scanner_crop_top_left),
        stringResource(R.string.scanner_crop_top_right),
        stringResource(R.string.scanner_crop_bottom_right),
        stringResource(R.string.scanner_crop_bottom_left),
    )
    val moveLeftLabel = stringResource(R.string.scanner_crop_move_left)
    val moveRightLabel = stringResource(R.string.scanner_crop_move_right)
    val moveUpLabel = stringResource(R.string.scanner_crop_move_up)
    val moveDownLabel = stringResource(R.string.scanner_crop_move_down)
    fun move(index: Int, deltaX: Float, deltaY: Float): Boolean {
        if (editorSize.width <= 0 || editorSize.height <= 0) return false
        val point = crop.points[index]
        val updated = crop.moveCorner(
            index,
            ScannerCropPoint(
                point.x + deltaX / editorSize.width,
                point.y + deltaY / editorSize.height,
            ),
        )
        if (updated == crop) return false
        onCropChanged(updated)
        return true
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp, max = 560.dp)
            .aspectRatio(bitmap.width / bitmap.height.toFloat())
            .onSizeChanged { editorSize = it },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val points = crop.points.map { Offset(it.x * size.width, it.y * size.height) }
            val cropPath = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val outside = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                addPath(cropPath)
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            }
            drawPath(outside, shadeColor)
            drawPath(cropPath, lineColor, style = Stroke(width = 3.dp.toPx()))
            points.forEach { point ->
                drawCircle(Color.White, radius = 10.dp.toPx(), center = point)
                drawCircle(lineColor, radius = 10.dp.toPx(), center = point, style = Stroke(3.dp.toPx()))
            }
        }
        crop.points.forEachIndexed { index, point ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = ((point.x * editorSize.width).roundToInt() - handleRadius).coerceIn(
                                0,
                                (editorSize.width - handleRadius * 2).coerceAtLeast(0),
                            ),
                            y = ((point.y * editorSize.height).roundToInt() - handleRadius).coerceIn(
                                0,
                                (editorSize.height - handleRadius * 2).coerceAtLeast(0),
                            ),
                        )
                    }
                    .size(48.dp)
                    .semantics {
                        contentDescription = cornerLabels[index]
                        customActions = listOf(
                            CustomAccessibilityAction(moveLeftLabel) {
                                move(index, -editorSize.width * 0.02f, 0f)
                            },
                            CustomAccessibilityAction(moveRightLabel) {
                                move(index, editorSize.width * 0.02f, 0f)
                            },
                            CustomAccessibilityAction(moveUpLabel) {
                                move(index, 0f, -editorSize.height * 0.02f)
                            },
                            CustomAccessibilityAction(moveDownLabel) {
                                move(index, 0f, editorSize.height * 0.02f)
                            },
                        )
                    }
                    .testTag("scanner_crop_corner_$index")
                    .pointerInput(index, crop, editorSize) {
                        var workingCrop = crop
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (editorSize.width <= 0 || editorSize.height <= 0) {
                                return@detectDragGestures
                            }
                            val current = workingCrop.points[index]
                            val updated = workingCrop.moveCorner(
                                index,
                                ScannerCropPoint(
                                    current.x + dragAmount.x / editorSize.width,
                                    current.y + dragAmount.y / editorSize.height,
                                ),
                            )
                            if (updated != workingCrop) {
                                workingCrop = updated
                                onCropChanged(updated)
                            }
                        }
                    },
            )
        }
    }
}

@Composable
private fun ScannerCameraPreview(
    capturing: Boolean,
    onBeginCapture: () -> File?,
    onCaptureSaved: (File) -> Unit,
    onCaptureFailed: (File?) -> Unit,
    onCameraUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    DisposableEffect(controller, lifecycleOwner) {
        try {
            controller.bindToLifecycle(lifecycleOwner)
        } catch (_: Exception) {
            onCameraUnavailable()
        }
        onDispose { controller.unbind() }
    }
    Box(modifier = modifier) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize().testTag("scanner_camera_preview"),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (capturing) {
                CircularProgressIndicator(modifier = Modifier.testTag("scanner_capture_progress"))
            }
            Button(
                enabled = !capturing,
                onClick = {
                    val captureFile = onBeginCapture() ?: return@Button
                    val output = ImageCapture.OutputFileOptions.Builder(captureFile).build()
                    controller.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                onCaptureSaved(captureFile)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onCaptureFailed(captureFile)
                            }
                        },
                    )
                },
                modifier = Modifier.testTag("scanner_shutter"),
            ) {
                Text(stringResource(R.string.scanner_capture))
            }
        }
    }
}

@Composable
private fun OperationProgressContent(
    messageResource: Int,
    argument: Int? = null,
    firstArgument: Int? = null,
    secondArgument: Int? = null,
    thirdArgument: Int? = null,
    fourthArgument: Int? = null,
    onCancel: (() -> Unit)? = null,
) {
    CircularProgressIndicator(
        modifier = Modifier.size(48.dp).testTag("operation_progress"),
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = when {
            firstArgument != null && secondArgument != null &&
                thirdArgument != null && fourthArgument != null -> {
                stringResource(
                    messageResource,
                    thirdArgument,
                    fourthArgument,
                    firstArgument,
                    secondArgument,
                )
            }
            firstArgument != null && secondArgument != null -> {
                stringResource(messageResource, firstArgument, secondArgument)
            }
            argument != null -> stringResource(messageResource, argument)
            else -> stringResource(messageResource)
        },
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    if (onCancel != null) {
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel, modifier = Modifier.testTag("operation_cancel")) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun ImagesToPdfCreatingContent(imageCount: Int) {
    CircularProgressIndicator(
        modifier = Modifier.size(48.dp).testTag("images_to_pdf_progress"),
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.images_to_pdf_creating, imageCount),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun OpeningContent() {
    CircularProgressIndicator(
        modifier = Modifier
            .size(48.dp)
            .testTag("opening_indicator"),
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.opening_pdf),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun FailedContent(failure: PdfOpenFailure, onOpenPdf: () -> Unit) {
    Text(
        text = stringResource(failure.messageResource),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("open_error"),
    )
    Spacer(Modifier.height(24.dp))
    OpenButton(label = stringResource(R.string.open_pdf), onClick = onOpenPdf)
}

@Composable
private fun OpenButton(
    label: String,
    onClick: () -> Unit,
    testTag: String = "open_pdf_button",
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag),
    ) {
        Text(label)
    }
}

@Preview(showBackground = true)
@Composable
private fun QuietPdfPreview() {
    QuietPDFTheme(dynamicColor = false) {
        QuietPdfApp(
            state = PdfOpenState.Idle,
            onOpenPdf = {},
            renderPage = { _, _ -> PageRenderResult.Failed(PageRenderFailure.UnableToRender) },
        )
    }
}
