package com.rameshta.quietpdf

import android.Manifest
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.rameshta.quietpdf.pdf.TextWatermarkPosition
import com.rameshta.quietpdf.pdf.TextWatermarkPreviewResult
import com.rameshta.quietpdf.pdf.TextWatermarkSettings
import com.rameshta.quietpdf.pdf.TextWatermarkState
import com.rameshta.quietpdf.pdf.TextWatermarkPageSelection
import com.rameshta.quietpdf.pdf.ImageWatermarkPageSelection
import com.rameshta.quietpdf.pdf.ImageWatermarkPosition
import com.rameshta.quietpdf.pdf.ImageWatermarkPreviewResult
import com.rameshta.quietpdf.pdf.ImageWatermarkSettings
import com.rameshta.quietpdf.pdf.ImageWatermarkState
import com.rameshta.quietpdf.pdf.EmbeddedImagePreview
import com.rameshta.quietpdf.pdf.ExtractImagesDestination
import com.rameshta.quietpdf.pdf.ExtractImagesState
import com.rameshta.quietpdf.pdf.FillFormsState
import com.rameshta.quietpdf.pdf.FillFormsAnalysis
import com.rameshta.quietpdf.pdf.FormFieldDescriptor
import com.rameshta.quietpdf.pdf.FormFieldKind
import com.rameshta.quietpdf.pdf.FormFieldUpdate
import com.rameshta.quietpdf.pdf.FillFormsEngine
import com.rameshta.quietpdf.pdf.SignPdfPreviewResult
import com.rameshta.quietpdf.pdf.SignPdfState
import com.rameshta.quietpdf.pdf.VisibleSignatureSettings
import com.rameshta.quietpdf.pdf.AnnotatePdfPreviewResult
import com.rameshta.quietpdf.pdf.AnnotatePdfState
import com.rameshta.quietpdf.pdf.PdfAnnotationItem
import com.rameshta.quietpdf.pdf.RecentPdf
import com.rameshta.quietpdf.pdf.FavoritePdf
import com.rameshta.quietpdf.pdf.PdfHistoryEntry
import com.rameshta.quietpdf.pdf.PdfHistoryOperation
import com.rameshta.quietpdf.pdf.PdfFileOrganizer
import com.rameshta.quietpdf.pdf.PdfFileSortOrder
import com.rameshta.quietpdf.pdf.PdfShareIntentFactory
import com.rameshta.quietpdf.pdf.ContinueReadingPdf
import com.rameshta.quietpdf.pdf.SmartTool
import com.rameshta.quietpdf.pdf.FavoriteToolStore
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
import com.rameshta.quietpdf.ui.theme.AppThemeMode
import com.rameshta.quietpdf.ui.theme.AppThemePreferences
import com.rameshta.quietpdf.ui.theme.LauncherIconController
import com.rameshta.quietpdf.ads.AdMobController
import com.rameshta.quietpdf.ads.AdPlacementPolicy
import com.rameshta.quietpdf.ads.HomeBannerAd
import com.rameshta.quietpdf.ads.HomeNativeAd
import com.rameshta.quietpdf.ads.rememberHomeBannerAdSession
import com.rameshta.quietpdf.ads.FullScreenAdCoordinator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), DefaultLifecycleObserver {
    private val viewModel: PdfOpenViewModel by viewModels()
    private val adMobController = AdMobController()
    private lateinit var themePreferences: AppThemePreferences
    private lateinit var launcherIconController: LauncherIconController
    private var selectedThemeMode by mutableStateOf(AppThemeMode.Light)
    private val fullScreenAds by lazy {
        processAdCoordinator ?: FullScreenAdCoordinator(
            applicationContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            BuildConfig.ADMOB_APP_OPEN_ID,
        ).also { processAdCoordinator = it }
    }

    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.attachBaseContext(newBase)
            return
        }
        val languageTag = newBase.getSharedPreferences(LanguagePreferences, Context.MODE_PRIVATE)
            .getString(LanguageTagKey, "").orEmpty()
        if (languageTag.isBlank()) {
            super.attachBaseContext(newBase)
        } else {
            val configuration = newBase.resources.configuration
            configuration.setLocale(Locale.forLanguageTag(languageTag))
            super.attachBaseContext(newBase.createConfigurationContext(configuration))
        }
    }
    private var adsConsentAllowsRequests = false
    private var externalTransitionActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super<ComponentActivity>.onCreate(savedInstanceState)
        val systemThemeFallback = if (
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        ) AppThemeMode.Dark else AppThemeMode.Light
        themePreferences = AppThemePreferences(this)
        launcherIconController = LauncherIconController(this)
        selectedThemeMode = themePreferences.read(systemThemeFallback)
        applyThemeChrome(selectedThemeMode)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        if (savedInstanceState == null && !processSessionActive) {
            processSessionActive = true
            fullScreenAds.beginSession()
        }
        adMobController.start(this)
        if (savedInstanceState == null) openFromIntent(intent)

        setContent {
            QuietPDFTheme(darkTheme = selectedThemeMode == AppThemeMode.Dark) {
                val adMobState by adMobController.state.collectAsState()
                LaunchedEffect(adMobState.canRequestAds) {
                    adsConsentAllowsRequests = adMobState.canRequestAds
                    fullScreenAds.preload(adMobState.canRequestAds)
                }
                var eligibleWorkflowAwaitingExit by rememberSaveable { mutableStateOf(false) }
                var newestObservedHistory by remember {
                    mutableStateOf(viewModel.history.maxOfOrNull { it.completedEpochMillis } ?: -1L)
                }
                LaunchedEffect(viewModel.history) {
                    val newEligibleEntries = viewModel.history.asSequence()
                        .filter { it.completedEpochMillis > newestObservedHistory }
                        .filter { it.operation.isInterstitialEligibleWorkflow() }
                        .sortedBy(PdfHistoryEntry::completedEpochMillis)
                        .toList()
                    newEligibleEntries.forEach { entry ->
                        fullScreenAds.recordSuccessfulWorkflow(
                            "${entry.operation.name}:${entry.completedEpochMillis}",
                        )
                    }
                    if (newEligibleEntries.isNotEmpty()) eligibleWorkflowAwaitingExit = true
                    newestObservedHistory = maxOf(
                        newestObservedHistory,
                        viewModel.history.maxOfOrNull { it.completedEpochMillis } ?: -1L,
                    )
                }
                val finishEligibleResult: (() -> Unit) -> Unit = { continueNavigation ->
                    if (eligibleWorkflowAwaitingExit) {
                        eligibleWorkflowAwaitingExit = false
                        finishEligibleWorkflow(continueNavigation)
                    } else {
                        continueNavigation()
                    }
                }
                var pendingProtectPassword by remember { mutableStateOf<CharArray?>(null) }
                var pendingRemovalPassword by remember { mutableStateOf<CharArray?>(null) }
                var pendingPasswordChange by remember {
                    mutableStateOf<Pair<CharArray, CharArray>?>(null)
                }
                var pendingTextWatermark by remember { mutableStateOf<TextWatermarkSettings?>(null) }
                var pendingImageWatermark by remember { mutableStateOf<ImageWatermarkSettings?>(null) }
                var pendingExtractedImages by remember { mutableStateOf<Set<Int>?>(null) }
                var pendingFormUpdates by remember { mutableStateOf<List<FormFieldUpdate>?>(null) }
                var pendingVisibleSignature by remember {
                    mutableStateOf<Pair<Bitmap, VisibleSignatureSettings>?>(null)
                }
                var pendingAnnotations by remember { mutableStateOf<List<PdfAnnotationItem>?>(null) }
                DisposableEffect(Unit) {
                    onDispose {
                        pendingVisibleSignature?.first?.recycle()
                        pendingVisibleSignature = null
                    }
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
                val createTextWatermarkedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    val settings = pendingTextWatermark
                    pendingTextWatermark = null
                    if (uri == null || settings == null) viewModel.cancelTextWatermark()
                    else viewModel.applyTextWatermark(uri, settings)
                }
                val textWatermarkPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForTextWatermark(uri)
                    }
                }
                val createImageWatermarkedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    val settings = pendingImageWatermark
                    pendingImageWatermark = null
                    if (uri == null || settings == null) viewModel.cancelImageWatermark()
                    else viewModel.applyImageWatermark(uri, settings)
                }
                val imageWatermarkImagePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectImageForWatermark(uri)
                    }
                }
                val imageWatermarkPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForImageWatermark(uri)
                    }
                }
                val extractedImagesFolder = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    val selection = pendingExtractedImages
                    pendingExtractedImages = null
                    if (uri == null || selection == null) viewModel.cancelImageExtraction()
                    else {
                        retainDirectoryPermission(uri)
                        viewModel.exportExtractedImagesToDirectory(uri, selection)
                    }
                }
                val extractedImagesZip = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/zip"),
                ) { uri ->
                    val selection = pendingExtractedImages
                    pendingExtractedImages = null
                    if (uri == null || selection == null) viewModel.cancelImageExtraction()
                    else viewModel.exportExtractedImagesToZip(uri, selection)
                }
                val extractImagesPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForImageExtraction(uri)
                    }
                }
                val createFilledFormPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    val updates = pendingFormUpdates
                    pendingFormUpdates = null
                    if (uri == null || updates == null) viewModel.cancelFormFilling()
                    else viewModel.fillSelectedPdfForm(uri, updates)
                }
                val fillFormsPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForFormFilling(uri)
                    }
                }
                val createSignedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    val pending = pendingVisibleSignature
                    pendingVisibleSignature = null
                    if (uri == null || pending == null) {
                        pending?.first?.recycle()
                        viewModel.cancelPdfSigning()
                    } else {
                        viewModel.signSelectedPdf(uri, pending.first, pending.second)
                    }
                }
                val signatureImagePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectImportedSignature(uri)
                    }
                }
                val signPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForSigning(uri)
                    }
                }
                val createAnnotatedPdf = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf"),
                ) { uri ->
                    val annotations = pendingAnnotations
                    pendingAnnotations = null
                    if (uri == null || annotations == null) viewModel.cancelPdfAnnotation()
                    else viewModel.annotateSelectedPdf(uri, annotations)
                }
                val annotatePdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        retainReadPermission(uri)
                        viewModel.selectPdfForAnnotation(uri)
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

                val extractImagesState = viewModel.extractImagesState
                LaunchedEffect(extractImagesState) {
                    val share = extractImagesState as? ExtractImagesState.ShareReady
                        ?: return@LaunchedEffect
                    val streams = ArrayList(share.imageUris)
                    val intent = Intent(
                        if (streams.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE,
                    ).apply {
                        type = "image/png"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (streams.size == 1) putExtra(Intent.EXTRA_STREAM, streams.first())
                        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
                        clipData = ClipData.newUri(contentResolver, "Extracted image", streams.first()).also { clip ->
                            streams.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                        }
                    }
                    externalTransitionActive = true
                    startActivity(Intent.createChooser(intent, getString(R.string.extract_images_share_selected)))
                    viewModel.resumeImageExtractionAfterShare()
                }

                val homeBannerAdSession = rememberHomeBannerAdSession(
                    adUnitId = BuildConfig.ADMOB_HOME_BANNER_ID,
                    enabled = adMobState.canRequestAds,
                )
                QuietPdfApp(
                    state = viewModel.state,
                    recentPdfs = viewModel.recentPdfs,
                    favoritePdfs = viewModel.favoritePdfs,
                    history = viewModel.history,
                    continueReading = viewModel.continueReading,
                    favoriteTools = viewModel.favoriteTools,
                    legacyHomeSections = false,
                    onOpenRecentPdf = viewModel::openRecentPdf,
                    onRemoveRecentPdf = viewModel::removeRecentPdf,
                    onClearRecentPdfs = viewModel::clearRecentPdfs,
                    onOpenFavoritePdf = viewModel::openFavoritePdf,
                    onToggleFavoritePdf = viewModel::toggleFavoritePdf,
                    onRemoveFavoritePdf = viewModel::removeFavoritePdf,
                    onClearHistory = viewModel::clearHistory,
                    onRemoveHistoryEntry = viewModel::removeHistoryEntry,
                    onToggleFavoriteTool = viewModel::toggleFavoriteTool,
                    onSharePdf = ::sharePdf,
                    settings = QuietPdfSettings(
                        themeMode = selectedThemeMode,
                        onChangeTheme = ::changeAppTheme,
                        selectedLanguageTag = currentAppLanguageTag(),
                        onChangeLanguage = ::changeAppLanguage,
                        adPrivacyOptionsRequired = adMobState.privacyOptionsRequired,
                        onOpenAdvertisingPrivacy = ::openAdvertisingPrivacy,
                    ),
                    adsCanLoad = adMobState.canRequestAds,
                    homeNativeContent = if (
                        adMobState.canRequestAds && BuildConfig.ADMOB_NATIVE_ID.isNotBlank()
                    ) {
                        {
                            HomeNativeAd(
                                adUnitId = BuildConfig.ADMOB_NATIVE_ID,
                                enabled = true,
                            )
                        }
                    } else null,
                    homeBannerContent = homeBannerAdSession?.let { session ->
                        { HomeBannerAd(session) }
                    },
                    onOpenPdf = { launchExternal(picker, arrayOf("application/pdf")) },
                    // Closing the reader is never an advertisement transition. Eligible
                    // interstitials are evaluated only from explicit saved-result dismissal.
                    onClosePdf = viewModel::closePdf,
                    renderPage = viewModel::renderPage,
                    onPageChanged = viewModel::rememberPage,
                    searchDocument = viewModel::search,
                    onToggleBookmark = viewModel::toggleBookmark,
                    loadTableOfContents = viewModel::loadTableOfContents,
                    inspectHealth = viewModel::inspectHealth,
                    imagesToPdfState = viewModel.imagesToPdfState,
                    onImagesToPdf = { launchExternal(imagePicker, arrayOf("image/*")) },
                    onDismissImagesToPdfFailure = viewModel::clearImagesToPdfFailure,
                    onConfirmImagesPdfLayout = { layout ->
                        viewModel.configureImagesPdfLayout(layout)
                        launchExternal(createImagesPdf, "QuietPDF-images.pdf")
                    },
                    onCancelImagesPdfLayout = viewModel::discardSelectedImages,
                    mergePdfState = viewModel.mergePdfState,
                    onMergePdfs = { launchExternal(mergePdfPicker, arrayOf("application/pdf")) },
                    onMoveMergeDocument = viewModel::moveMergeDocument,
                    onRemoveMergeDocument = viewModel::removeMergeDocument,
                    onConfirmMerge = { launchExternal(createMergedPdf, "QuietPDF-merged.pdf") },
                    onCancelMerge = viewModel::cancelMergePdf,
                    onDismissMergeFailure = viewModel::clearMergePdfFailure,
                    splitPdfState = viewModel.splitPdfState,
                    onSplitPdf = { launchExternal(splitPdfPicker, arrayOf("application/pdf")) },
                    onConfirmSplit = { ranges ->
                        viewModel.configureSplitPdf(ranges)
                        launchExternal(splitOutputFolder, null)
                    },
                    onCancelSplit = viewModel::cancelSplitPdf,
                    onDismissSplitResult = {
                        if (viewModel.splitPdfState is SplitPdfState.Completed) {
                            finishEligibleResult(viewModel::clearSplitPdfResult)
                        } else viewModel.clearSplitPdfResult()
                    },
                    extractPagesState = viewModel.extractPagesState,
                    onExtractPages = {
                        launchExternal(extractPagesPicker, arrayOf("application/pdf"))
                    },
                    onConfirmPageExtraction = { selectedPages ->
                        viewModel.configurePageExtraction(selectedPages)
                        launchExternal(createExtractedPdf, "QuietPDF-extracted-pages.pdf")
                    },
                    onCancelPageExtraction = viewModel::cancelExtractPages,
                    onDismissPageExtractionFailure = viewModel::clearExtractPagesFailure,
                    deletePagesState = viewModel.deletePagesState,
                    onDeletePages = { launchExternal(deletePagesPicker, arrayOf("application/pdf")) },
                    onConfirmPageDeletion = { deletedPages ->
                        viewModel.configurePageDeletion(deletedPages)
                        launchExternal(createDeletedPagesPdf, "QuietPDF-pages-removed.pdf")
                    },
                    onCancelPageDeletion = viewModel::cancelDeletePages,
                    onDismissPageDeletionFailure = viewModel::clearDeletePagesFailure,
                    rearrangePagesState = viewModel.rearrangePagesState,
                    onRearrangePages = {
                        launchExternal(rearrangePagesPicker, arrayOf("application/pdf"))
                    },
                    onMoveRearrangedPage = viewModel::moveRearrangedPage,
                    onResetRearrangedPages = viewModel::resetRearrangedPageOrder,
                    onConfirmPageRearrangement = {
                        launchExternal(createRearrangedPdf, "QuietPDF-rearranged-pages.pdf")
                    },
                    onCancelPageRearrangement = viewModel::cancelRearrangePages,
                    onDismissPageRearrangementFailure = viewModel::clearRearrangePagesFailure,
                    rotatePagesState = viewModel.rotatePagesState,
                    onRotatePages = { launchExternal(rotatePagesPicker, arrayOf("application/pdf")) },
                    onConfirmPageRotation = { selectedPages, rotation ->
                        viewModel.configurePageRotation(selectedPages, rotation)
                        launchExternal(createRotatedPagesPdf, "QuietPDF-rotated-pages.pdf")
                    },
                    onCancelPageRotation = viewModel::cancelRotatePages,
                    onDismissPageRotationFailure = viewModel::clearRotatePagesFailure,
                    duplicatePagesState = viewModel.duplicatePagesState,
                    onDuplicatePages = { launchExternal(duplicatePagesPicker, arrayOf("application/pdf")) },
                    onConfirmPageDuplication = { selectedPages ->
                        viewModel.configurePageDuplication(selectedPages)
                        launchExternal(createDuplicatedPagesPdf, "QuietPDF-duplicated-pages.pdf")
                    },
                    onCancelPageDuplication = viewModel::cancelDuplicatePages,
                    onDismissPageDuplicationFailure = viewModel::clearDuplicatePagesFailure,
                    compressPdfState = viewModel.compressPdfState,
                    onCompressPdf = { launchExternal(compressPdfPicker, arrayOf("application/pdf")) },
                    onConfirmPdfCompression = { mode ->
                        viewModel.configurePdfCompression(mode)
                        launchExternal(createCompressedPdf, "QuietPDF-compressed.pdf")
                    },
                    onCancelPdfCompression = viewModel::cancelCompressPdf,
                    onOpenCompressedPdf = viewModel::openCompressedPdf,
                    onDismissPdfCompressionResult = {
                        if (viewModel.compressPdfState is CompressPdfState.Completed) {
                            finishEligibleResult(viewModel::clearCompressPdfResult)
                        } else viewModel.clearCompressPdfResult()
                    },
                    protectPdfState = viewModel.protectPdfState,
                    onProtectPdf = { launchExternal(protectPdfPicker, arrayOf("application/pdf")) },
                    onConfirmPdfProtection = { password ->
                        pendingProtectPassword?.fill('\u0000')
                        pendingProtectPassword = password.copyOf()
                        password.fill('\u0000')
                        launchExternal(createProtectedPdf, "QuietPDF-protected.pdf")
                    },
                    onCancelPdfProtection = {
                        pendingProtectPassword?.fill('\u0000')
                        pendingProtectPassword = null
                        viewModel.cancelProtectPdf()
                    },
                    onDismissPdfProtectionResult = {
                        if (viewModel.protectPdfState is ProtectPdfState.Completed) {
                            finishEligibleResult(viewModel::clearProtectPdfResult)
                        } else viewModel.clearProtectPdfResult()
                    },
                    removePasswordState = viewModel.removePasswordState,
                    onRemovePassword = {
                        launchExternal(removePasswordPicker, arrayOf("application/pdf"))
                    },
                    onConfirmPasswordRemoval = { password ->
                        pendingRemovalPassword?.fill('\u0000')
                        pendingRemovalPassword = password.copyOf()
                        password.fill('\u0000')
                        launchExternal(createPasswordRemovedPdf, "QuietPDF-unlocked.pdf")
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
                        launchExternal(changePasswordPicker, arrayOf("application/pdf"))
                    },
                    onConfirmPasswordChange = { currentPassword, newPassword ->
                        pendingPasswordChange?.first?.fill('\u0000')
                        pendingPasswordChange?.second?.fill('\u0000')
                        pendingPasswordChange = currentPassword.copyOf() to newPassword.copyOf()
                        currentPassword.fill('\u0000')
                        newPassword.fill('\u0000')
                        launchExternal(createPasswordChangedPdf, "QuietPDF-password-changed.pdf")
                    },
                    onCancelPasswordChange = {
                        pendingPasswordChange?.first?.fill('\u0000')
                        pendingPasswordChange?.second?.fill('\u0000')
                        pendingPasswordChange = null
                        viewModel.cancelChangePassword()
                    },
                    onDismissPasswordChangeResult = viewModel::clearChangePasswordResult,
                    textWatermarkState = viewModel.textWatermarkState,
                    onTextWatermark = {
                        launchExternal(textWatermarkPicker, arrayOf("application/pdf"))
                    },
                    renderTextWatermarkPreview = viewModel::renderTextWatermarkPreview,
                    onConfirmTextWatermark = { settings ->
                        pendingTextWatermark = settings
                        launchExternal(createTextWatermarkedPdf, "QuietPDF-text-watermark.pdf")
                    },
                    onCancelTextWatermark = {
                        pendingTextWatermark = null
                        viewModel.cancelTextWatermark()
                    },
                    onOpenTextWatermarkedPdf = viewModel::openTextWatermarkedPdf,
                    onDismissTextWatermarkResult = {
                        if (viewModel.textWatermarkState is TextWatermarkState.Completed) {
                            finishEligibleResult(viewModel::clearTextWatermarkResult)
                        } else viewModel.clearTextWatermarkResult()
                    },
                    imageWatermarkState = viewModel.imageWatermarkState,
                    onImageWatermark = {
                        launchExternal(imageWatermarkPdfPicker, arrayOf("application/pdf"))
                    },
                    onChooseWatermarkImage = {
                        launchExternal(imageWatermarkImagePicker, arrayOf("image/*"))
                    },
                    renderImageWatermarkPreview = viewModel::renderImageWatermarkPreview,
                    onConfirmImageWatermark = { settings ->
                        pendingImageWatermark = settings
                        launchExternal(createImageWatermarkedPdf, "QuietPDF-image-watermark.pdf")
                    },
                    onCancelImageWatermark = {
                        pendingImageWatermark = null
                        viewModel.cancelImageWatermark()
                    },
                    onOpenImageWatermarkedPdf = viewModel::openImageWatermarkedPdf,
                    onDismissImageWatermarkResult = {
                        if (viewModel.imageWatermarkState is ImageWatermarkState.Completed) {
                            finishEligibleResult(viewModel::clearImageWatermarkResult)
                        } else viewModel.clearImageWatermarkResult()
                    },
                    extractImagesState = extractImagesState,
                    onExtractImages = {
                        launchExternal(extractImagesPdfPicker, arrayOf("application/pdf"))
                    },
                    onSaveSelectedImages = { selection ->
                        pendingExtractedImages = selection
                        launchExternal(extractedImagesFolder, null)
                    },
                    onSaveAllImages = { selection ->
                        pendingExtractedImages = selection
                        launchExternal(extractedImagesFolder, null)
                    },
                    onExportImagesZip = { selection ->
                        pendingExtractedImages = selection
                        launchExternal(extractedImagesZip, "QuietPDF-extracted-images.zip")
                    },
                    onShareExtractedImages = viewModel::prepareExtractedImagesShare,
                    onCancelImageExtraction = {
                        pendingExtractedImages = null
                        viewModel.cancelImageExtraction()
                    },
                    onDismissExtractImagesResult = {
                        if (viewModel.extractImagesState is ExtractImagesState.Completed) {
                            finishEligibleResult(viewModel::clearExtractImagesResult)
                        } else viewModel.clearExtractImagesResult()
                    },
                    fillFormsState = viewModel.fillFormsState,
                    onFillForms = { launchExternal(fillFormsPdfPicker, arrayOf("application/pdf")) },
                    onConfirmFormFilling = { updates ->
                        pendingFormUpdates = updates
                        launchExternal(createFilledFormPdf, "QuietPDF-filled-form.pdf")
                    },
                    onCancelFormFilling = {
                        pendingFormUpdates = null
                        viewModel.cancelFormFilling()
                    },
                    onOpenFilledFormPdf = viewModel::openFilledFormPdf,
                    onDismissFillFormsResult = {
                        if (viewModel.fillFormsState is FillFormsState.Completed) {
                            finishEligibleResult(viewModel::clearFillFormsResult)
                        } else viewModel.clearFillFormsResult()
                    },
                    signPdfState = viewModel.signPdfState,
                    onSignPdf = { launchExternal(signPdfPicker, arrayOf("application/pdf")) },
                    onImportSignature = { launchExternal(signatureImagePicker, arrayOf("image/*")) },
                    renderSignaturePreview = viewModel::renderSignaturePreview,
                    onConfirmSignature = { signature, settings ->
                        pendingVisibleSignature?.first?.recycle()
                        pendingVisibleSignature = try {
                            signature.copy(Bitmap.Config.ARGB_8888, false)?.let { it to settings }
                        } catch (_: OutOfMemoryError) {
                            null
                        }
                        if (pendingVisibleSignature == null) viewModel.signatureCopyFailed()
                        else launchExternal(createSignedPdf, "QuietPDF-visibly-signed.pdf")
                    },
                    onCancelPdfSigning = {
                        pendingVisibleSignature?.first?.recycle()
                        pendingVisibleSignature = null
                        viewModel.cancelPdfSigning()
                    },
                    onOpenSignedPdf = viewModel::openSignedPdf,
                    onDismissSignPdfResult = {
                        if (viewModel.signPdfState is SignPdfState.Completed) {
                            finishEligibleResult(viewModel::clearSignPdfResult)
                        } else viewModel.clearSignPdfResult()
                    },
                    annotatePdfState = viewModel.annotatePdfState,
                    onAnnotatePdf = { launchExternal(annotatePdfPicker, arrayOf("application/pdf")) },
                    renderAnnotationPreview = viewModel::renderAnnotationPreview,
                    onConfirmAnnotations = { annotations ->
                        pendingAnnotations = annotations
                        launchExternal(createAnnotatedPdf, "QuietPDF-annotated.pdf")
                    },
                    onCancelPdfAnnotation = {
                        pendingAnnotations = null
                        viewModel.cancelPdfAnnotation()
                    },
                    onOpenAnnotatedPdf = viewModel::openAnnotatedPdf,
                    onDismissAnnotatePdfResult = viewModel::clearAnnotatePdfResult,
                    scannerCaptureState = viewModel.scannerCaptureState,
                    onScanDocument = {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.startScannerCapture()
                        } else {
                            launchExternal(cameraPermission, Manifest.permission.CAMERA)
                        }
                    },
                    onBeginScannerCapture = viewModel::beginScannerCapture,
                    onScannerCaptureSaved = viewModel::scannerCaptureSaved,
                    onScannerCaptureFailed = viewModel::scannerCaptureFailed,
                    onScannerCameraUnavailable = viewModel::scannerCameraUnavailable,
                    onRetakeScannerCapture = viewModel::retakeScannerCapture,
                    onUpdateScannerCrop = viewModel::updateScannerCrop,
                    onScannerCropChangeFinished = viewModel::finishScannerCropUpdate,
                    onResetScannerCrop = viewModel::resetScannerCrop,
                    onUpdateScannerEnhancement = viewModel::updateScannerEnhancement,
                    onAddScannerPage = viewModel::addScannerPage,
                    onSelectScannerPage = viewModel::selectScannerPage,
                    onMoveScannerPage = viewModel::moveScannerPage,
                    onDeleteScannerPage = viewModel::deleteScannerPage,
                    onSaveScannerPdf = { launchExternal(createScannedPdf, "QuietPDF-scan.pdf") },
                    onCancelScannerCapture = viewModel::cancelScannerCapture,
                    onOpenScannerPdf = viewModel::openScannerPdf,
                    onDismissScannerResult = {
                        if (viewModel.scannerCaptureState is ScannerCaptureState.Completed) {
                            finishEligibleResult(viewModel::clearScannerResult)
                        } else viewModel.clearScannerResult()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntent(intent)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (processIsForeground) return
        processIsForeground = true
        if (externalTransitionActive) {
            externalTransitionActive = false
            return
        }
        if (processFullScreenReturnPending) {
            processFullScreenReturnPending = false
            return
        }
        val backgroundDurationMillis = processBackgroundedAtElapsedMillis?.let {
            (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L)
        }
        processBackgroundedAtElapsedMillis = null
        if (!processSessionActive) {
            processSessionActive = true
            fullScreenAds.beginSession()
        }
        fullScreenAds.showAppOpenIfEligible(
            activity = this,
            consentAllowsAds = adsConsentAllowsRequests,
            safeHomeTransition = isSafeForAppOpen(),
            homeAlreadyInteractive = false,
            backgroundDurationMillis = backgroundDurationMillis,
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!processIsForeground) return
        processIsForeground = false
        // Some launchers move the task to the background when its active alias changes.
        // Apply the queued icon only after the user has naturally left QuietPDF.
        launcherIconController.apply(selectedThemeMode)
        if (externalTransitionActive) return
        if (fullScreenAds.isFullScreenAdActive) {
            processFullScreenReturnPending = true
            return
        }
        processBackgroundedAtElapsedMillis = SystemClock.elapsedRealtime()
        fullScreenAds.completeSession()
        processSessionActive = false
    }

    override fun onResume() {
        super<ComponentActivity>.onResume()
        // Activity-result destinations sometimes return before ProcessLifecycleOwner emits a
        // foreground transition. Clear the one-shot suppression once this Activity is usable.
        externalTransitionActive = false
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        super<ComponentActivity>.onDestroy()
    }

    private fun isSafeForAppOpen(): Boolean = intent?.action != Intent.ACTION_VIEW &&
        viewModel.state is PdfOpenState.Idle &&
        viewModel.imagesToPdfState is ImagesToPdfState.Idle &&
        viewModel.mergePdfState is MergePdfState.Idle &&
        viewModel.splitPdfState is SplitPdfState.Idle &&
        viewModel.extractPagesState is ExtractPagesState.Idle &&
        viewModel.deletePagesState is DeletePagesState.Idle &&
        viewModel.rearrangePagesState is RearrangePagesState.Idle &&
        viewModel.rotatePagesState is RotatePagesState.Idle &&
        viewModel.duplicatePagesState is DuplicatePagesState.Idle &&
        viewModel.compressPdfState is CompressPdfState.Idle &&
        viewModel.protectPdfState is ProtectPdfState.Idle &&
        viewModel.removePasswordState is RemovePasswordState.Idle &&
        viewModel.changePasswordState is ChangePasswordState.Idle &&
        viewModel.textWatermarkState is TextWatermarkState.Idle &&
        viewModel.imageWatermarkState is ImageWatermarkState.Idle &&
        viewModel.extractImagesState is ExtractImagesState.Idle &&
        viewModel.fillFormsState is FillFormsState.Idle &&
        viewModel.signPdfState is SignPdfState.Idle &&
        viewModel.annotatePdfState is AnnotatePdfState.Idle &&
        viewModel.scannerCaptureState is ScannerCaptureState.Idle

    private fun finishEligibleWorkflow(clearResult: () -> Unit) {
        fullScreenAds.showInterstitialIfEligible(
            activity = this,
            consentAllowsAds = adsConsentAllowsRequests,
            // Every caller is a user-initiated Result/created-document exit. The document may
            // still be marked Opened until continueNavigation runs, but the reading session is
            // ending and the ad is shown before the safe transition back Home.
            protectedWorkflowActive = false,
            continueNavigation = clearResult,
        )
    }

    private fun <I> launchExternal(launcher: ActivityResultLauncher<I>, input: I) {
        externalTransitionActive = true
        launcher.launch(input)
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

    private fun sharePdf(uri: Uri) {
        val shareIntent = PdfShareIntentFactory.create(this, uri)
        if (shareIntent == null) {
            Toast.makeText(this, R.string.share_pdf_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val readable = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                } catch (_: Exception) {
                    false
                }
            }
            if (!readable) {
                Toast.makeText(this@MainActivity, R.string.share_pdf_unavailable, Toast.LENGTH_LONG).show()
                return@launch
            }
            try {
                externalTransitionActive = true
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_pdf_chooser)))
            } catch (_: RuntimeException) {
                Toast.makeText(this@MainActivity, R.string.share_pdf_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun currentAppLanguageTag(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.toLanguageTag().orEmpty()
    } else {
        getSharedPreferences(LanguagePreferences, Context.MODE_PRIVATE)
            .getString(LanguageTagKey, "").orEmpty()
    }

    private fun changeAppLanguage(languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(languageTag)
        } else {
            getSharedPreferences(LanguagePreferences, Context.MODE_PRIVATE)
                .edit().putString(LanguageTagKey, languageTag).apply()
            recreate()
        }
    }

    private fun openAdvertisingPrivacy() {
        adMobController.showPrivacyOptions(this) {
            Toast.makeText(
                this,
                R.string.settings_advertising_unavailable,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun applyThemeChrome(mode: AppThemeMode) {
        val style = if (mode == AppThemeMode.Dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun changeAppTheme(mode: AppThemeMode) {
        if (mode == selectedThemeMode) return
        themePreferences.write(mode)
        applyThemeChrome(mode)
        selectedThemeMode = mode
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
        const val LanguagePreferences = "quietpdf_language"
        const val LanguageTagKey = "language_tag"
        var processAdCoordinator: FullScreenAdCoordinator? = null
        var processIsForeground = false
        var processSessionActive = false
        var processBackgroundedAtElapsedMillis: Long? = null
        var processFullScreenReturnPending = false
    }
}

private fun PdfHistoryOperation.isInterstitialEligibleWorkflow(): Boolean = when (this) {
    PdfHistoryOperation.ScanDocument,
    PdfHistoryOperation.ImagesToPdf,
    PdfHistoryOperation.MergePdf,
    PdfHistoryOperation.SplitPdf,
    PdfHistoryOperation.RearrangePages,
    PdfHistoryOperation.CompressPdf,
    PdfHistoryOperation.ProtectPdf,
    PdfHistoryOperation.TextWatermark,
    PdfHistoryOperation.ImageWatermark,
    PdfHistoryOperation.ExtractImages,
    PdfHistoryOperation.FillForms,
    PdfHistoryOperation.SignPdf -> true
    else -> false
}

private enum class SmartHomeDestination { Home, Files, Tools, History, Search, Settings }

private enum class SettingsPage { Overview, Language, Privacy, Advertising, About }

data class QuietPdfSettings(
    val themeMode: AppThemeMode = AppThemeMode.Light,
    val onChangeTheme: (AppThemeMode) -> Unit = {},
    val selectedLanguageTag: String = "",
    val onChangeLanguage: (String) -> Unit = {},
    val adPrivacyOptionsRequired: Boolean = false,
    val onOpenAdvertisingPrivacy: () -> Unit = {},
)

private enum class SmartToolCategory { Create, Organize, Optimize, Secure, EditAndSign, Extract }

private enum class SmartFileFilter { All, Recent, Favorites }

private enum class HistoryFilter { All, Create, Organize, Optimize, Secure, EditAndSign, Extract }

private enum class HistorySortOrder { Newest, Oldest }

private data class SmartToolAction(
    val tool: SmartTool,
    val label: String,
    val category: SmartToolCategory,
    val testTag: String,
    val onClick: () -> Unit,
)

@Composable
private fun SmartHomeNavigationBar(
    selected: SmartHomeDestination,
    onSelect: (SmartHomeDestination) -> Unit,
) {
    NavigationBar(modifier = Modifier.testTag("smart_home_bottom_navigation")) {
        SmartHomeDestination.entries.filterNot {
            it == SmartHomeDestination.Search || it == SmartHomeDestination.Settings
        }
            .forEach { destination ->
            val label = smartHomeDestinationLabel(destination)
            NavigationBarItem(
                selected = selected == destination,
                onClick = { if (selected != destination) onSelect(destination) },
                icon = { Text(navigationSymbol(destination)) },
                label = { Text(label) },
                modifier = Modifier.testTag("smart_home_nav_${destination.name}"),
            )
        }
    }
}

@Composable
private fun SmartHomeNavigationRail(
    selected: SmartHomeDestination,
    onSelect: (SmartHomeDestination) -> Unit,
) {
    NavigationRail(modifier = Modifier.testTag("smart_home_navigation_rail")) {
        SmartHomeDestination.entries.filterNot {
            it == SmartHomeDestination.Search || it == SmartHomeDestination.Settings
        }
            .forEach { destination ->
            val label = smartHomeDestinationLabel(destination)
            NavigationRailItem(
                selected = selected == destination,
                onClick = { if (selected != destination) onSelect(destination) },
                icon = { Text(navigationSymbol(destination)) },
                label = { Text(label) },
                modifier = Modifier.testTag("smart_home_nav_${destination.name}"),
            )
        }
    }
}

@Composable
private fun smartHomeDestinationLabel(destination: SmartHomeDestination): String = stringResource(
    when (destination) {
        SmartHomeDestination.Home -> R.string.smart_home_nav_home
        SmartHomeDestination.Files -> R.string.smart_home_nav_files
        SmartHomeDestination.Tools -> R.string.smart_home_nav_tools
        SmartHomeDestination.History -> R.string.smart_home_nav_history
        SmartHomeDestination.Search -> R.string.smart_home_nav_search
        SmartHomeDestination.Settings -> R.string.smart_home_settings
    },
)

private fun navigationSymbol(destination: SmartHomeDestination): String = when (destination) {
    SmartHomeDestination.Home -> "⌂"
    SmartHomeDestination.Files -> "▤"
    SmartHomeDestination.Tools -> "◆"
    SmartHomeDestination.History -> "◷"
    SmartHomeDestination.Search -> "⌕"
    SmartHomeDestination.Settings -> "⚙"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietPdfApp(
    state: PdfOpenState,
    recentPdfs: List<RecentPdf> = emptyList(),
    favoritePdfs: List<FavoritePdf> = emptyList(),
    history: List<PdfHistoryEntry> = emptyList(),
    continueReading: ContinueReadingPdf? = null,
    favoriteTools: List<SmartTool> = emptyList(),
    legacyHomeSections: Boolean = true,
    onOpenRecentPdf: (Uri) -> Unit = {},
    onRemoveRecentPdf: (Uri) -> Unit = {},
    onClearRecentPdfs: () -> Unit = {},
    onOpenFavoritePdf: (Uri) -> Unit = {},
    onToggleFavoritePdf: (Uri) -> Unit = {},
    onRemoveFavoritePdf: (Uri) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onRemoveHistoryEntry: (PdfHistoryEntry) -> Unit = {},
    onToggleFavoriteTool: (SmartTool) -> Unit = {},
    onSharePdf: (Uri) -> Unit = {},
    settings: QuietPdfSettings = QuietPdfSettings(),
    adsCanLoad: Boolean = false,
    homeNativeContent: (@Composable () -> Unit)? = null,
    homeBannerContent: (@Composable () -> Unit)? = null,
    onOpenPdf: () -> Unit,
    onClosePdf: () -> Unit = {},
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
    textWatermarkState: TextWatermarkState = TextWatermarkState.Idle,
    onTextWatermark: () -> Unit = {},
    renderTextWatermarkPreview: suspend (TextWatermarkSettings, Int) -> TextWatermarkPreviewResult = { _, _ ->
        TextWatermarkPreviewResult.Failed
    },
    onConfirmTextWatermark: (TextWatermarkSettings) -> Unit = {},
    onCancelTextWatermark: () -> Unit = {},
    onOpenTextWatermarkedPdf: () -> Unit = {},
    onDismissTextWatermarkResult: () -> Unit = {},
    imageWatermarkState: ImageWatermarkState = ImageWatermarkState.Idle,
    onImageWatermark: () -> Unit = {},
    onChooseWatermarkImage: () -> Unit = {},
    renderImageWatermarkPreview: suspend (ImageWatermarkSettings, Int) -> ImageWatermarkPreviewResult = { _, _ ->
        ImageWatermarkPreviewResult.Failed
    },
    onConfirmImageWatermark: (ImageWatermarkSettings) -> Unit = {},
    onCancelImageWatermark: () -> Unit = {},
    onOpenImageWatermarkedPdf: () -> Unit = {},
    onDismissImageWatermarkResult: () -> Unit = {},
    extractImagesState: ExtractImagesState = ExtractImagesState.Idle,
    onExtractImages: () -> Unit = {},
    onSaveSelectedImages: (Set<Int>) -> Unit = {},
    onSaveAllImages: (Set<Int>) -> Unit = {},
    onExportImagesZip: (Set<Int>) -> Unit = {},
    onShareExtractedImages: (Set<Int>) -> Unit = {},
    onCancelImageExtraction: () -> Unit = {},
    onDismissExtractImagesResult: () -> Unit = {},
    fillFormsState: FillFormsState = FillFormsState.Idle,
    onFillForms: () -> Unit = {},
    onConfirmFormFilling: (List<FormFieldUpdate>) -> Unit = {},
    onCancelFormFilling: () -> Unit = {},
    onOpenFilledFormPdf: () -> Unit = {},
    onDismissFillFormsResult: () -> Unit = {},
    signPdfState: SignPdfState = SignPdfState.Idle,
    onSignPdf: () -> Unit = {},
    onImportSignature: () -> Unit = {},
    renderSignaturePreview: suspend (Bitmap, VisibleSignatureSettings, Int) -> SignPdfPreviewResult = { _, _, _ ->
        SignPdfPreviewResult.Failed
    },
    onConfirmSignature: (Bitmap, VisibleSignatureSettings) -> Unit = { _, _ -> },
    onCancelPdfSigning: () -> Unit = {},
    onOpenSignedPdf: () -> Unit = {},
    onDismissSignPdfResult: () -> Unit = {},
    annotatePdfState: AnnotatePdfState = AnnotatePdfState.Idle,
    onAnnotatePdf: () -> Unit = {},
    renderAnnotationPreview: suspend (List<PdfAnnotationItem>, Int, Int) -> AnnotatePdfPreviewResult = { _, _, _ ->
        AnnotatePdfPreviewResult.Failed
    },
    onConfirmAnnotations: (List<PdfAnnotationItem>) -> Unit = {},
    onCancelPdfAnnotation: () -> Unit = {},
    onOpenAnnotatedPdf: () -> Unit = {},
    onDismissAnnotatePdfResult: () -> Unit = {},
    scannerCaptureState: ScannerCaptureState = ScannerCaptureState.Idle,
    onScanDocument: () -> Unit = {},
    onBeginScannerCapture: () -> File? = { null },
    onScannerCaptureSaved: (File) -> Unit = {},
    onScannerCaptureFailed: (File?) -> Unit = {},
    onScannerCameraUnavailable: () -> Unit = {},
    onRetakeScannerCapture: () -> Unit = {},
    onUpdateScannerCrop: (ScannerCropSelection) -> Unit = {},
    onScannerCropChangeFinished: () -> Unit = {},
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
    val successfulResultIsVisible = splitPdfState is SplitPdfState.Completed ||
        compressPdfState is CompressPdfState.Completed ||
        protectPdfState is ProtectPdfState.Completed ||
        removePasswordState is RemovePasswordState.Completed ||
        changePasswordState is ChangePasswordState.Completed ||
        textWatermarkState is TextWatermarkState.Completed ||
        imageWatermarkState is ImageWatermarkState.Completed ||
        extractImagesState is ExtractImagesState.Completed ||
        fillFormsState is FillFormsState.Completed ||
        signPdfState is SignPdfState.Completed ||
        annotatePdfState is AnnotatePdfState.Completed ||
        scannerCaptureState is ScannerCaptureState.Completed
    val dismissSuccessfulResult = {
        when {
            splitPdfState is SplitPdfState.Completed -> onDismissSplitResult()
            compressPdfState is CompressPdfState.Completed -> onDismissPdfCompressionResult()
            protectPdfState is ProtectPdfState.Completed -> onDismissPdfProtectionResult()
            removePasswordState is RemovePasswordState.Completed -> onDismissPasswordRemovalResult()
            changePasswordState is ChangePasswordState.Completed -> onDismissPasswordChangeResult()
            textWatermarkState is TextWatermarkState.Completed -> onDismissTextWatermarkResult()
            imageWatermarkState is ImageWatermarkState.Completed -> onDismissImageWatermarkResult()
            extractImagesState is ExtractImagesState.Completed -> onDismissExtractImagesResult()
            fillFormsState is FillFormsState.Completed -> onDismissFillFormsResult()
            signPdfState is SignPdfState.Completed -> onDismissSignPdfResult()
            annotatePdfState is AnnotatePdfState.Completed -> onDismissAnnotatePdfResult()
            scannerCaptureState is ScannerCaptureState.Completed -> onDismissScannerResult()
        }
    }
    var destination by rememberSaveable { mutableStateOf(SmartHomeDestination.Home) }
    var destinationHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Overview) }
    val navigateTo: (SmartHomeDestination) -> Unit = { target ->
        if (target != destination) {
            if (target == SmartHomeDestination.Home && successfulResultIsVisible) {
                dismissSuccessfulResult()
            }
            val existingIndex = destinationHistory.indexOf(target.name)
            destinationHistory = if (existingIndex >= 0) {
                destinationHistory.take(existingIndex)
            } else {
                (destinationHistory + destination.name).distinct()
            }
            destination = target
            if (target == SmartHomeDestination.Settings) settingsPage = SettingsPage.Overview
        }
    }
    val navigateBack: () -> Unit = {
        if (destination == SmartHomeDestination.Settings && settingsPage != SettingsPage.Overview) {
            settingsPage = SettingsPage.Overview
        } else if (destinationHistory.isNotEmpty()) {
            destination = SmartHomeDestination.valueOf(destinationHistory.last())
            destinationHistory = destinationHistory.dropLast(1)
        } else if (destination != SmartHomeDestination.Home) {
            destination = SmartHomeDestination.Home
        }
    }
    if (state is PdfOpenState.Opened) {
        PdfReaderScreen(
            document = state,
            onOpenAnother = onOpenPdf,
            onCloseDocument = onClosePdf,
            renderPage = renderPage,
            onPageChanged = onPageChanged,
            searchDocument = searchDocument,
            onToggleBookmark = onToggleBookmark,
            loadTableOfContents = loadTableOfContents,
            inspectHealth = inspectHealth,
            onToggleFavorite = { onToggleFavoritePdf(state.uri) },
            onSharePdf = { onSharePdf(state.uri) },
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
            onCropChangeFinished = onScannerCropChangeFinished,
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

    val operationsAreIdle = imagesToPdfState is ImagesToPdfState.Idle &&
        mergePdfState is MergePdfState.Idle && splitPdfState is SplitPdfState.Idle &&
        extractPagesState is ExtractPagesState.Idle && deletePagesState is DeletePagesState.Idle &&
        rearrangePagesState is RearrangePagesState.Idle && rotatePagesState is RotatePagesState.Idle &&
        duplicatePagesState is DuplicatePagesState.Idle && compressPdfState is CompressPdfState.Idle &&
        protectPdfState is ProtectPdfState.Idle && removePasswordState is RemovePasswordState.Idle &&
        changePasswordState is ChangePasswordState.Idle && textWatermarkState is TextWatermarkState.Idle &&
        imageWatermarkState is ImageWatermarkState.Idle && extractImagesState is ExtractImagesState.Idle &&
        fillFormsState is FillFormsState.Idle && signPdfState is SignPdfState.Idle &&
        annotatePdfState is AnnotatePdfState.Idle && scannerCaptureState is ScannerCaptureState.Idle
    val showBanner = AdPlacementPolicy.showBanner(
        screenAllowsBanner = destination != SmartHomeDestination.Settings &&
            (destination != SmartHomeDestination.Home || successfulResultIsVisible) &&
            (operationsAreIdle || successfulResultIsVisible),
        documentIsClosed = state is PdfOpenState.Idle,
        consentAllowsAds = adsCanLoad,
        isConfigured = homeBannerContent != null,
    )
    val showHomeNative = AdPlacementPolicy.showHomeNative(
        isHome = destination == SmartHomeDestination.Home,
        documentIsClosed = state is PdfOpenState.Idle,
        operationsAreIdle = operationsAreIdle,
        consentAllowsAds = adsCanLoad,
        isConfigured = homeNativeContent != null,
    )
    BackHandler(
        settingsPage != SettingsPage.Overview ||
            destinationHistory.isNotEmpty() || destination != SmartHomeDestination.Home,
    ) {
        navigateBack()
    }
    BackHandler(state is PdfOpenState.Failed) {
        onClosePdf()
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail && destination != SmartHomeDestination.Settings) {
                SmartHomeNavigationRail(
                    selected = destination,
                    onSelect = navigateTo,
                )
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = {
                    if (destination == SmartHomeDestination.Settings) {
                        val settingsBackDescription = stringResource(R.string.settings_back)
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = stringResource(
                                        when (settingsPage) {
                                            SettingsPage.Overview -> R.string.smart_home_settings
                                            SettingsPage.Language -> R.string.settings_language_title
                                            SettingsPage.Privacy -> R.string.settings_privacy_title
                                            SettingsPage.Advertising -> R.string.settings_advertising_title
                                            SettingsPage.About -> R.string.settings_about_title
                                        },
                                    ),
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = navigateBack,
                                    modifier = Modifier.testTag("settings_back_action"),
                                ) {
                                    Text(
                                        text = "←",
                                        style = MaterialTheme.typography.headlineMedium,
                                        modifier = Modifier.semantics {
                                            contentDescription = settingsBackDescription
                                        },
                                    )
                                }
                            },
                        )
                    } else {
                        TopAppBar(
                        title = {
                            Text(stringResource(R.string.app_name))
                        },
                        actions = {
                            val settingsDescription = stringResource(R.string.smart_home_settings)
                            IconButton(
                                onClick = { navigateTo(SmartHomeDestination.Settings) },
                                modifier = Modifier
                                    .semantics { contentDescription = settingsDescription }
                                    .testTag("smart_home_settings_action"),
                            ) {
                                Text(
                                    text = "⚙",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                }
                        },
                    )
                    }
                },
                bottomBar = {
                    Column {
                        if (showBanner) {
                            homeBannerContent?.invoke()
                        }
                        if (!useNavigationRail && destination != SmartHomeDestination.Settings) {
                        SmartHomeNavigationBar(
                            selected = destination,
                            onSelect = navigateTo,
                        )
                        }
                    }
                },
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
                color = if (destination == SmartHomeDestination.Settings) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = if (destination == SmartHomeDestination.Settings) 0.dp else 2.dp,
            ) {
                OpenPdfContent(
                    state = state,
                    destination = destination,
                    legacyHomeSections = legacyHomeSections,
                    recentPdfs = recentPdfs,
                    favoritePdfs = favoritePdfs,
                    history = history,
                    continueReading = continueReading,
                    favoriteTools = favoriteTools,
                    homeNativeContent = homeNativeContent.takeIf { showHomeNative },
                    onOpenRecentPdf = onOpenRecentPdf,
                    onRemoveRecentPdf = onRemoveRecentPdf,
                    onClearRecentPdfs = onClearRecentPdfs,
                    onOpenFavoritePdf = onOpenFavoritePdf,
                    onToggleFavoritePdf = onToggleFavoritePdf,
                    onRemoveFavoritePdf = onRemoveFavoritePdf,
                    onClearHistory = onClearHistory,
                    onRemoveHistoryEntry = onRemoveHistoryEntry,
                    onToggleFavoriteTool = onToggleFavoriteTool,
                    onSharePdf = onSharePdf,
                    onNavigate = navigateTo,
                    settingsPage = settingsPage,
                    onOpenSettingsPage = { settingsPage = it },
                    themeMode = settings.themeMode,
                    onChangeTheme = settings.onChangeTheme,
                    selectedLanguageTag = settings.selectedLanguageTag,
                    onChangeLanguage = settings.onChangeLanguage,
                    adPrivacyOptionsRequired = settings.adPrivacyOptionsRequired,
                    onOpenAdvertisingPrivacy = settings.onOpenAdvertisingPrivacy,
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
                    textWatermarkState = textWatermarkState,
                    onTextWatermark = onTextWatermark,
                    onCancelTextWatermark = onCancelTextWatermark,
                    onOpenTextWatermarkedPdf = onOpenTextWatermarkedPdf,
                    onDismissTextWatermarkResult = onDismissTextWatermarkResult,
                    imageWatermarkState = imageWatermarkState,
                    onImageWatermark = onImageWatermark,
                    onCancelImageWatermark = onCancelImageWatermark,
                    onOpenImageWatermarkedPdf = onOpenImageWatermarkedPdf,
                    onDismissImageWatermarkResult = onDismissImageWatermarkResult,
                    extractImagesState = extractImagesState,
                    onExtractImages = onExtractImages,
                    onCancelImageExtraction = onCancelImageExtraction,
                    onDismissExtractImagesResult = onDismissExtractImagesResult,
                    fillFormsState = fillFormsState,
                    onFillForms = onFillForms,
                    onCancelFormFilling = onCancelFormFilling,
                    onOpenFilledFormPdf = onOpenFilledFormPdf,
                    onDismissFillFormsResult = onDismissFillFormsResult,
                    signPdfState = signPdfState,
                    onSignPdf = onSignPdf,
                    onCancelPdfSigning = onCancelPdfSigning,
                    onOpenSignedPdf = onOpenSignedPdf,
                    onDismissSignPdfResult = onDismissSignPdfResult,
                    annotatePdfState = annotatePdfState,
                    onAnnotatePdf = onAnnotatePdf,
                    onCancelPdfAnnotation = onCancelPdfAnnotation,
                    onOpenAnnotatedPdf = onOpenAnnotatedPdf,
                    onDismissAnnotatePdfResult = onDismissAnnotatePdfResult,
                    scannerCaptureState = scannerCaptureState,
                    onScanDocument = onScanDocument,
                    onOpenScannerPdf = onOpenScannerPdf,
                    onDismissScannerResult = onDismissScannerResult,
                    contentPadding = PaddingValues(24.dp),
                )
            }
                }
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
    if (textWatermarkState is TextWatermarkState.Configuring) {
        TextWatermarkDialog(
            documentName = textWatermarkState.displayName,
            pageCount = textWatermarkState.pageCount,
            renderPreview = renderTextWatermarkPreview,
            onConfirm = onConfirmTextWatermark,
            onCancel = onCancelTextWatermark,
        )
    }
    if (imageWatermarkState is ImageWatermarkState.AwaitingImage) {
        AlertDialog(
            onDismissRequest = onCancelImageWatermark,
            title = { Text(stringResource(R.string.image_watermark_choose_title)) },
            text = {
                Text(stringResource(
                    R.string.image_watermark_choose_message,
                    imageWatermarkState.displayName,
                    imageWatermarkState.pageCount,
                ))
            },
            confirmButton = {
                Button(
                    onClick = onChooseWatermarkImage,
                    modifier = Modifier.testTag("image_watermark_choose_image"),
                ) { Text(stringResource(R.string.choose_image)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelImageWatermark) { Text(stringResource(R.string.cancel)) }
            },
            modifier = Modifier.testTag("image_watermark_choose_dialog"),
        )
    }
    if (imageWatermarkState is ImageWatermarkState.Configuring) {
        ImageWatermarkDialog(
            documentName = imageWatermarkState.displayName,
            pageCount = imageWatermarkState.pageCount,
            imageWidth = imageWatermarkState.imageInfo.width,
            imageHeight = imageWatermarkState.imageInfo.height,
            renderPreview = renderImageWatermarkPreview,
            onConfirm = onConfirmImageWatermark,
            onCancel = onCancelImageWatermark,
        )
    }
    if (extractImagesState is ExtractImagesState.Configuring) {
        ExtractImagesDialog(
            documentName = extractImagesState.displayName,
            images = extractImagesState.analysis.images,
            onSaveSelected = onSaveSelectedImages,
            onSaveAll = onSaveAllImages,
            onExportZip = onExportImagesZip,
            onShare = onShareExtractedImages,
            onCancel = onCancelImageExtraction,
        )
    }
    if (fillFormsState is FillFormsState.Configuring) {
        FillFormsDialog(
            documentName = fillFormsState.displayName,
            analysis = fillFormsState.analysis,
            onConfirm = onConfirmFormFilling,
            onCancel = onCancelFormFilling,
        )
    }
    if (signPdfState is SignPdfState.Configuring) {
        SignPdfDialog(
            documentName = signPdfState.displayName,
            pageCount = signPdfState.pageCount,
            importedSignature = signPdfState.importedSignature,
            onImportSignature = onImportSignature,
            renderPreview = renderSignaturePreview,
            onConfirm = onConfirmSignature,
            onCancel = onCancelPdfSigning,
        )
    }
    if (annotatePdfState is AnnotatePdfState.Configuring) {
        AnnotatePdfDialog(
            documentName = annotatePdfState.displayName,
            pageCount = annotatePdfState.pageCount,
            renderPreview = renderAnnotationPreview,
            onConfirm = onConfirmAnnotations,
            onCancel = onCancelPdfAnnotation,
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
private fun TextWatermarkDialog(
    documentName: String,
    pageCount: Int,
    renderPreview: suspend (TextWatermarkSettings, Int) -> TextWatermarkPreviewResult,
    onConfirm: (TextWatermarkSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var watermarkText by remember(documentName, pageCount) { mutableStateOf("") }
    var pageSelection by remember(documentName, pageCount) { mutableStateOf("") }
    var horizontalPosition by remember(documentName, pageCount) { mutableStateOf(1) }
    var verticalPosition by remember(documentName, pageCount) { mutableStateOf(1) }
    var opacity by remember(documentName, pageCount) { mutableStateOf(0.3f) }
    var rotationIndex by remember(documentName, pageCount) { mutableStateOf(0) }
    var scale by remember(documentName, pageCount) { mutableStateOf(0.1f) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    val selectedPages = remember(pageSelection, pageCount) {
        TextWatermarkPageSelection.parse(pageSelection, pageCount)
    }
    val validSettings = remember(
        watermarkText,
        selectedPages,
        horizontalPosition,
        verticalPosition,
        opacity,
        rotationIndex,
        scale,
        pageCount,
    ) {
        selectedPages?.let {
            TextWatermarkSettings(
                text = watermarkText,
                pageIndices = it,
                position = textWatermarkPosition(verticalPosition, horizontalPosition),
                opacity = opacity,
                rotationDegrees = listOf(-45, 0, 45)[rotationIndex],
                scale = scale,
            )
        }?.takeIf { it.isValid(pageCount) }
    }
    LaunchedEffect(validSettings) {
        if (validSettings == null) {
            previewBitmap?.recycle()
            previewBitmap = null
            previewLoading = false
        } else {
            previewLoading = true
            when (val result = renderPreview(validSettings, 600)) {
                is TextWatermarkPreviewResult.Ready -> {
                    previewBitmap?.takeIf { it !== result.bitmap }?.recycle()
                    previewBitmap = result.bitmap
                }
                else -> {
                    previewBitmap?.recycle()
                    previewBitmap = null
                }
            }
            previewLoading = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { previewBitmap?.recycle() }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.text_watermark_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.text_watermark_document_summary, documentName, pageCount))
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = {
                        if (it.length <= TextWatermarkSettings.MAX_TEXT_LENGTH &&
                            it.none(Char::isISOControl)
                        ) watermarkText = it
                    },
                    label = { Text(stringResource(R.string.watermark_text)) },
                    supportingText = {
                        Text(stringResource(R.string.text_watermark_text_help, TextWatermarkSettings.MAX_TEXT_LENGTH))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("text_watermark_text"),
                )
                OutlinedTextField(
                    value = pageSelection,
                    onValueChange = { pageSelection = it },
                    label = { Text(stringResource(R.string.watermark_pages)) },
                    supportingText = {
                        Text(
                            if (pageSelection.isBlank() || selectedPages != null) {
                                stringResource(R.string.text_watermark_pages_help, pageCount)
                            } else stringResource(R.string.text_watermark_pages_invalid)
                        )
                    },
                    isError = pageSelection.isNotBlank() && selectedPages == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .testTag("text_watermark_pages"),
                )
                LayoutChoiceRow(
                    label = stringResource(R.string.watermark_horizontal_position),
                    options = listOf(
                        stringResource(R.string.position_left),
                        stringResource(R.string.position_center),
                        stringResource(R.string.position_right),
                    ),
                    selectedIndex = horizontalPosition,
                    tagPrefix = "watermark_horizontal",
                    onSelect = { horizontalPosition = it },
                )
                LayoutChoiceRow(
                    label = stringResource(R.string.watermark_vertical_position),
                    options = listOf(
                        stringResource(R.string.position_top),
                        stringResource(R.string.position_middle),
                        stringResource(R.string.position_bottom),
                    ),
                    selectedIndex = verticalPosition,
                    tagPrefix = "watermark_vertical",
                    onSelect = { verticalPosition = it },
                )
                LayoutChoiceRow(
                    label = stringResource(R.string.watermark_rotation),
                    options = listOf("-45°", "0°", "+45°"),
                    selectedIndex = rotationIndex,
                    tagPrefix = "watermark_rotation",
                    onSelect = { rotationIndex = it },
                )
                Text(
                    text = stringResource(R.string.watermark_opacity, (opacity * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.1f..1f,
                    steps = 8,
                    modifier = Modifier.testTag("text_watermark_opacity"),
                )
                Text(
                    text = stringResource(R.string.watermark_scale, (scale * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = 0.05f..0.25f,
                    steps = 3,
                    modifier = Modifier.testTag("text_watermark_scale"),
                )
                Text(
                    text = stringResource(
                        R.string.text_watermark_preview,
                        (selectedPages?.minOrNull() ?: 0) + 1,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 300.dp)
                        .testTag("text_watermark_preview"),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        previewLoading -> CircularProgressIndicator()
                        previewBitmap != null -> Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.text_watermark_preview_description),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Text(
                            text = stringResource(R.string.text_watermark_preview_prompt),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validSettings?.let(onConfirm) },
                enabled = validSettings != null && previewBitmap != null && !previewLoading,
                modifier = Modifier.testTag("text_watermark_confirm"),
            ) {
                Text(stringResource(R.string.save_watermarked_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("text_watermark_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("text_watermark_dialog"),
    )
}

private fun textWatermarkPosition(vertical: Int, horizontal: Int): TextWatermarkPosition =
    when (vertical to horizontal) {
        0 to 0 -> TextWatermarkPosition.TopLeft
        0 to 1 -> TextWatermarkPosition.TopCenter
        0 to 2 -> TextWatermarkPosition.TopRight
        1 to 0 -> TextWatermarkPosition.MiddleLeft
        1 to 2 -> TextWatermarkPosition.MiddleRight
        2 to 0 -> TextWatermarkPosition.BottomLeft
        2 to 1 -> TextWatermarkPosition.BottomCenter
        2 to 2 -> TextWatermarkPosition.BottomRight
        else -> TextWatermarkPosition.Center
    }

@Composable
private fun ImageWatermarkDialog(
    documentName: String,
    pageCount: Int,
    imageWidth: Int,
    imageHeight: Int,
    renderPreview: suspend (ImageWatermarkSettings, Int) -> ImageWatermarkPreviewResult,
    onConfirm: (ImageWatermarkSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var pageSelection by remember(documentName, pageCount) { mutableStateOf("") }
    var horizontal by remember(documentName, pageCount) { mutableStateOf(1) }
    var vertical by remember(documentName, pageCount) { mutableStateOf(1) }
    var opacity by remember(documentName, pageCount) { mutableStateOf(0.5f) }
    var rotation by remember(documentName, pageCount) { mutableStateOf(1) }
    var scale by remember(documentName, pageCount) { mutableStateOf(0.2f) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    val pages = remember(pageSelection, pageCount) {
        ImageWatermarkPageSelection.parse(pageSelection, pageCount)
    }
    val settings = remember(pages, horizontal, vertical, opacity, rotation, scale, pageCount) {
        pages?.let {
            ImageWatermarkSettings(
                pageIndices = it,
                position = imageWatermarkPosition(vertical, horizontal),
                opacity = opacity,
                rotationDegrees = listOf(-45, 0, 45)[rotation],
                scale = scale,
            )
        }?.takeIf { it.isValid(pageCount) }
    }
    LaunchedEffect(settings) {
        if (settings == null) {
            preview?.recycle()
            preview = null
            loading = false
        } else {
            loading = true
            when (val result = renderPreview(settings, 600)) {
                is ImageWatermarkPreviewResult.Ready -> {
                    preview?.takeIf { it !== result.bitmap }?.recycle()
                    preview = result.bitmap
                }
                else -> {
                    preview?.recycle()
                    preview = null
                }
            }
            loading = false
        }
    }
    DisposableEffect(Unit) { onDispose { preview?.recycle() } }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.image_watermark_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(
                    R.string.image_watermark_document_summary,
                    documentName, pageCount, imageWidth, imageHeight,
                ))
                OutlinedTextField(
                    value = pageSelection,
                    onValueChange = { pageSelection = it },
                    label = { Text(stringResource(R.string.watermark_pages)) },
                    supportingText = {
                        Text(if (pageSelection.isBlank() || pages != null) {
                            stringResource(R.string.image_watermark_pages_help, pageCount)
                        } else stringResource(R.string.image_watermark_pages_invalid))
                    },
                    isError = pageSelection.isNotBlank() && pages == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("image_watermark_pages"),
                )
                LayoutChoiceRow(
                    stringResource(R.string.watermark_horizontal_position),
                    listOf(stringResource(R.string.position_left), stringResource(R.string.position_center), stringResource(R.string.position_right)),
                    horizontal, "image_watermark_horizontal", { horizontal = it },
                )
                LayoutChoiceRow(
                    stringResource(R.string.watermark_vertical_position),
                    listOf(stringResource(R.string.position_top), stringResource(R.string.position_middle), stringResource(R.string.position_bottom)),
                    vertical, "image_watermark_vertical", { vertical = it },
                )
                LayoutChoiceRow(
                    stringResource(R.string.watermark_rotation), listOf("-45°", "0°", "+45°"),
                    rotation, "image_watermark_rotation", { rotation = it },
                )
                Text(
                    stringResource(R.string.watermark_opacity, (opacity * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Slider(opacity, { opacity = it }, valueRange = 0.1f..1f, steps = 8,
                    modifier = Modifier.testTag("image_watermark_opacity"))
                Text(
                    stringResource(R.string.watermark_scale, (scale * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(scale, { scale = it }, valueRange = 0.05f..0.5f, steps = 8,
                    modifier = Modifier.testTag("image_watermark_scale"))
                Text(
                    stringResource(R.string.image_watermark_preview, (pages?.minOrNull() ?: 0) + 1),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 300.dp)
                        .testTag("image_watermark_preview"),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        preview != null -> Image(
                            bitmap = preview!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.image_watermark_preview_description),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Text(
                            stringResource(R.string.image_watermark_preview_prompt),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { settings?.let(onConfirm) },
                enabled = settings != null && preview != null && !loading,
                modifier = Modifier.testTag("image_watermark_confirm"),
            ) { Text(stringResource(R.string.save_watermarked_pdf)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("image_watermark_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("image_watermark_dialog"),
    )
}

private fun imageWatermarkPosition(vertical: Int, horizontal: Int): ImageWatermarkPosition =
    when (vertical to horizontal) {
        0 to 0 -> ImageWatermarkPosition.TopLeft
        0 to 1 -> ImageWatermarkPosition.TopCenter
        0 to 2 -> ImageWatermarkPosition.TopRight
        1 to 0 -> ImageWatermarkPosition.MiddleLeft
        1 to 2 -> ImageWatermarkPosition.MiddleRight
        2 to 0 -> ImageWatermarkPosition.BottomLeft
        2 to 1 -> ImageWatermarkPosition.BottomCenter
        2 to 2 -> ImageWatermarkPosition.BottomRight
        else -> ImageWatermarkPosition.Center
    }

@Composable
private fun ExtractImagesDialog(
    documentName: String,
    images: List<EmbeddedImagePreview>,
    onSaveSelected: (Set<Int>) -> Unit,
    onSaveAll: (Set<Int>) -> Unit,
    onExportZip: (Set<Int>) -> Unit,
    onShare: (Set<Int>) -> Unit,
    onCancel: () -> Unit,
) {
    val safeIndices: Set<Int> = remember(images) {
        images.filter(EmbeddedImagePreview::extractable).mapTo(linkedSetOf()) { it.index }
    }
    var selected by remember(images) { mutableStateOf<Set<Int>>(safeIndices) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.extract_images_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.extract_images_summary, documentName, images.size))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { selected = safeIndices },
                        modifier = Modifier.weight(1f).testTag("extract_images_select_all"),
                    ) { Text(stringResource(R.string.extract_images_select_all)) }
                    TextButton(
                        onClick = { selected = emptySet() },
                        modifier = Modifier.weight(1f).testTag("extract_images_clear"),
                    ) { Text(stringResource(R.string.extract_images_clear_selection)) }
                }
                images.forEach { image ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            .then(if (image.extractable) Modifier.clickable {
                                selected = if (image.index in selected) selected - image.index
                                else selected + image.index
                            } else Modifier)
                            .testTag("extract_image_${image.index}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = image.index in selected,
                            onCheckedChange = if (image.extractable) { checked ->
                                selected = if (checked) selected + image.index else selected - image.index
                            } else null,
                        )
                        Box(
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            image.bitmap?.let { preview ->
                                Image(
                                    bitmap = preview.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(stringResource(
                                R.string.extract_images_item,
                                image.pageNumber,
                                image.width,
                                image.height,
                            ))
                            if (!image.extractable) {
                                Text(
                                    stringResource(R.string.extract_images_too_large),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { onSaveSelected(selected) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("extract_images_save_selected"),
                ) { Text(stringResource(R.string.extract_images_save_selected)) }
                TextButton(
                    onClick = { onSaveAll(safeIndices) },
                    enabled = safeIndices.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("extract_images_save_all"),
                ) { Text(stringResource(R.string.extract_images_save_all)) }
                TextButton(
                    onClick = { onExportZip(selected) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("extract_images_zip"),
                ) { Text(stringResource(R.string.extract_images_zip_selected)) }
                TextButton(
                    onClick = { onShare(selected) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("extract_images_share"),
                ) { Text(stringResource(R.string.extract_images_share_selected)) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("extract_images_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("extract_images_dialog"),
    )
}

@Composable
private fun FillFormsDialog(
    documentName: String,
    analysis: FillFormsAnalysis,
    onConfirm: (List<FormFieldUpdate>) -> Unit,
    onCancel: () -> Unit,
) {
    var values by remember(analysis) {
        mutableStateOf(analysis.fields.associate { it.id to it.values })
    }
    val writableFields = analysis.fields.filterNot(FormFieldDescriptor::readOnly)
    val changedFields = writableFields.filter { field -> values[field.id].orEmpty() != field.values }
    val valid = changedFields.isNotEmpty() && writableFields.all { field ->
        validFormValue(field, values[field.id].orEmpty())
    }
    fun update(field: FormFieldDescriptor, newValues: List<String>) {
        values = values + (field.id to newValues)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.fill_forms_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(
                    R.string.fill_forms_summary,
                    documentName,
                    analysis.fields.size,
                ))
                if (analysis.unsupportedFieldCount > 0) {
                    Text(
                        stringResource(
                            R.string.fill_forms_unsupported_count,
                            analysis.unsupportedFieldCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                analysis.fields.forEachIndexed { index, field ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                            .testTag("form_field_$index"),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(field.label, fontWeight = FontWeight.SemiBold)
                            field.pageNumber?.let { page ->
                                Text(
                                    stringResource(R.string.fill_forms_page, page),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (field.required || field.readOnly) {
                            Text(
                                buildString {
                                    if (field.required) append(stringResource(R.string.fill_forms_required))
                                    if (field.required && field.readOnly) append(" · ")
                                    if (field.readOnly) append(stringResource(R.string.fill_forms_read_only))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when (field.kind) {
                            FormFieldKind.Text -> OutlinedTextField(
                                value = values[field.id]?.singleOrNull().orEmpty(),
                                onValueChange = { candidate ->
                                    if (candidate.none(Char::isISOControl) &&
                                        (field.maxLength <= 0 || candidate.length <= field.maxLength)
                                    ) update(field, listOf(candidate))
                                },
                                enabled = !field.readOnly,
                                label = { Text(stringResource(R.string.fill_forms_text_value)) },
                                minLines = if (field.multiline) 3 else 1,
                                maxLines = if (field.multiline) 6 else 1,
                                visualTransformation = if (field.password) {
                                    PasswordVisualTransformation()
                                } else androidx.compose.ui.text.input.VisualTransformation.None,
                                isError = !field.readOnly && !validFormValue(
                                    field, values[field.id].orEmpty(),
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("form_text_$index"),
                            )
                            FormFieldKind.CheckBox -> Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Switch(
                                    checked = values[field.id]?.singleOrNull() == FillFormsEngine.TRUE_VALUE,
                                    onCheckedChange = if (!field.readOnly) { checked ->
                                        update(field, listOf(if (checked) {
                                            FillFormsEngine.TRUE_VALUE
                                        } else FillFormsEngine.FALSE_VALUE))
                                    } else null,
                                    modifier = Modifier.testTag("form_checkbox_$index"),
                                )
                                Text(
                                    stringResource(R.string.fill_forms_checked),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            FormFieldKind.Radio, FormFieldKind.ComboBox, FormFieldKind.ListBox -> {
                                if (field.kind == FormFieldKind.ComboBox && field.editableChoice) {
                                    OutlinedTextField(
                                        value = values[field.id]?.singleOrNull().orEmpty(),
                                        onValueChange = { candidate ->
                                            if (candidate.none(Char::isISOControl)) {
                                                update(field, listOf(candidate))
                                            }
                                        },
                                        enabled = !field.readOnly,
                                        label = { Text(stringResource(R.string.fill_forms_text_value)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("form_choice_text_$index"),
                                    )
                                } else {
                                    field.options.forEachIndexed { optionIndex, option ->
                                        val optionValue = field.optionValues[optionIndex]
                                        val selected = optionValue in values[field.id].orEmpty()
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                if (!field.readOnly) {
                                                    val next = if (field.kind == FormFieldKind.ListBox &&
                                                        field.multiSelect
                                                    ) {
                                                        if (selected) values[field.id].orEmpty() - optionValue
                                                        else values[field.id].orEmpty() + optionValue
                                                    } else listOf(optionValue)
                                                    update(field, next)
                                                }
                                            },
                                            enabled = !field.readOnly,
                                            label = { Text(option) },
                                            modifier = Modifier.padding(end = 6.dp)
                                                .testTag("form_option_${index}_$optionIndex"),
                                        )
                                    }
                                    if (!field.required && field.kind != FormFieldKind.ListBox &&
                                        !field.readOnly
                                    ) {
                                        TextButton(
                                            onClick = { update(field, listOf("")) },
                                            modifier = Modifier.testTag("form_clear_$index"),
                                        ) { Text(stringResource(R.string.fill_forms_clear)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(changedFields.map { field ->
                        FormFieldUpdate(field.id, values[field.id].orEmpty())
                    })
                },
                enabled = valid,
                modifier = Modifier.testTag("fill_forms_save"),
            ) { Text(stringResource(R.string.fill_forms_save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("fill_forms_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("fill_forms_dialog"),
    )
}

private fun validFormValue(field: FormFieldDescriptor, values: List<String>): Boolean = when (field.kind) {
    FormFieldKind.Text -> values.size == 1 &&
        (field.maxLength <= 0 || values.single().length <= field.maxLength) &&
        (!field.required || values.single().isNotBlank()) && values.single().none(Char::isISOControl)
    FormFieldKind.CheckBox -> values.size == 1 &&
        values.single() in setOf(FillFormsEngine.TRUE_VALUE, FillFormsEngine.FALSE_VALUE) &&
        (!field.required || values.single() == FillFormsEngine.TRUE_VALUE)
    FormFieldKind.Radio -> values.size == 1 &&
        (values.single().isEmpty() && !field.required || values.single() in field.optionValues)
    FormFieldKind.ComboBox -> values.size == 1 &&
        (values.single().isEmpty() && !field.required ||
            field.editableChoice || values.single() in field.optionValues) &&
        (!field.required || values.single().isNotBlank()) && values.single().none(Char::isISOControl)
    FormFieldKind.ListBox -> values.distinct().size == values.size &&
        (field.multiSelect || values.size <= 1) && values.all { it in field.optionValues } &&
        (!field.required || values.isNotEmpty())
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
    destination: SmartHomeDestination,
    legacyHomeSections: Boolean,
    recentPdfs: List<RecentPdf>,
    favoritePdfs: List<FavoritePdf>,
    history: List<PdfHistoryEntry>,
    continueReading: ContinueReadingPdf?,
    favoriteTools: List<SmartTool>,
    homeNativeContent: (@Composable () -> Unit)?,
    onOpenRecentPdf: (Uri) -> Unit,
    onRemoveRecentPdf: (Uri) -> Unit,
    onClearRecentPdfs: () -> Unit,
    onOpenFavoritePdf: (Uri) -> Unit,
    onToggleFavoritePdf: (Uri) -> Unit,
    onRemoveFavoritePdf: (Uri) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveHistoryEntry: (PdfHistoryEntry) -> Unit,
    onToggleFavoriteTool: (SmartTool) -> Unit,
    onSharePdf: (Uri) -> Unit,
    onNavigate: (SmartHomeDestination) -> Unit,
    settingsPage: SettingsPage,
    onOpenSettingsPage: (SettingsPage) -> Unit,
    themeMode: AppThemeMode,
    onChangeTheme: (AppThemeMode) -> Unit,
    selectedLanguageTag: String,
    onChangeLanguage: (String) -> Unit,
    adPrivacyOptionsRequired: Boolean,
    onOpenAdvertisingPrivacy: () -> Unit,
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
    textWatermarkState: TextWatermarkState,
    onTextWatermark: () -> Unit,
    onCancelTextWatermark: () -> Unit,
    onOpenTextWatermarkedPdf: () -> Unit,
    onDismissTextWatermarkResult: () -> Unit,
    imageWatermarkState: ImageWatermarkState,
    onImageWatermark: () -> Unit,
    onCancelImageWatermark: () -> Unit,
    onOpenImageWatermarkedPdf: () -> Unit,
    onDismissImageWatermarkResult: () -> Unit,
    extractImagesState: ExtractImagesState,
    onExtractImages: () -> Unit,
    onCancelImageExtraction: () -> Unit,
    onDismissExtractImagesResult: () -> Unit,
    fillFormsState: FillFormsState,
    onFillForms: () -> Unit,
    onCancelFormFilling: () -> Unit,
    onOpenFilledFormPdf: () -> Unit,
    onDismissFillFormsResult: () -> Unit,
    signPdfState: SignPdfState,
    onSignPdf: () -> Unit,
    onCancelPdfSigning: () -> Unit,
    onOpenSignedPdf: () -> Unit,
    onDismissSignPdfResult: () -> Unit,
    annotatePdfState: AnnotatePdfState,
    onAnnotatePdf: () -> Unit,
    onCancelPdfAnnotation: () -> Unit,
    onOpenAnnotatedPdf: () -> Unit,
    onDismissAnnotatePdfResult: () -> Unit,
    scannerCaptureState: ScannerCaptureState,
    onScanDocument: () -> Unit,
    onOpenScannerPdf: () -> Unit,
    onDismissScannerResult: () -> Unit,
    contentPadding: PaddingValues,
) {
    val homeScroll = rememberScrollState()
    val filesScroll = rememberScrollState()
    val toolsScroll = rememberScrollState()
    val historyScroll = rememberScrollState()
    val searchScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val contentScroll = when (destination) {
        SmartHomeDestination.Home -> homeScroll
        SmartHomeDestination.Files -> filesScroll
        SmartHomeDestination.Tools -> toolsScroll
        SmartHomeDestination.History -> historyScroll
        SmartHomeDestination.Search -> searchScroll
        SmartHomeDestination.Settings -> settingsScroll
    }
    LaunchedEffect(destination, settingsPage) {
        if (destination == SmartHomeDestination.Settings) contentScroll.scrollTo(0)
    }
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
        || textWatermarkState is TextWatermarkState.Failed ||
        textWatermarkState is TextWatermarkState.Completed
        || imageWatermarkState is ImageWatermarkState.Failed ||
        imageWatermarkState is ImageWatermarkState.Completed
        || extractImagesState is ExtractImagesState.Failed ||
        extractImagesState is ExtractImagesState.Completed
        || fillFormsState is FillFormsState.Failed || fillFormsState is FillFormsState.Completed
        || signPdfState is SignPdfState.Failed || signPdfState is SignPdfState.Completed
        || annotatePdfState is AnnotatePdfState.Failed || annotatePdfState is AnnotatePdfState.Completed
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
        when (textWatermarkState) {
            TextWatermarkState.Preparing -> {
                OperationProgressContent(R.string.text_watermark_preparing)
                return@Column
            }
            is TextWatermarkState.Applying -> {
                OperationProgressContent(
                    messageResource = R.string.text_watermark_applying,
                    argument = textWatermarkState.selectedPageCount,
                    onCancel = onCancelTextWatermark,
                )
                return@Column
            }
            else -> Unit
        }
        when (imageWatermarkState) {
            ImageWatermarkState.PreparingPdf -> {
                OperationProgressContent(R.string.image_watermark_preparing_pdf)
                return@Column
            }
            ImageWatermarkState.PreparingImage -> {
                OperationProgressContent(R.string.image_watermark_preparing_image)
                return@Column
            }
            is ImageWatermarkState.Applying -> {
                OperationProgressContent(
                    messageResource = R.string.image_watermark_applying,
                    argument = imageWatermarkState.selectedPageCount,
                    onCancel = onCancelImageWatermark,
                )
                return@Column
            }
            else -> Unit
        }
        when (extractImagesState) {
            ExtractImagesState.Preparing -> {
                OperationProgressContent(R.string.extract_images_preparing)
                return@Column
            }
            is ExtractImagesState.Exporting -> {
                OperationProgressContent(
                    R.string.extract_images_exporting,
                    argument = extractImagesState.imageCount,
                    onCancel = onCancelImageExtraction,
                )
                return@Column
            }
            is ExtractImagesState.PreparingShare -> {
                OperationProgressContent(
                    R.string.extract_images_preparing_share,
                    argument = extractImagesState.imageCount,
                    onCancel = onCancelImageExtraction,
                )
                return@Column
            }
            else -> Unit
        }
        when (fillFormsState) {
            FillFormsState.Preparing -> {
                OperationProgressContent(R.string.fill_forms_preparing)
                return@Column
            }
            is FillFormsState.Saving -> {
                OperationProgressContent(
                    R.string.fill_forms_saving,
                    argument = fillFormsState.fieldCount,
                    onCancel = onCancelFormFilling,
                )
                return@Column
            }
            else -> Unit
        }
        when (signPdfState) {
            SignPdfState.PreparingPdf -> {
                OperationProgressContent(R.string.sign_pdf_preparing)
                return@Column
            }
            SignPdfState.PreparingImage -> {
                OperationProgressContent(R.string.sign_pdf_preparing_image)
                return@Column
            }
            SignPdfState.Saving -> {
                OperationProgressContent(
                    R.string.sign_pdf_saving,
                    onCancel = onCancelPdfSigning,
                )
                return@Column
            }
            else -> Unit
        }
        when (annotatePdfState) {
            AnnotatePdfState.Preparing -> {
                OperationProgressContent(R.string.annotate_pdf_preparing)
                return@Column
            }
            AnnotatePdfState.Saving -> {
                OperationProgressContent(
                    R.string.annotate_pdf_saving,
                    onCancel = onCancelPdfAnnotation,
                )
                return@Column
            }
            else -> Unit
        }
        when (state) {
            PdfOpenState.Idle -> IdleContent(
                destination = destination,
                legacyHomeSections = legacyHomeSections,
                onOpenPdf = onOpenPdf,
                recentPdfs = recentPdfs,
                favoritePdfs = favoritePdfs,
                history = history,
                continueReading = continueReading,
                favoriteTools = favoriteTools,
                homeNativeContent = homeNativeContent,
                onOpenRecentPdf = onOpenRecentPdf,
                onRemoveRecentPdf = onRemoveRecentPdf,
                onClearRecentPdfs = onClearRecentPdfs,
                onOpenFavoritePdf = onOpenFavoritePdf,
                onToggleFavoritePdf = onToggleFavoritePdf,
                onRemoveFavoritePdf = onRemoveFavoritePdf,
                onClearHistory = onClearHistory,
                onRemoveHistoryEntry = onRemoveHistoryEntry,
                onToggleFavoriteTool = onToggleFavoriteTool,
                onSharePdf = onSharePdf,
                onNavigate = onNavigate,
                settingsPage = settingsPage,
                onOpenSettingsPage = onOpenSettingsPage,
                themeMode = themeMode,
                onChangeTheme = onChangeTheme,
                selectedLanguageTag = selectedLanguageTag,
                onChangeLanguage = onChangeLanguage,
                adPrivacyOptionsRequired = adPrivacyOptionsRequired,
                onOpenAdvertisingPrivacy = onOpenAdvertisingPrivacy,
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
                textWatermarkState = textWatermarkState,
                onTextWatermark = onTextWatermark,
                onOpenTextWatermarkedPdf = onOpenTextWatermarkedPdf,
                onDismissTextWatermarkResult = onDismissTextWatermarkResult,
                imageWatermarkState = imageWatermarkState,
                onImageWatermark = onImageWatermark,
                onOpenImageWatermarkedPdf = onOpenImageWatermarkedPdf,
                onDismissImageWatermarkResult = onDismissImageWatermarkResult,
                extractImagesState = extractImagesState,
                onExtractImages = onExtractImages,
                onDismissExtractImagesResult = onDismissExtractImagesResult,
                fillFormsState = fillFormsState,
                onFillForms = onFillForms,
                onOpenFilledFormPdf = onOpenFilledFormPdf,
                onDismissFillFormsResult = onDismissFillFormsResult,
                signPdfState = signPdfState,
                onSignPdf = onSignPdf,
                onOpenSignedPdf = onOpenSignedPdf,
                onDismissSignPdfResult = onDismissSignPdfResult,
                annotatePdfState = annotatePdfState,
                onAnnotatePdf = onAnnotatePdf,
                onOpenAnnotatedPdf = onOpenAnnotatedPdf,
                onDismissAnnotatePdfResult = onDismissAnnotatePdfResult,
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
private fun RecentFilesSection(
    recentPdfs: List<RecentPdf>,
    showAll: Boolean,
    favoriteUris: Set<Uri>,
    onOpen: (Uri) -> Unit,
    onToggleFavorite: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
    onRemove: (Uri) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.recent_files),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("recent_files_title"),
        )
        TextButton(
            onClick = { confirmClear = true },
            modifier = Modifier.testTag("recent_files_clear"),
        ) { Text(stringResource(R.string.recent_files_clear)) }
    }
    val displayed = if (showAll) recentPdfs else recentPdfs.take(5)
    displayed.forEachIndexed { index, recent ->
        val name = recent.displayName ?: stringResource(R.string.recent_file_unnamed)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Button(
                onClick = { onOpen(recent.uri) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("recent_file_$index"),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.recent_file_pages,
                            recent.pageCount,
                            recent.pageCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { onToggleFavorite(recent.uri) },
                    modifier = Modifier.testTag("recent_file_favorite_$index"),
                ) {
                    Text(
                        stringResource(
                            if (recent.uri in favoriteUris) R.string.favorite_file_remove
                            else R.string.favorite_file_add,
                        ),
                    )
                }
                TextButton(
                    onClick = { onShare(recent.uri) },
                    modifier = Modifier.testTag("recent_file_share_$index"),
                ) { Text(stringResource(R.string.share_pdf)) }
                TextButton(
                    onClick = { onRemove(recent.uri) },
                    modifier = Modifier.testTag("recent_file_remove_$index"),
                ) { Text(stringResource(R.string.recent_file_remove)) }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.recent_files_clear_title)) },
            text = { Text(stringResource(R.string.recent_files_clear_message)) },
            confirmButton = {
                Button(
                    onClick = { confirmClear = false; onClear() },
                    modifier = Modifier.testTag("recent_files_clear_confirm"),
                ) { Text(stringResource(R.string.recent_files_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            modifier = Modifier.testTag("recent_files_clear_dialog"),
        )
    }
}

@Composable
private fun FavoriteFilesSection(
    favoritePdfs: List<FavoritePdf>,
    onOpen: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
    onRemove: (Uri) -> Unit,
) {
    Text(
        text = stringResource(R.string.favorite_files),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).testTag("favorite_files_title"),
    )
    favoritePdfs.forEachIndexed { index, favorite ->
        val name = favorite.displayName ?: stringResource(R.string.recent_file_unnamed)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Button(
                onClick = { onOpen(favorite.uri) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("favorite_file_$index"),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = pluralStringResource(
                            R.plurals.recent_file_pages,
                            favorite.pageCount,
                            favorite.pageCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { onShare(favorite.uri) },
                    modifier = Modifier.testTag("favorite_file_share_$index"),
                ) { Text(stringResource(R.string.share_pdf)) }
                TextButton(
                    onClick = { onRemove(favorite.uri) },
                    modifier = Modifier.testTag("favorite_file_remove_$index"),
                ) { Text(stringResource(R.string.favorite_file_remove)) }
            }
        }
    }
}

@Composable
private fun FileOrganizerControls(
    query: String,
    onQueryChange: (String) -> Unit,
    sortOrder: PdfFileSortOrder,
    onSortOrderChange: (PdfFileSortOrder) -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    val sortLabel = stringResource(
        when (sortOrder) {
            PdfFileSortOrder.Newest -> R.string.file_sort_newest
            PdfFileSortOrder.Oldest -> R.string.file_sort_oldest
            PdfFileSortOrder.NameAscending -> R.string.file_sort_name_ascending
            PdfFileSortOrder.NameDescending -> R.string.file_sort_name_descending
            PdfFileSortOrder.PageCountAscending -> R.string.file_sort_pages_ascending
            PdfFileSortOrder.PageCountDescending -> R.string.file_sort_pages_descending
        },
    )
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.file_search)) },
            placeholder = { Text(stringResource(R.string.file_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("file_search_field"),
        )
        Box(modifier = Modifier.align(Alignment.End)) {
            TextButton(
                onClick = { sortExpanded = true },
                modifier = Modifier.testTag("file_sort_button"),
            ) { Text(stringResource(R.string.file_sort_selected, sortLabel)) }
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
            ) {
                PdfFileSortOrder.entries.forEach { option ->
                    val label = stringResource(
                        when (option) {
                            PdfFileSortOrder.Newest -> R.string.file_sort_newest
                            PdfFileSortOrder.Oldest -> R.string.file_sort_oldest
                            PdfFileSortOrder.NameAscending -> R.string.file_sort_name_ascending
                            PdfFileSortOrder.NameDescending -> R.string.file_sort_name_descending
                            PdfFileSortOrder.PageCountAscending -> R.string.file_sort_pages_ascending
                            PdfFileSortOrder.PageCountDescending -> R.string.file_sort_pages_descending
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            sortExpanded = false
                            onSortOrderChange(option)
                        },
                        modifier = Modifier
                            .semantics { selected = option == sortOrder }
                            .testTag("file_sort_${option.name}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySection(
    history: List<PdfHistoryEntry>,
    onClear: () -> Unit,
    onRemove: (PdfHistoryEntry) -> Unit = {},
    onRepeat: (PdfHistoryOperation) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<PdfHistoryEntry?>(null) }
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.All) }
    var sortOrder by rememberSaveable { mutableStateOf(HistorySortOrder.Newest) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.history),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("history_title"),
        )
        TextButton(
            onClick = { confirmClear = true },
            modifier = Modifier.testTag("history_clear"),
        ) { Text(stringResource(R.string.history_clear)) }
    }
    Text(
        text = pluralStringResource(R.plurals.history_entry_count, history.size, history.size),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("history_count"),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryFilter.entries.forEach { option ->
            FilterChip(
                selected = filter == option,
                onClick = { filter = option; expanded = false },
                label = { Text(historyFilterLabel(option)) },
                modifier = Modifier.testTag("history_filter_${option.name}"),
            )
        }
    }
    TextButton(
        onClick = {
            sortOrder = if (sortOrder == HistorySortOrder.Newest) {
                HistorySortOrder.Oldest
            } else {
                HistorySortOrder.Newest
            }
            expanded = false
        },
        modifier = Modifier.fillMaxWidth().testTag("history_sort"),
    ) {
        Text(
            stringResource(
                if (sortOrder == HistorySortOrder.Newest) R.string.history_sort_newest
                else R.string.history_sort_oldest,
            ),
        )
    }
    val filtered = history.filter { entry ->
        filter == HistoryFilter.All || entry.operation.historyFilter() == filter
    }.let { entries ->
        if (sortOrder == HistorySortOrder.Newest) {
            entries.sortedByDescending(PdfHistoryEntry::completedEpochMillis)
        } else {
            entries.sortedBy(PdfHistoryEntry::completedEpochMillis)
        }
    }
    val displayed = if (expanded) filtered else filtered.take(5)
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    if (filtered.isEmpty()) {
        Text(
            text = stringResource(R.string.history_filter_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                .testTag("history_filter_empty"),
        )
    }
    displayed.forEachIndexed { index, entry ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("history_item_$index"),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(entry.operation.labelResource),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(
                        R.string.history_completed,
                        formatter.format(Date(entry.completedEpochMillis)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { onRepeat(entry.operation) },
                        modifier = Modifier.testTag("history_repeat_$index"),
                    ) { Text(stringResource(R.string.history_run_again)) }
                    TextButton(
                        onClick = { pendingRemoval = entry },
                        modifier = Modifier.testTag("history_remove_$index"),
                    ) { Text(stringResource(R.string.history_remove)) }
                }
            }
        }
    }
    if (filtered.size > 5) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("history_expand"),
        ) {
            Text(
                stringResource(
                    if (expanded) R.string.history_show_less else R.string.history_show_all,
                ),
            )
        }
    }
    pendingRemoval?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.history_remove_title)) },
            text = { Text(stringResource(R.string.history_remove_message)) },
            confirmButton = {
                Button(
                    onClick = { pendingRemoval = null; onRemove(entry) },
                    modifier = Modifier.testTag("history_remove_confirm"),
                ) { Text(stringResource(R.string.history_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            modifier = Modifier.testTag("history_remove_dialog"),
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message)) },
            confirmButton = {
                Button(
                    onClick = { confirmClear = false; onClear() },
                    modifier = Modifier.testTag("history_clear_confirm"),
                ) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            modifier = Modifier.testTag("history_clear_dialog"),
        )
    }
}

@Composable
private fun historyFilterLabel(filter: HistoryFilter): String = stringResource(
    when (filter) {
        HistoryFilter.All -> R.string.smart_home_category_all
        HistoryFilter.Create -> R.string.smart_home_category_create
        HistoryFilter.Organize -> R.string.smart_home_category_organize
        HistoryFilter.Optimize -> R.string.smart_home_category_optimize
        HistoryFilter.Secure -> R.string.smart_home_category_secure
        HistoryFilter.EditAndSign -> R.string.smart_home_category_edit
        HistoryFilter.Extract -> R.string.smart_home_category_extract
    },
)

private fun PdfHistoryOperation.historyFilter(): HistoryFilter = when (this) {
    PdfHistoryOperation.ScanDocument, PdfHistoryOperation.ImagesToPdf -> HistoryFilter.Create
    PdfHistoryOperation.MergePdf, PdfHistoryOperation.SplitPdf, PdfHistoryOperation.ExtractPages,
    PdfHistoryOperation.DeletePages, PdfHistoryOperation.RearrangePages,
    PdfHistoryOperation.RotatePages, PdfHistoryOperation.DuplicatePages -> HistoryFilter.Organize
    PdfHistoryOperation.CompressPdf -> HistoryFilter.Optimize
    PdfHistoryOperation.ProtectPdf, PdfHistoryOperation.RemovePassword,
    PdfHistoryOperation.ChangePassword -> HistoryFilter.Secure
    PdfHistoryOperation.TextWatermark, PdfHistoryOperation.ImageWatermark,
    PdfHistoryOperation.FillForms, PdfHistoryOperation.SignPdf,
    PdfHistoryOperation.AnnotatePdf -> HistoryFilter.EditAndSign
    PdfHistoryOperation.ExtractImages -> HistoryFilter.Extract
}

@Composable
private fun SmartHomeDashboard(
    continueReading: ContinueReadingPdf?,
    recentPdfs: List<RecentPdf>,
    favoritePdfs: List<FavoritePdf>,
    history: List<PdfHistoryEntry>,
    favoriteTools: List<SmartTool>,
    homeNativeContent: (@Composable () -> Unit)?,
    tools: List<SmartToolAction>,
    onOpenPdf: () -> Unit,
    onScanDocument: () -> Unit,
    onResume: (Uri) -> Unit,
    onOpenRecent: (Uri) -> Unit,
    onSharePdf: (Uri) -> Unit,
    onToggleFavoritePdf: (Uri) -> Unit,
    onRemoveRecentPdf: (Uri) -> Unit,
    onToggleFavoriteTool: (SmartTool) -> Unit,
    onNavigate: (SmartHomeDestination) -> Unit,
) {
    SearchPdfBanner(
        onClick = { onNavigate(SmartHomeDestination.Search) },
        modifier = Modifier.testTag("smart_home_search_action"),
    )
    Spacer(Modifier.height(16.dp))
    val privacyDescription = stringResource(R.string.smart_home_privacy_description)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("smart_home_privacy"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "🛡",
                modifier = Modifier.semantics {
                    contentDescription = privacyDescription
                },
            )
            Text(
                text = stringResource(R.string.smart_home_privacy),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    Text(
        text = stringResource(R.string.ad_privacy_disclosure),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            .testTag("ad_privacy_disclosure"),
    )
    Spacer(Modifier.height(16.dp))
    if (continueReading != null) {
        var actionsExpanded by remember(continueReading.uri) { mutableStateOf(false) }
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("continue_reading_card"),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.smart_home_continue_reading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = continueReading.displayName ?: stringResource(R.string.recent_file_unnamed),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.smart_home_page_progress,
                        continueReading.currentPageIndex + 1,
                        continueReading.pageCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = {
                        (continueReading.currentPageIndex + 1).toFloat() / continueReading.pageCount
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        .testTag("continue_reading_progress"),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box {
                        TextButton(
                            onClick = { actionsExpanded = true },
                            modifier = Modifier.testTag("continue_reading_actions"),
                        ) { Text(stringResource(R.string.smart_home_file_actions)) }
                        DropdownMenu(
                            expanded = actionsExpanded,
                            onDismissRequest = { actionsExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_pdf)) },
                                onClick = { actionsExpanded = false; onSharePdf(continueReading.uri) },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (favoritePdfs.any { it.uri == continueReading.uri }) {
                                                R.string.favorite_file_remove
                                            } else R.string.favorite_file_add,
                                        ),
                                    )
                                },
                                onClick = {
                                    actionsExpanded = false
                                    onToggleFavoritePdf(continueReading.uri)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recent_file_remove)) },
                                onClick = {
                                    actionsExpanded = false
                                    onRemoveRecentPdf(continueReading.uri)
                                },
                            )
                        }
                    }
                    Button(
                        onClick = { onResume(continueReading.uri) },
                        modifier = Modifier.testTag("continue_reading_resume"),
                    ) { Text(stringResource(R.string.smart_home_resume)) }
                }
            }
        }
    } else {
        Text(
            text = stringResource(R.string.smart_home_new_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().testTag("smart_home_new_user"),
        )
        Text(
            text = stringResource(R.string.smart_home_new_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
    SmartHomePrimaryActions(onOpenPdf = onOpenPdf, onScanDocument = onScanDocument)

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.smart_home_recent),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("smart_home_recent_title"),
            )
            Text(
                text = pluralStringResource(
                    R.plurals.smart_home_recent_count,
                    recentPdfs.size,
                    recentPdfs.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("smart_home_recent_count"),
            )
        }
        TextButton(
            onClick = { onNavigate(SmartHomeDestination.Files) },
            modifier = Modifier.testTag("smart_home_recent_see_all"),
        ) { Text(stringResource(R.string.smart_home_see_all)) }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("smart_home_recent_container"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (recentPdfs.isEmpty()) {
                Text(
                    text = stringResource(R.string.smart_home_no_recent),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                        .testTag("smart_home_recent_empty"),
                )
            } else {
                recentPdfs.take(3).forEachIndexed { index, recent ->
                    SmartRecentFileRow(
                        recent = recent,
                        isFavorite = favoritePdfs.any { it.uri == recent.uri },
                        onOpen = onOpenRecent,
                        onShare = onSharePdf,
                        onToggleFavorite = onToggleFavoritePdf,
                        onRemove = onRemoveRecentPdf,
                        index = index,
                    )
                }
            }
        }
    }

    homeNativeContent?.let { nativeAd ->
        Spacer(Modifier.height(20.dp))
        nativeAd()
    }

    val quickTools = favoriteTools.mapNotNull { favorite ->
        tools.firstOrNull { it.tool == favorite }
    }.ifEmpty { tools.filter { it.tool in FavoriteToolStore.DEFAULT_TOOLS } }.take(4)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.smart_home_quick_tools),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("quick_tools_title"),
        )
        TextButton(
            onClick = { onNavigate(SmartHomeDestination.Tools) },
            modifier = Modifier.testTag("view_all_tools"),
        ) { Text(stringResource(R.string.smart_home_view_all_tools)) }
    }
    quickTools.chunked(2).forEach { rowTools ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rowTools.forEach { tool ->
                QuickToolCard(
                    tool = tool,
                    isFavorite = tool.tool in favoriteTools,
                    favoriteLimitReached = tool.tool !in favoriteTools &&
                        favoriteTools.size >= FavoriteToolStore.MAX_FAVORITES,
                    onToggleFavorite = onToggleFavoriteTool,
                    testTag = "favorite_${tool.testTag}",
                    modifier = Modifier.weight(1f),
                )
            }
            if (rowTools.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    SmartToolSectionTitle(R.string.smart_home_activity, "smart_home_activity_title")
    Text(
        text = stringResource(
            R.string.smart_home_activity_summary,
            (recentPdfs.map { it.uri } + favoritePdfs.map { it.uri }).distinct().size,
            favoritePdfs.size,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = if (history.isEmpty()) stringResource(R.string.smart_home_history_empty)
        else stringResource(R.string.smart_home_history_summary, history.size),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(
        onClick = { onNavigate(SmartHomeDestination.History) },
        modifier = Modifier.testTag("smart_home_view_history"),
    ) { Text(stringResource(R.string.smart_home_view_history)) }
}

@Composable
private fun SearchPdfBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.smart_home_search_description)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
            role = Role.Button
            contentDescription = description
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("⌕", style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.smart_home_search),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickToolCard(
    tool: SmartToolAction,
    isFavorite: Boolean,
    favoriteLimitReached: Boolean,
    onToggleFavorite: (SmartTool) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val limitMessage = stringResource(R.string.smart_home_favorite_tool_limit_message)
    val favoriteDescription = stringResource(
        when {
            isFavorite -> R.string.smart_home_remove_favorite_tool_description
            favoriteLimitReached -> R.string.smart_home_favorite_tool_limit_description
            else -> R.string.smart_home_add_favorite_tool_description
        },
        tool.label,
    )
    Surface(
        onClick = tool.onClick,
        modifier = modifier.heightIn(min = 112.dp).testTag(testTag),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(smartToolSymbol(tool.tool), style = MaterialTheme.typography.titleLarge)
                TextButton(
                    onClick = {
                        if (favoriteLimitReached) {
                            Toast.makeText(context, limitMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            onToggleFavorite(tool.tool)
                        }
                    },
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = favoriteDescription
                        selected = isFavorite
                    }.testTag("favorite_tool_${tool.tool.name}"),
                ) { Text(if (isFavorite) "★" else "☆") }
            }
            Text(
                text = tool.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun smartToolSymbol(tool: SmartTool): String = when (tool) {
    SmartTool.ScanDocument -> "▣"
    SmartTool.ImagesToPdf -> "▧"
    SmartTool.MergePdf -> "⇉"
    SmartTool.SplitPdf -> "⑂"
    SmartTool.CompressPdf, SmartTool.TargetFileSize -> "⇣"
    SmartTool.ProtectPdf, SmartTool.RemovePassword, SmartTool.ChangePassword -> "◇"
    SmartTool.SignPdf, SmartTool.FillForms, SmartTool.AnnotatePdf -> "✎"
    SmartTool.TextWatermark, SmartTool.ImageWatermark -> "◫"
    SmartTool.ExtractImages, SmartTool.ExtractPages -> "⇱"
    else -> "▤"
}

@Composable
private fun SmartHomePrimaryActions(onOpenPdf: () -> Unit, onScanDocument: () -> Unit) {
    var locked by remember { mutableStateOf(false) }
    LaunchedEffect(locked) {
        if (locked) {
            delay(600L)
            locked = false
        }
    }
    SmartToolSectionTitle(R.string.smart_home_primary_actions, "smart_home_primary_actions")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            enabled = !locked,
            onClick = { locked = true; onOpenPdf() },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("open_pdf_button"),
        ) { Text(stringResource(R.string.open_pdf)) }
        Button(
            enabled = !locked,
            onClick = { locked = true; onScanDocument() },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("scan_document_button"),
        ) { Text(stringResource(R.string.scan_document)) }
    }
}

@Composable
private fun SmartRecentFileRow(
    recent: RecentPdf,
    isFavorite: Boolean,
    onOpen: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
    onToggleFavorite: (Uri) -> Unit,
    onRemove: (Uri) -> Unit,
    index: Int,
) {
    var expanded by remember(recent.uri) { mutableStateOf(false) }
    val favoriteDescription = stringResource(R.string.smart_home_recent_favorite_indicator)
    val lastOpened = remember(recent.lastOpenedEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(recent.lastOpenedEpochMillis))
    }
    Surface(
        onClick = { onOpen(recent.uri) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .testTag("smart_recent_$index"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "PDF",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            ) {
                Text(
                    text = recent.displayName ?: stringResource(R.string.recent_file_unnamed),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.recent_file_pages,
                        recent.pageCount,
                        recent.pageCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = lastOpened,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isFavorite) {
                Text(
                    text = "★",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = favoriteDescription },
                )
            }
            Box {
                TextButton(
                    onClick = { expanded = true },
                    modifier = Modifier.testTag("smart_recent_actions_$index"),
                ) { Text("⋮") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_pdf)) },
                        onClick = { expanded = false; onShare(recent.uri) },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isFavorite) R.string.favorite_file_remove
                                    else R.string.favorite_file_add,
                                ),
                            )
                        },
                        onClick = { expanded = false; onToggleFavorite(recent.uri) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recent_file_remove)) },
                        onClick = { expanded = false; onRemove(recent.uri) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartToolsContent(
    tools: List<SmartToolAction>,
    favoriteTools: List<SmartTool>,
    onToggleFavoriteTool: (SmartTool) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<SmartToolCategory?>(null) }
    Text(
        text = stringResource(R.string.smart_home_discovery),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().testTag("smart_tools_title"),
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.smart_home_search)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("smart_tool_search"),
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolCategoryChip(null, category) { category = null }
        SmartToolCategory.entries.forEach { option ->
            ToolCategoryChip(option, category) { category = option }
        }
    }
    val visibleTools = tools.filter { tool ->
        (category == null || tool.category == category) &&
            (query.isBlank() || tool.label.contains(query.trim(), ignoreCase = true))
    }
    if (visibleTools.isEmpty()) {
        Text(
            text = stringResource(R.string.smart_home_no_tools),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                .testTag("smart_tool_no_results"),
            textAlign = TextAlign.Center,
        )
    } else {
        SmartToolCategory.entries.forEach { section ->
            val sectionTools = visibleTools.filter { it.category == section }
            if (sectionTools.isEmpty()) return@forEach
            Text(
                text = smartToolCategoryLabel(section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                    .testTag("tool_category_${section.name}"),
            )
            sectionTools.chunked(2).forEach { rowTools ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowTools.forEach { tool ->
                        QuickToolCard(
                            tool = tool,
                            isFavorite = tool.tool in favoriteTools,
                            favoriteLimitReached = tool.tool !in favoriteTools &&
                                favoriteTools.size >= FavoriteToolStore.MAX_FAVORITES,
                            onToggleFavorite = onToggleFavoriteTool,
                            testTag = tool.testTag,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun smartToolCategoryLabel(category: SmartToolCategory): String = stringResource(
    when (category) {
        SmartToolCategory.Create -> R.string.smart_home_category_create
        SmartToolCategory.Organize -> R.string.smart_home_category_organize
        SmartToolCategory.Optimize -> R.string.smart_home_category_optimize
        SmartToolCategory.Secure -> R.string.smart_home_category_secure
        SmartToolCategory.EditAndSign -> R.string.smart_home_category_edit
        SmartToolCategory.Extract -> R.string.smart_home_category_extract
    },
)

@Composable
private fun ToolCategoryChip(
    option: SmartToolCategory?,
    selectedCategory: SmartToolCategory?,
    onClick: () -> Unit,
) {
    val label = stringResource(
        when (option) {
            null -> R.string.smart_home_category_all
            SmartToolCategory.Create -> R.string.smart_home_category_create
            SmartToolCategory.Organize -> R.string.smart_home_category_organize
            SmartToolCategory.Optimize -> R.string.smart_home_category_optimize
            SmartToolCategory.Secure -> R.string.smart_home_category_secure
            SmartToolCategory.EditAndSign -> R.string.smart_home_category_edit
            SmartToolCategory.Extract -> R.string.smart_home_category_extract
        },
    )
    FilterChip(
        selected = selectedCategory == option,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag("smart_tool_category_${option?.name ?: "All"}"),
    )
}

@Composable
private fun SmartToolRow(
    tool: SmartToolAction,
    isFavorite: Boolean,
    onToggleFavorite: (SmartTool) -> Unit,
    testTag: String = tool.testTag,
) {
    val favoriteDescription = stringResource(
        if (isFavorite) R.string.smart_home_remove_favorite_tool_description
        else R.string.smart_home_add_favorite_tool_description,
        tool.label,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = tool.onClick,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag(testTag),
        ) { Text(tool.label) }
        TextButton(
            onClick = { onToggleFavorite(tool.tool) },
            modifier = Modifier
                .semantics {
                    contentDescription = favoriteDescription
                }
                .testTag("favorite_tool_${tool.tool.name}"),
        ) { Text(if (isFavorite) "★" else "☆") }
    }
}

@Composable
private fun SmartToolSectionTitle(resource: Int, testTag: String) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp).testTag(testTag),
    )
}

private fun PdfHistoryOperation.suggestedSmartTool(): SmartTool? = when (this) {
    PdfHistoryOperation.ScanDocument -> SmartTool.ScanDocument
    PdfHistoryOperation.ImagesToPdf -> SmartTool.ImagesToPdf
    PdfHistoryOperation.MergePdf -> SmartTool.MergePdf
    PdfHistoryOperation.SplitPdf -> SmartTool.SplitPdf
    PdfHistoryOperation.ExtractPages -> SmartTool.ExtractPages
    PdfHistoryOperation.DeletePages -> SmartTool.DeletePages
    PdfHistoryOperation.RearrangePages -> SmartTool.RearrangePages
    PdfHistoryOperation.RotatePages -> SmartTool.RotatePages
    PdfHistoryOperation.DuplicatePages -> SmartTool.DuplicatePages
    PdfHistoryOperation.CompressPdf -> SmartTool.CompressPdf
    PdfHistoryOperation.ProtectPdf -> SmartTool.ProtectPdf
    PdfHistoryOperation.RemovePassword -> SmartTool.RemovePassword
    PdfHistoryOperation.ChangePassword -> SmartTool.ChangePassword
    PdfHistoryOperation.TextWatermark -> SmartTool.TextWatermark
    PdfHistoryOperation.ImageWatermark -> SmartTool.ImageWatermark
    PdfHistoryOperation.ExtractImages -> SmartTool.ExtractImages
    PdfHistoryOperation.FillForms -> SmartTool.FillForms
    PdfHistoryOperation.SignPdf -> SmartTool.SignPdf
    PdfHistoryOperation.AnnotatePdf -> SmartTool.AnnotatePdf
}

@Composable
private fun SettingsContent(
    page: SettingsPage,
    onOpenPage: (SettingsPage) -> Unit,
    themeMode: AppThemeMode,
    onChangeTheme: (AppThemeMode) -> Unit,
    selectedLanguageTag: String,
    onChangeLanguage: (String) -> Unit,
    adPrivacyOptionsRequired: Boolean,
    onOpenAdvertisingPrivacy: () -> Unit,
) {
    val languageOptions = listOf(
        "en" to R.string.settings_language_english,
        "de" to R.string.settings_language_german,
        "fr" to R.string.settings_language_french,
        "ja" to R.string.settings_language_japanese,
        "hi" to R.string.settings_language_hindi,
        "ru" to R.string.settings_language_russian,
        "es" to R.string.settings_language_spanish,
        "pt-PT" to R.string.settings_language_portuguese_portugal,
        "pt-BR" to R.string.settings_language_portuguese_brazil,
        "it" to R.string.settings_language_italian,
        "id" to R.string.settings_language_indonesian,
        "ar" to R.string.settings_language_arabic,
        "ko" to R.string.settings_language_korean,
        "ur" to R.string.settings_language_urdu,
    )
    var languageQuery by rememberSaveable { mutableStateOf("") }
    val currentLanguage = languageOptions.firstOrNull { (tag, _) ->
        tag.equals(selectedLanguageTag, ignoreCase = true) ||
            (!tag.contains('-') && tag == selectedLanguageTag.substringBefore('-'))
    }

    when (page) {
        SettingsPage.Overview -> Column(
            modifier = Modifier.fillMaxWidth().testTag("settings_content"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsCard(
                title = stringResource(R.string.settings_appearance_title),
                description = stringResource(
                    R.string.settings_appearance_current,
                    stringResource(
                        if (themeMode == AppThemeMode.Dark) {
                            R.string.settings_theme_dark
                        } else {
                            R.string.settings_theme_light
                        },
                    ),
                ),
                testTag = "settings_theme_card",
                action = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_dark_theme_toggle),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Switch(
                                checked = themeMode == AppThemeMode.Dark,
                                onCheckedChange = { enabled ->
                                    onChangeTheme(
                                        if (enabled) AppThemeMode.Dark else AppThemeMode.Light,
                                    )
                                },
                                modifier = Modifier.testTag("settings_dark_theme_switch"),
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_theme_icon_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                },
            )
            SettingsCard(
                title = stringResource(R.string.settings_language_title),
                description = stringResource(
                    R.string.settings_language_current,
                    stringResource(currentLanguage?.second ?: R.string.settings_language_system),
                ),
                testTag = "settings_language_card",
                onClick = {
                    languageQuery = ""
                    onOpenPage(SettingsPage.Language)
                },
            )
            SettingsCard(
                title = stringResource(R.string.settings_privacy_title),
                description = stringResource(R.string.settings_privacy_card_description),
                testTag = "settings_privacy_card",
                onClick = { onOpenPage(SettingsPage.Privacy) },
            )
            SettingsCard(
                title = stringResource(R.string.settings_advertising_title),
                description = stringResource(R.string.settings_advertising_card_description),
                testTag = "settings_advertising_card",
                onClick = { onOpenPage(SettingsPage.Advertising) },
            )
            SettingsCard(
                title = stringResource(R.string.settings_about_title),
                description = stringResource(
                    R.string.settings_about_description,
                    BuildConfig.VERSION_NAME,
                ),
                testTag = "settings_about_card",
                onClick = { onOpenPage(SettingsPage.About) },
            )
        }

        SettingsPage.Language -> {
        val localizedOptions = languageOptions.map { (tag, label) -> tag to stringResource(label) }
        val filteredOptions = localizedOptions.filter { (_, label) ->
            label.contains(languageQuery.trim(), ignoreCase = true)
        }
            Column(
                modifier = Modifier.fillMaxWidth().testTag("settings_language_screen"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_language_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = languageQuery,
                    onValueChange = { languageQuery = it },
                    label = { Text(stringResource(R.string.settings_search_language)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_language_search"),
                )
                SettingsLanguageChoice(
                    label = stringResource(R.string.settings_language_system),
                    selected = currentLanguage == null,
                    testTag = "settings_language_system",
                    onClick = { if (selectedLanguageTag.isNotBlank()) onChangeLanguage("") },
                )
                filteredOptions.forEach { (tag, label) ->
                    SettingsLanguageChoice(
                        label = label,
                        selected = tag == currentLanguage?.first,
                        testTag = "settings_language_$tag",
                        onClick = { if (tag != currentLanguage?.first) onChangeLanguage(tag) },
                    )
                }
            }
        }

        SettingsPage.Privacy -> {
            val context = LocalContext.current
            Column(
                modifier = Modifier.fillMaxWidth().testTag("settings_privacy_screen"),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SettingsDetailContent(
                    text = stringResource(R.string.settings_privacy_policy_details),
                    testTag = "settings_privacy_details",
                )
                SettingsOutlineAction(
                    text = stringResource(R.string.settings_privacy_policy),
                    onClick = {
                        context.startActivity(Intent(context, PrivacyPolicyActivity::class.java))
                    },
                    testTag = "settings_privacy_policy",
                )
            }
        }

        SettingsPage.Advertising -> Column(
            modifier = Modifier.fillMaxWidth().testTag("settings_advertising_screen"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsDetailContent(
                text = stringResource(
                    if (adPrivacyOptionsRequired) {
                        R.string.settings_advertising_description
                    } else {
                        R.string.settings_advertising_not_required
                    },
                ),
                testTag = "settings_advertising_details",
            )
            SettingsOutlineAction(
                text = stringResource(R.string.settings_advertising_manage),
                onClick = onOpenAdvertisingPrivacy,
                testTag = "settings_advertising_manage",
            )
        }

        SettingsPage.About -> SettingsDetailContent(
            text = stringResource(
                R.string.settings_about_details,
                BuildConfig.VERSION_NAME,
            ),
            testTag = "settings_about_screen",
        )
    }
}

@Composable
private fun SettingsLanguageChoice(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { this.selected = selected }
            .testTag(testTag),
    ) {
        Text(
            text = if (selected) "✓  $label" else label,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SettingsDetailContent(text: String, testTag: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    testTag: String,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            action?.let {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { it() }
            }
        }
    }
}

@Composable
private fun SettingsOutlineAction(
    text: String,
    onClick: () -> Unit,
    testTag: String,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(testTag),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IdleContent(
    destination: SmartHomeDestination,
    legacyHomeSections: Boolean,
    onOpenPdf: () -> Unit,
    recentPdfs: List<RecentPdf>,
    favoritePdfs: List<FavoritePdf>,
    history: List<PdfHistoryEntry>,
    continueReading: ContinueReadingPdf?,
    favoriteTools: List<SmartTool>,
    homeNativeContent: (@Composable () -> Unit)?,
    onOpenRecentPdf: (Uri) -> Unit,
    onRemoveRecentPdf: (Uri) -> Unit,
    onClearRecentPdfs: () -> Unit,
    onOpenFavoritePdf: (Uri) -> Unit,
    onToggleFavoritePdf: (Uri) -> Unit,
    onRemoveFavoritePdf: (Uri) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveHistoryEntry: (PdfHistoryEntry) -> Unit,
    onToggleFavoriteTool: (SmartTool) -> Unit,
    onSharePdf: (Uri) -> Unit,
    onNavigate: (SmartHomeDestination) -> Unit,
    settingsPage: SettingsPage,
    onOpenSettingsPage: (SettingsPage) -> Unit,
    themeMode: AppThemeMode,
    onChangeTheme: (AppThemeMode) -> Unit,
    selectedLanguageTag: String,
    onChangeLanguage: (String) -> Unit,
    adPrivacyOptionsRequired: Boolean,
    onOpenAdvertisingPrivacy: () -> Unit,
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
    textWatermarkState: TextWatermarkState,
    onTextWatermark: () -> Unit,
    onOpenTextWatermarkedPdf: () -> Unit,
    onDismissTextWatermarkResult: () -> Unit,
    imageWatermarkState: ImageWatermarkState,
    onImageWatermark: () -> Unit,
    onOpenImageWatermarkedPdf: () -> Unit,
    onDismissImageWatermarkResult: () -> Unit,
    extractImagesState: ExtractImagesState,
    onExtractImages: () -> Unit,
    onDismissExtractImagesResult: () -> Unit,
    fillFormsState: FillFormsState,
    onFillForms: () -> Unit,
    onOpenFilledFormPdf: () -> Unit,
    onDismissFillFormsResult: () -> Unit,
    signPdfState: SignPdfState,
    onSignPdf: () -> Unit,
    onOpenSignedPdf: () -> Unit,
    onDismissSignPdfResult: () -> Unit,
    annotatePdfState: AnnotatePdfState,
    onAnnotatePdf: () -> Unit,
    onOpenAnnotatedPdf: () -> Unit,
    onDismissAnnotatePdfResult: () -> Unit,
    scannerCaptureState: ScannerCaptureState,
    onScanDocument: () -> Unit,
    onOpenScannerPdf: () -> Unit,
    onDismissScannerResult: () -> Unit,
) {
    if (destination == SmartHomeDestination.Settings) {
        SettingsContent(
            page = settingsPage,
            onOpenPage = onOpenSettingsPage,
            themeMode = themeMode,
            onChangeTheme = onChangeTheme,
            selectedLanguageTag = selectedLanguageTag,
            onChangeLanguage = onChangeLanguage,
            adPrivacyOptionsRequired = adPrivacyOptionsRequired,
            onOpenAdvertisingPrivacy = onOpenAdvertisingPrivacy,
        )
        return
    }
    var fileQuery by rememberSaveable { mutableStateOf("") }
    var fileSortOrder by rememberSaveable { mutableStateOf(PdfFileSortOrder.Newest) }
    var fileFilter by rememberSaveable { mutableStateOf(SmartFileFilter.All) }
    val unnamedPdfName = stringResource(R.string.recent_file_unnamed)
    val organizedFavorites = PdfFileOrganizer.organize(
        files = favoritePdfs,
        query = fileQuery,
        sortOrder = fileSortOrder,
        displayName = { it.displayName ?: unnamedPdfName },
        pageCount = FavoritePdf::pageCount,
        timestamp = FavoritePdf::addedEpochMillis,
    )
    val organizedRecents = PdfFileOrganizer.organize(
        files = recentPdfs,
        query = fileQuery,
        sortOrder = fileSortOrder,
        displayName = { it.displayName ?: unnamedPdfName },
        pageCount = RecentPdf::pageCount,
        timestamp = RecentPdf::lastOpenedEpochMillis,
    )
    val toolActions = listOf(
        SmartToolAction(SmartTool.ScanDocument, stringResource(R.string.scan_document), SmartToolCategory.Create, "tool_scan_document_button", onScanDocument),
        SmartToolAction(SmartTool.ImagesToPdf, stringResource(R.string.images_to_pdf), SmartToolCategory.Create, "images_to_pdf_button", onImagesToPdf),
        SmartToolAction(SmartTool.MergePdf, stringResource(R.string.merge_pdf), SmartToolCategory.Organize, "merge_pdf_button", onMergePdfs),
        SmartToolAction(SmartTool.SplitPdf, stringResource(R.string.split_pdf), SmartToolCategory.Organize, "split_pdf_button", onSplitPdf),
        SmartToolAction(SmartTool.RearrangePages, stringResource(R.string.rearrange_pages), SmartToolCategory.Organize, "rearrange_pages_button", onRearrangePages),
        SmartToolAction(SmartTool.ExtractPages, stringResource(R.string.extract_pages), SmartToolCategory.Organize, "extract_pages_button", onExtractPages),
        SmartToolAction(SmartTool.DeletePages, stringResource(R.string.delete_pages), SmartToolCategory.Organize, "delete_pages_button", onDeletePages),
        SmartToolAction(SmartTool.RotatePages, stringResource(R.string.rotate_pages), SmartToolCategory.Organize, "rotate_pages_button", onRotatePages),
        SmartToolAction(SmartTool.DuplicatePages, stringResource(R.string.duplicate_pages), SmartToolCategory.Organize, "duplicate_pages_button", onDuplicatePages),
        SmartToolAction(SmartTool.CompressPdf, stringResource(R.string.compress_pdf), SmartToolCategory.Optimize, "compress_pdf_button", onCompressPdf),
        SmartToolAction(SmartTool.TargetFileSize, stringResource(R.string.smart_home_target_size), SmartToolCategory.Optimize, "target_file_size_button", onCompressPdf),
        SmartToolAction(SmartTool.ProtectPdf, stringResource(R.string.protect_pdf), SmartToolCategory.Secure, "protect_pdf_button", onProtectPdf),
        SmartToolAction(SmartTool.RemovePassword, stringResource(R.string.remove_password), SmartToolCategory.Secure, "remove_password_button", onRemovePassword),
        SmartToolAction(SmartTool.ChangePassword, stringResource(R.string.change_password), SmartToolCategory.Secure, "change_password_button", onChangePassword),
        SmartToolAction(SmartTool.FillForms, stringResource(R.string.fill_forms), SmartToolCategory.EditAndSign, "fill_forms_button", onFillForms),
        SmartToolAction(SmartTool.SignPdf, stringResource(R.string.sign_pdf), SmartToolCategory.EditAndSign, "sign_pdf_button", onSignPdf),
        SmartToolAction(SmartTool.AnnotatePdf, stringResource(R.string.annotate_pdf), SmartToolCategory.EditAndSign, "annotate_pdf_button", onAnnotatePdf),
        SmartToolAction(SmartTool.TextWatermark, stringResource(R.string.text_watermark), SmartToolCategory.EditAndSign, "text_watermark_button", onTextWatermark),
        SmartToolAction(SmartTool.ImageWatermark, stringResource(R.string.image_watermark), SmartToolCategory.EditAndSign, "image_watermark_button", onImageWatermark),
        SmartToolAction(SmartTool.ExtractImages, stringResource(R.string.extract_images), SmartToolCategory.Extract, "extract_images_button", onExtractImages),
    )
    if (destination == SmartHomeDestination.Home && !legacyHomeSections) {
        SmartHomeDashboard(
            continueReading = continueReading,
            recentPdfs = recentPdfs,
            favoritePdfs = favoritePdfs,
            history = history,
            favoriteTools = favoriteTools,
            homeNativeContent = homeNativeContent,
            tools = toolActions,
            onOpenPdf = onOpenPdf,
            onScanDocument = onScanDocument,
            onResume = onOpenRecentPdf,
            onOpenRecent = onOpenRecentPdf,
            onSharePdf = onSharePdf,
            onToggleFavoritePdf = onToggleFavoritePdf,
            onRemoveRecentPdf = onRemoveRecentPdf,
            onToggleFavoriteTool = onToggleFavoriteTool,
            onNavigate = onNavigate,
        )
    }
    if (destination == SmartHomeDestination.Home && legacyHomeSections) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.home_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        SmartHomePrimaryActions(onOpenPdf = onOpenPdf, onScanDocument = onScanDocument)
        if (favoritePdfs.isEmpty() && recentPdfs.isEmpty() && history.isEmpty()) {
            toolActions.filterNot { it.tool == SmartTool.ScanDocument }.forEach { tool ->
                SmartToolRow(tool, tool.tool in favoriteTools, onToggleFavoriteTool)
            }
        }
    }
    if (destination == SmartHomeDestination.Tools) {
        SmartToolsContent(
            tools = toolActions,
            favoriteTools = favoriteTools,
            onToggleFavoriteTool = onToggleFavoriteTool,
        )
    }
    if (destination == SmartHomeDestination.Files ||
        (destination == SmartHomeDestination.Home && legacyHomeSections)
    ) {
        if (destination == SmartHomeDestination.Files) {
            SearchPdfBanner(
                onClick = { onNavigate(SmartHomeDestination.Search) },
                modifier = Modifier.testTag("smart_files_search_banner"),
            )
            Text(
                text = stringResource(R.string.smart_home_nav_files),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().testTag("smart_files_title"),
            )
            Text(
                text = stringResource(
                    R.string.smart_files_count,
                    (favoritePdfs.map { it.uri } + recentPdfs.map { it.uri }).distinct().size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmartFileFilter.entries.forEach { option ->
                    FilterChip(
                        selected = fileFilter == option,
                        onClick = { fileFilter = option },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        SmartFileFilter.All -> R.string.smart_files_filter_all
                                        SmartFileFilter.Recent -> R.string.smart_files_filter_recent
                                        SmartFileFilter.Favorites -> R.string.smart_files_filter_favorites
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.testTag("smart_files_filter_${option.name}"),
                    )
                }
            }
        }
        if (destination == SmartHomeDestination.Files &&
            favoritePdfs.isEmpty() && recentPdfs.isEmpty()
        ) {
            Text(
                text = stringResource(R.string.smart_home_files_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    .testTag("smart_files_empty"),
            )
            Button(
                onClick = onOpenPdf,
                modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)
                    .testTag("smart_files_open"),
            ) { Text(stringResource(R.string.open_pdf)) }
        }
        if (favoritePdfs.isNotEmpty() || recentPdfs.isNotEmpty()) {
        FileOrganizerControls(
            query = fileQuery,
            onQueryChange = { fileQuery = it },
            sortOrder = fileSortOrder,
            onSortOrderChange = { fileSortOrder = it },
        )
        }
    if (organizedFavorites.isNotEmpty() &&
        (destination != SmartHomeDestination.Files || fileFilter != SmartFileFilter.Recent)
    ) {
        FavoriteFilesSection(
            favoritePdfs = organizedFavorites,
            onOpen = onOpenFavoritePdf,
            onShare = onSharePdf,
            onRemove = onRemoveFavoritePdf,
        )
    }
    if (organizedRecents.isNotEmpty() &&
        (destination != SmartHomeDestination.Files || fileFilter != SmartFileFilter.Favorites)
    ) {
        RecentFilesSection(
            recentPdfs = organizedRecents,
            showAll = fileQuery.isNotBlank() || fileSortOrder != PdfFileSortOrder.Newest,
            favoriteUris = favoritePdfs.mapTo(mutableSetOf(), FavoritePdf::uri),
            onOpen = onOpenRecentPdf,
            onToggleFavorite = onToggleFavoritePdf,
            onShare = onSharePdf,
            onRemove = onRemoveRecentPdf,
            onClear = onClearRecentPdfs,
        )
    }
    if (fileQuery.isNotBlank() && organizedFavorites.isEmpty() && organizedRecents.isEmpty()) {
        Text(
            text = stringResource(R.string.file_search_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("file_search_no_results"),
            textAlign = TextAlign.Center,
        )
    }
    }
    if (destination == SmartHomeDestination.Search) {
        Text(
            text = stringResource(R.string.smart_home_nav_search),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().testTag("smart_search_title"),
        )
        FileOrganizerControls(
            query = fileQuery,
            onQueryChange = { fileQuery = it },
            sortOrder = fileSortOrder,
            onSortOrderChange = { fileSortOrder = it },
        )
        if (organizedFavorites.isNotEmpty()) {
            FavoriteFilesSection(
                favoritePdfs = organizedFavorites,
                onOpen = onOpenFavoritePdf,
                onShare = onSharePdf,
                onRemove = onRemoveFavoritePdf,
            )
        }
        if (organizedRecents.isNotEmpty()) {
            RecentFilesSection(
                recentPdfs = organizedRecents,
                showAll = true,
                favoriteUris = favoritePdfs.mapTo(mutableSetOf(), FavoritePdf::uri),
                onOpen = onOpenRecentPdf,
                onToggleFavorite = onToggleFavoritePdf,
                onShare = onSharePdf,
                onRemove = onRemoveRecentPdf,
                onClear = onClearRecentPdfs,
            )
        }
        if (fileQuery.isNotBlank() && organizedFavorites.isEmpty() && organizedRecents.isEmpty()) {
            Text(
                text = stringResource(R.string.file_search_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    .testTag("file_search_no_results"),
                textAlign = TextAlign.Center,
            )
        }
    }
    if (destination == SmartHomeDestination.History) {
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.smart_home_nav_history),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().testTag("smart_history_title"),
            )
            Text(
                text = stringResource(R.string.smart_home_history_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    .testTag("smart_history_empty"),
                textAlign = TextAlign.Center,
            )
        } else {
            HistorySection(
                history = history,
                onClear = onClearHistory,
                onRemove = onRemoveHistoryEntry,
                onRepeat = { operation ->
                    operation.suggestedSmartTool()?.let { tool ->
                        toolActions.firstOrNull { it.tool == tool }?.onClick?.invoke()
                    }
                },
            )
        }
    }
    if (destination == SmartHomeDestination.Home && legacyHomeSections &&
        (favoritePdfs.isNotEmpty() || recentPdfs.isNotEmpty() || history.isNotEmpty())
    ) {
        if (history.isNotEmpty()) {
            HistorySection(
                history = history,
                onClear = onClearHistory,
                onRemove = onRemoveHistoryEntry,
            )
        }
        toolActions.filterNot { it.tool == SmartTool.ScanDocument }.forEach { tool ->
            SmartToolRow(tool, tool.tool in favoriteTools, onToggleFavoriteTool)
        }
    }
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
                TextButton(
                    onClick = { onSharePdf(compressPdfState.outputUri) },
                    modifier = Modifier.testTag("share_compressed_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onSharePdf(protectPdfState.outputUri) },
                    modifier = Modifier.testTag("share_protected_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
                TextButton(onClick = onDismissPdfProtectionResult) {
                    Text(stringResource(R.string.dismiss))
                }
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
                TextButton(
                    onClick = { onSharePdf(removePasswordState.outputUri) },
                    modifier = Modifier.testTag("share_password_removed_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onSharePdf(changePasswordState.outputUri) },
                    modifier = Modifier.testTag("share_password_changed_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
                TextButton(onClick = onDismissPasswordChangeResult) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
        else -> Unit
    }
    when (textWatermarkState) {
        is TextWatermarkState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(textWatermarkState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("text_watermark_error"),
            )
            TextButton(onClick = onDismissTextWatermarkResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is TextWatermarkState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(
                    R.string.text_watermark_completed,
                    textWatermarkState.watermarkedPageCount,
                    textWatermarkState.pageCount,
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("text_watermark_success"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onOpenTextWatermarkedPdf,
                    modifier = Modifier.testTag("open_text_watermarked_pdf"),
                ) { Text(stringResource(R.string.open_pdf)) }
                TextButton(
                    onClick = { onSharePdf(textWatermarkState.outputUri) },
                    modifier = Modifier.testTag("share_text_watermarked_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
                TextButton(onClick = onDismissTextWatermarkResult) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
        else -> Unit
    }
    when (imageWatermarkState) {
        is ImageWatermarkState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(imageWatermarkState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("image_watermark_error"),
            )
            TextButton(onClick = onDismissImageWatermarkResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is ImageWatermarkState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(
                    R.string.image_watermark_completed,
                    imageWatermarkState.watermarkedPageCount,
                    imageWatermarkState.pageCount,
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("image_watermark_success"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onOpenImageWatermarkedPdf,
                    modifier = Modifier.testTag("open_image_watermarked_pdf"),
                ) { Text(stringResource(R.string.open_pdf)) }
                TextButton(
                    onClick = { onSharePdf(imageWatermarkState.outputUri) },
                    modifier = Modifier.testTag("share_image_watermarked_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
                TextButton(onClick = onDismissImageWatermarkResult) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
        else -> Unit
    }
    when (extractImagesState) {
        is ExtractImagesState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(extractImagesState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("extract_images_error"),
            )
            TextButton(onClick = onDismissExtractImagesResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is ExtractImagesState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(
                    if (extractImagesState.destination == ExtractImagesDestination.Zip) {
                        R.string.extract_images_completed_zip
                    } else R.string.extract_images_completed_directory,
                    extractImagesState.imageCount,
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("extract_images_success"),
            )
            TextButton(onClick = onDismissExtractImagesResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        else -> Unit
    }
    when (fillFormsState) {
        is FillFormsState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(fillFormsState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("fill_forms_error"),
            )
            TextButton(onClick = onDismissFillFormsResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is FillFormsState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(
                    R.string.fill_forms_completed,
                    fillFormsState.updatedFieldCount,
                    fillFormsState.pageCount,
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("fill_forms_success"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onOpenFilledFormPdf,
                    modifier = Modifier.testTag("open_filled_form_pdf"),
                ) { Text(stringResource(R.string.open_pdf)) }
                TextButton(
                    onClick = { onSharePdf(fillFormsState.outputUri) },
                    modifier = Modifier.testTag("share_filled_form_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
                TextButton(onClick = onDismissFillFormsResult) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
        else -> Unit
    }
    when (signPdfState) {
        is SignPdfState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(signPdfState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("sign_pdf_error"),
            )
            TextButton(onClick = onDismissSignPdfResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is SignPdfState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.sign_pdf_completed, signPdfState.pageCount),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("sign_pdf_success"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onOpenSignedPdf,
                    modifier = Modifier.testTag("open_signed_pdf"),
                ) { Text(stringResource(R.string.open_pdf)) }
                TextButton(
                    onClick = { onSharePdf(signPdfState.outputUri) },
                    modifier = Modifier.testTag("share_signed_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
                TextButton(onClick = onDismissSignPdfResult) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
        else -> Unit
    }
    when (annotatePdfState) {
        is AnnotatePdfState.Failed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(annotatePdfState.failure.messageResource),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("annotate_pdf_error"),
            )
            TextButton(onClick = onDismissAnnotatePdfResult) {
                Text(stringResource(R.string.dismiss))
            }
        }
        is AnnotatePdfState.Completed -> {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(
                    R.string.annotate_pdf_completed,
                    annotatePdfState.annotationCount,
                    annotatePdfState.pageCount,
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("annotate_pdf_success"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onOpenAnnotatedPdf,
                    modifier = Modifier.testTag("open_annotated_pdf"),
                ) { Text(stringResource(R.string.open_pdf)) }
                TextButton(
                    onClick = { onSharePdf(annotatePdfState.outputUri) },
                    modifier = Modifier.testTag("share_annotated_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
                TextButton(onClick = onDismissAnnotatePdfResult) {
                    Text(stringResource(R.string.dismiss))
                }
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
                TextButton(
                    onClick = { onSharePdf(scannerCaptureState.outputUri) },
                    modifier = Modifier.testTag("share_scanner_pdf"),
                ) { Text(stringResource(R.string.share_pdf)) }
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
    onCropChangeFinished: () -> Unit,
    onResetCrop: () -> Unit,
    onUpdateEnhancement: (ScannerEnhancementSettings) -> Unit,
    onAddPage: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onDeletePage: () -> Unit,
    onSavePdf: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
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
                    onCropChangeFinished = onCropChangeFinished,
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
    onCropChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    val latestCrop by rememberUpdatedState(crop)
    val latestOnCropChanged by rememberUpdatedState(onCropChanged)
    val latestOnCropChangeFinished by rememberUpdatedState(onCropChangeFinished)
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
        val updated = crop.moveCornerConstrained(
            index,
            ScannerCropPoint(
                point.x + deltaX / editorSize.width,
                point.y + deltaY / editorSize.height,
            ),
        )
        if (updated == crop) return false
        onCropChanged(updated)
        onCropChangeFinished()
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
                    .pointerInput(index, editorSize) {
                        var workingCrop = latestCrop
                        var cropChanged = false
                        detectDragGestures(
                            onDragStart = {
                                workingCrop = latestCrop
                                cropChanged = false
                            },
                            onDragEnd = {
                                if (cropChanged) latestOnCropChangeFinished()
                            },
                            onDragCancel = {
                                if (cropChanged) latestOnCropChangeFinished()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (editorSize.width <= 0 || editorSize.height <= 0) {
                                    return@detectDragGestures
                                }
                                val current = workingCrop.points[index]
                                val updated = workingCrop.moveCornerConstrained(
                                    index,
                                    ScannerCropPoint(
                                        current.x + dragAmount.x / editorSize.width,
                                        current.y + dragAmount.y / editorSize.height,
                                    ),
                                )
                                if (updated != workingCrop) {
                                    workingCrop = updated
                                    cropChanged = true
                                    latestOnCropChanged(updated)
                                }
                            },
                        )
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
