package com.rameshta.quietpdf

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.rameshta.quietpdf.ui.reader.PdfReaderScreen
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PdfOpenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) openFromIntent(intent)

        setContent {
            QuietPDFTheme {
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
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier.padding(contentPadding),
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
        when (state) {
            PdfOpenState.Idle -> IdleContent(
                onOpenPdf = onOpenPdf,
                onImagesToPdf = onImagesToPdf,
                imagesToPdfState = imagesToPdfState,
                onDismissImagesToPdfFailure = onDismissImagesToPdfFailure,
                mergePdfState = mergePdfState,
                onMergePdfs = onMergePdfs,
                onDismissMergeFailure = onDismissMergeFailure,
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
    Spacer(Modifier.height(12.dp))
    OpenButton(
        label = stringResource(R.string.images_to_pdf),
        onClick = onImagesToPdf,
        testTag = "images_to_pdf_button",
    )
    Spacer(Modifier.height(12.dp))
    OpenButton(
        label = stringResource(R.string.merge_pdf),
        onClick = onMergePdfs,
        testTag = "merge_pdf_button",
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
}

@Composable
private fun OperationProgressContent(
    messageResource: Int,
    argument: Int? = null,
    onCancel: (() -> Unit)? = null,
) {
    CircularProgressIndicator(
        modifier = Modifier.size(48.dp).testTag("operation_progress"),
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = if (argument == null) stringResource(messageResource)
        else stringResource(messageResource, argument),
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
