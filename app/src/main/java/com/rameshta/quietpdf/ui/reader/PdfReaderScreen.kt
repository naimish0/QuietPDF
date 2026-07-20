package com.rameshta.quietpdf.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.rameshta.quietpdf.R
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    var zoomState by remember(documentIdentity, pageIndex) { mutableStateOf(PageZoomState()) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val baseWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
                var attempt by remember(documentIdentity, pageIndex) { mutableIntStateOf(0) }
                var qualityScale by remember(documentIdentity, pageIndex) { mutableFloatStateOf(1f) }
                var pageState by remember(documentIdentity, pageIndex, baseWidth) {
                    mutableStateOf<PageRenderResult?>(null)
                }
                val targetWidth = (baseWidth * qualityScale).roundToInt().coerceAtLeast(1)

                LaunchedEffect(zoomState.scale) {
                    delay(180)
                    qualityScale = zoomState.scale
                }

                LaunchedEffect(documentIdentity, pageIndex, targetWidth, attempt) {
                    val result = renderPage(pageIndex, targetWidth)
                    if (result is PageRenderResult.Ready || pageState == null) {
                        pageState = result
                    }
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
                        zoomState = zoomState,
                        onZoomStateChange = { zoomState = it },
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
@OptIn(ExperimentalFoundationApi::class)
private fun RenderedPage(
    bitmap: Bitmap,
    pageNumber: Int,
    pageCount: Int,
    zoomState: PageZoomState,
    onZoomStateChange: (PageZoomState) -> Unit,
) {
    val description = stringResource(R.string.page_content_description, pageNumber, pageCount)
    val zoomDescription = stringResource(R.string.zoom_state, (zoomState.scale * 100).roundToInt())
    val zoomInLabel = stringResource(R.string.zoom_in)
    val zoomOutLabel = stringResource(R.string.zoom_out)
    val resetLabel = stringResource(R.string.reset_zoom)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val viewportSize = Size(viewport.width.toFloat(), viewport.height.toFloat())
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        onZoomStateChange(
            zoomState.transform(
                zoomChange = zoomChange,
                panChange = panChange,
                viewportSize = viewportSize,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { viewport = it },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                }
                .transformable(
                    state = transformableState,
                    canPan = { zoomState.isZoomed },
                    lockRotationOnZoomPan = true,
                )
                .pointerInput(viewport, zoomState.isZoomed) {
                    detectTapGestures(
                        onDoubleTap = { position ->
                            onZoomStateChange(zoomState.toggle(viewportSize, position))
                        },
                    )
                }
                .semantics {
                    contentDescription = description
                    stateDescription = zoomDescription
                    customActions = listOf(
                        CustomAccessibilityAction(zoomInLabel) {
                            onZoomStateChange(
                                zoomState.transform(1.5f, Offset.Zero, viewportSize),
                            )
                            true
                        },
                        CustomAccessibilityAction(zoomOutLabel) {
                            onZoomStateChange(
                                zoomState.transform(1f / 1.5f, Offset.Zero, viewportSize),
                            )
                            true
                        },
                        CustomAccessibilityAction(resetLabel) {
                            onZoomStateChange(PageZoomState())
                            true
                        },
                    )
                }
                .testTag("pdf_page_$pageNumber"),
        )

        if (zoomState.isZoomed) {
            TextButton(
                onClick = { onZoomStateChange(PageZoomState()) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .testTag("reset_zoom_$pageNumber"),
            ) {
                Text(resetLabel)
            }
        }
    }
}
