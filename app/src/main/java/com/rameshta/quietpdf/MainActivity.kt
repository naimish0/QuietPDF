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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

                QuietPdfApp(
                    state = viewModel.state,
                    onOpenPdf = { picker.launch(arrayOf("application/pdf")) },
                    renderPage = viewModel::renderPage,
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
) {
    if (state is PdfOpenState.Opened) {
        PdfReaderScreen(
            document = state,
            onOpenAnother = onOpenPdf,
            renderPage = renderPage,
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
                    contentPadding = PaddingValues(24.dp),
                )
            }
        }
    }
}

@Composable
private fun OpenPdfContent(
    state: PdfOpenState,
    onOpenPdf: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier.padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            PdfOpenState.Idle -> IdleContent(onOpenPdf)
            PdfOpenState.Opening -> OpeningContent()
            is PdfOpenState.Opened -> Unit
            is PdfOpenState.Failed -> FailedContent(state.failure, onOpenPdf)
        }
    }
}

@Composable
private fun IdleContent(onOpenPdf: () -> Unit) {
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
private fun OpenButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("open_pdf_button"),
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
