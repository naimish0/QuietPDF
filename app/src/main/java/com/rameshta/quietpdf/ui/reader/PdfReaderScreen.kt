package com.rameshta.quietpdf.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rameshta.quietpdf.R
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    document: PdfOpenState.Opened,
    onOpenAnother: () -> Unit,
    renderPage: suspend (pageIndex: Int, targetWidth: Int) -> PageRenderResult,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = document.displayName ?: stringResource(R.string.selected_pdf),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(onClick = onOpenAnother) {
                        Text(stringResource(R.string.reader_open_another))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                count = document.pageCount,
                key = { pageIndex -> pageIndex },
            ) { pageIndex ->
                PdfPageItem(
                    pageIndex = pageIndex,
                    pageCount = document.pageCount,
                    documentIdentity = document.uri,
                    renderPage = renderPage,
                )
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    pageCount: Int,
    documentIdentity: Any,
    renderPage: suspend (pageIndex: Int, targetWidth: Int) -> PageRenderResult,
) {
    val pageNumber = pageIndex + 1
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val targetWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
                var attempt by remember(documentIdentity, pageIndex) { mutableIntStateOf(0) }
                var pageState by remember(documentIdentity, pageIndex, targetWidth) {
                    mutableStateOf<PageRenderResult?>(null)
                }

                LaunchedEffect(documentIdentity, pageIndex, targetWidth, attempt) {
                    pageState = null
                    pageState = renderPage(pageIndex, targetWidth)
                }

                when (val result = pageState) {
                    null -> PageLoading(pageNumber)
                    is PageRenderResult.Failed -> PageFailure(
                        pageNumber = pageNumber,
                        messageResource = result.reason.messageResource,
                        onRetry = { attempt++ },
                    )
                    is PageRenderResult.Ready -> RenderedPage(
                        bitmap = result.bitmap,
                        pageNumber = pageNumber,
                        pageCount = pageCount,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.page_number, pageNumber, pageCount),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageLoading(pageNumber: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .testTag("page_loading_$pageNumber"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
            Text(
                text = stringResource(R.string.page_loading, pageNumber),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PageFailure(pageNumber: Int, messageResource: Int, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .testTag("page_error_$pageNumber"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(messageResource, pageNumber),
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun RenderedPage(bitmap: Bitmap, pageNumber: Int, pageCount: Int) {
    val description = stringResource(R.string.page_content_description, pageNumber, pageCount)
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .testTag("pdf_page_$pageNumber"),
    )
}
