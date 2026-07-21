package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rameshta.quietpdf.pdf.SignPdfPreviewResult
import com.rameshta.quietpdf.pdf.VisibleSignatureSettings
import kotlin.math.roundToInt

@Composable
internal fun SignPdfDialog(
    documentName: String,
    pageCount: Int,
    importedSignature: Bitmap?,
    onImportSignature: () -> Unit,
    renderPreview: suspend (Bitmap, VisibleSignatureSettings, Int) -> SignPdfPreviewResult,
    onConfirm: (Bitmap, VisibleSignatureSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var strokes by remember(documentName) { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var redoStrokes by remember(documentName) { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember(documentName) { mutableStateOf<List<Offset>>(emptyList()) }
    var drawnSignature by remember(documentName) { mutableStateOf<Bitmap?>(null) }
    var useImported by remember(importedSignature) { mutableStateOf(importedSignature != null) }
    var pageText by remember(documentName) { mutableStateOf("1") }
    var centerX by remember(documentName) { mutableStateOf(0.5f) }
    var centerY by remember(documentName) { mutableStateOf(0.2f) }
    var widthFraction by remember(documentName) { mutableStateOf(0.3f) }
    var previewZoom by remember(documentName) { mutableStateOf(1f) }
    var preview by remember(documentName) { mutableStateOf<Bitmap?>(null) }
    var previewLoading by remember(documentName) { mutableStateOf(false) }
    val signature = drawnSignature ?: importedSignature.takeIf { useImported }
    val pageIndex = pageText.toIntOrNull()?.minus(1)
    val settings = pageIndex?.let {
        VisibleSignatureSettings(it, centerX, centerY, widthFraction)
    }

    DisposableEffect(documentName) {
        onDispose {
            drawnSignature?.recycle()
            preview?.recycle()
            strokes = emptyList()
            redoStrokes = emptyList()
            currentStroke = emptyList()
        }
    }
    LaunchedEffect(signature, settings) {
        preview?.recycle()
        preview = null
        if (signature != null && settings != null && settings.pageIndex in 0 until pageCount) {
            previewLoading = true
            preview = when (val result = renderPreview(signature, settings, 720)) {
                is SignPdfPreviewResult.Ready -> result.bitmap
                else -> null
            }
            previewLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sign_pdf_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.sign_pdf_summary, documentName, pageCount))
                Text(
                    text = stringResource(R.string.sign_pdf_visible_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp).testTag("sign_pdf_visible_notice"),
                )
                if (signature == null) {
                    Text(
                        text = stringResource(R.string.sign_pdf_draw),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    SignatureDrawingPad(
                        strokes = strokes,
                        currentStroke = currentStroke,
                        onStrokeStarted = { currentStroke = listOf(it) },
                        onStrokeChanged = { currentStroke = it },
                        onStrokeFinished = { finishedStroke ->
                            if (finishedStroke.size > 1) {
                                strokes = strokes + listOf(finishedStroke)
                                redoStrokes = emptyList()
                            }
                            currentStroke = emptyList()
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TextButton(
                            enabled = strokes.isNotEmpty(),
                            onClick = {
                                redoStrokes = listOf(strokes.last()) + redoStrokes
                                strokes = strokes.dropLast(1)
                            },
                            modifier = Modifier.testTag("sign_pdf_undo"),
                        ) { Text(stringResource(R.string.sign_pdf_undo)) }
                        TextButton(
                            enabled = redoStrokes.isNotEmpty(),
                            onClick = {
                                strokes = strokes + listOf(redoStrokes.first())
                                redoStrokes = redoStrokes.drop(1)
                            },
                            modifier = Modifier.testTag("sign_pdf_redo"),
                        ) { Text(stringResource(R.string.sign_pdf_redo)) }
                        TextButton(
                            enabled = strokes.isNotEmpty(),
                            onClick = { strokes = emptyList(); redoStrokes = emptyList() },
                            modifier = Modifier.testTag("sign_pdf_clear"),
                        ) { Text(stringResource(R.string.sign_pdf_clear)) }
                    }
                    Button(
                        enabled = strokes.isNotEmpty(),
                        onClick = {
                            drawnSignature?.recycle()
                            drawnSignature = createSignatureBitmap(strokes)
                            useImported = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("sign_pdf_use_drawing"),
                    ) { Text(stringResource(R.string.sign_pdf_use_drawing)) }
                    TextButton(
                        onClick = onImportSignature,
                        modifier = Modifier.fillMaxWidth().testTag("sign_pdf_import"),
                    ) { Text(stringResource(R.string.sign_pdf_import)) }
                } else {
                    OutlinedTextField(
                        value = pageText,
                        onValueChange = { value ->
                            if (value.length <= 5 && value.all(Char::isDigit)) pageText = value
                        },
                        label = { Text(stringResource(R.string.sign_pdf_page)) },
                        supportingText = { Text(stringResource(R.string.sign_pdf_page_help, pageCount)) },
                        isError = pageIndex !in 0 until pageCount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("sign_pdf_page"),
                    )
                    SignatureSlider(
                        label = stringResource(R.string.sign_pdf_horizontal, (centerX * 100).roundToInt()),
                        value = centerX,
                        range = 0f..1f,
                        tag = "sign_pdf_horizontal",
                        onValueChange = { centerX = it },
                    )
                    SignatureSlider(
                        label = stringResource(R.string.sign_pdf_vertical, (centerY * 100).roundToInt()),
                        value = centerY,
                        range = 0f..1f,
                        tag = "sign_pdf_vertical",
                        onValueChange = { centerY = it },
                    )
                    SignatureSlider(
                        label = stringResource(R.string.sign_pdf_size, (widthFraction * 100).roundToInt()),
                        value = widthFraction,
                        range = 0.1f..0.6f,
                        tag = "sign_pdf_size",
                        onValueChange = { widthFraction = it },
                    )
                    Text(
                        text = stringResource(R.string.sign_pdf_preview),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    SignatureSlider(
                        label = stringResource(R.string.sign_pdf_zoom, (previewZoom * 100).roundToInt()),
                        value = previewZoom,
                        range = 1f..2f,
                        tag = "sign_pdf_zoom",
                        onValueChange = { previewZoom = it },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 380.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clipToBounds()
                            .testTag("sign_pdf_preview"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (previewLoading) CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        preview?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = stringResource(R.string.sign_pdf_preview_description),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 380.dp)
                                    .graphicsLayer(scaleX = previewZoom, scaleY = previewZoom),
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            drawnSignature?.recycle()
                            drawnSignature = null
                            useImported = false
                            preview?.recycle()
                            preview = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("sign_pdf_change"),
                    ) { Text(stringResource(R.string.sign_pdf_change)) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = signature != null && settings != null &&
                    settings.pageIndex in 0 until pageCount && preview != null,
                onClick = { onConfirm(checkNotNull(signature), checkNotNull(settings)) },
                modifier = Modifier.testTag("sign_pdf_save"),
            ) { Text(stringResource(R.string.sign_pdf_save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("sign_pdf_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("sign_pdf_dialog"),
    )
}

@Composable
private fun SignatureDrawingPad(
    strokes: List<List<Offset>>,
    currentStroke: List<Offset>,
    onStrokeStarted: (Offset) -> Unit,
    onStrokeChanged: (List<Offset>) -> Unit,
    onStrokeFinished: (List<Offset>) -> Unit,
) {
    val description = stringResource(R.string.sign_pdf_drawing_description)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(top = 8.dp)
            .background(Color.White)
            .semantics { contentDescription = description }
            .testTag("sign_pdf_drawing_pad")
            .pointerInput(Unit) {
                var activeStroke = emptyList<Offset>()
                fun normalized(position: Offset) = Offset(
                    (position.x / size.width).coerceIn(0f, 1f),
                    (position.y / size.height).coerceIn(0f, 1f),
                )
                detectDragGestures(
                    onDragStart = {
                        activeStroke = listOf(normalized(it))
                        onStrokeStarted(activeStroke.first())
                    },
                    onDrag = { change, _ ->
                        activeStroke = activeStroke + normalized(change.position)
                        onStrokeChanged(activeStroke)
                        change.consume()
                    },
                    onDragEnd = { onStrokeFinished(activeStroke) },
                    onDragCancel = { onStrokeFinished(activeStroke) },
                )
            },
    ) {
        (strokes + listOf(currentStroke)).filter { it.isNotEmpty() }.forEach { stroke ->
            val path = Path().apply {
                moveTo(stroke.first().x * size.width, stroke.first().y * size.height)
                stroke.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
            }
            drawPath(
                path = path,
                color = Color(0xFF132F55),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun SignatureSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    onValueChange: (Float) -> Unit,
) {
    Text(label, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

private fun createSignatureBitmap(strokes: List<List<Offset>>): Bitmap? {
    if (strokes.none { it.size > 1 }) return null
    val width = 1200
    val height = 400
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(19, 47, 85)
        style = AndroidPaint.Style.STROKE
        strokeWidth = 14f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    strokes.filter { it.size > 1 }.forEach { stroke ->
        for (index in 1 until stroke.size) {
            canvas.drawLine(
                stroke[index - 1].x * width,
                stroke[index - 1].y * height,
                stroke[index].x * width,
                stroke[index].y * height,
                paint,
            )
        }
    }
    return cropSignatureBitmap(bitmap)
}

private fun cropSignatureBitmap(source: Bitmap): Bitmap? {
    var left = source.width
    var top = source.height
    var right = -1
    var bottom = -1
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            if (source.getPixel(x, y) ushr 24 > 8) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    if (right < left || bottom < top) {
        source.recycle()
        return null
    }
    val padding = 16
    val cropLeft = (left - padding).coerceAtLeast(0)
    val cropTop = (top - padding).coerceAtLeast(0)
    val cropRight = (right + padding).coerceAtMost(source.width - 1)
    val cropBottom = (bottom + padding).coerceAtMost(source.height - 1)
    return Bitmap.createBitmap(
        source,
        cropLeft,
        cropTop,
        cropRight - cropLeft + 1,
        cropBottom - cropTop + 1,
    ).also { source.recycle() }
}
