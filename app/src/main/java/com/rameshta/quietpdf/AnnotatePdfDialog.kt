package com.rameshta.quietpdf

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import com.rameshta.quietpdf.pdf.AnnotatePdfPreviewResult
import com.rameshta.quietpdf.pdf.AnnotationPoint
import com.rameshta.quietpdf.pdf.PdfAnnotationItem
import kotlin.math.roundToInt

private enum class AnnotationTool { FreeText, Ink, Highlight }

@Composable
internal fun AnnotatePdfDialog(
    documentName: String,
    pageCount: Int,
    renderPreview: suspend (List<PdfAnnotationItem>, Int, Int) -> AnnotatePdfPreviewResult,
    onConfirm: (List<PdfAnnotationItem>) -> Unit,
    onCancel: () -> Unit,
) {
    var annotations by remember(documentName) { mutableStateOf<List<PdfAnnotationItem>>(emptyList()) }
    var redoAnnotations by remember(documentName) { mutableStateOf<List<PdfAnnotationItem>>(emptyList()) }
    var pageText by remember(documentName) { mutableStateOf("1") }
    var tool by remember(documentName) { mutableStateOf(AnnotationTool.FreeText) }
    var freeText by remember(documentName) { mutableStateOf("") }
    var centerX by remember(documentName) { mutableStateOf(0.5f) }
    var centerY by remember(documentName) { mutableStateOf(0.7f) }
    var widthFraction by remember(documentName) { mutableStateOf(0.5f) }
    var heightFraction by remember(documentName) { mutableStateOf(0.06f) }
    var fontSize by remember(documentName) { mutableStateOf(18f) }
    var lineWidth by remember(documentName) { mutableStateOf(3f) }
    var inkStrokes by remember(documentName) { mutableStateOf<List<List<AnnotationPoint>>>(emptyList()) }
    var currentInkStroke by remember(documentName) { mutableStateOf<List<AnnotationPoint>>(emptyList()) }
    var preview by remember(documentName) { mutableStateOf<Bitmap?>(null) }
    var previewLoading by remember(documentName) { mutableStateOf(false) }
    var previewZoom by remember(documentName) { mutableStateOf(1f) }
    val pageIndex = pageText.toIntOrNull()?.minus(1)

    fun addAnnotation(item: PdfAnnotationItem) {
        annotations = annotations + item
        redoAnnotations = emptyList()
    }

    DisposableEffect(documentName) {
        onDispose {
            preview?.recycle()
            annotations = emptyList()
            redoAnnotations = emptyList()
            inkStrokes = emptyList()
            currentInkStroke = emptyList()
        }
    }
    LaunchedEffect(annotations, pageIndex) {
        preview?.recycle()
        preview = null
        if (annotations.isNotEmpty() && pageIndex in 0 until pageCount) {
            previewLoading = true
            preview = when (val result = renderPreview(annotations, checkNotNull(pageIndex), 720)) {
                is AnnotatePdfPreviewResult.Ready -> result.bitmap
                else -> null
            }
            previewLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.annotate_pdf_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.annotate_pdf_summary, documentName, pageCount))
                Text(
                    text = stringResource(R.string.annotate_pdf_scope),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp).testTag("annotate_pdf_scope"),
                )
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { value ->
                        if (value.length <= 5 && value.all(Char::isDigit)) pageText = value
                    },
                    label = { Text(stringResource(R.string.annotate_pdf_page)) },
                    supportingText = { Text(stringResource(R.string.annotate_pdf_page_help, pageCount)) },
                    isError = pageIndex !in 0 until pageCount,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("annotate_pdf_page"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnnotationTool.entries.forEach { candidate ->
                        FilterChip(
                            selected = tool == candidate,
                            onClick = { tool = candidate },
                            label = { Text(stringResource(when (candidate) {
                                AnnotationTool.FreeText -> R.string.annotate_pdf_free_text
                                AnnotationTool.Ink -> R.string.annotate_pdf_ink
                                AnnotationTool.Highlight -> R.string.annotate_pdf_highlight
                            })) },
                            modifier = Modifier.testTag("annotate_tool_${candidate.name}"),
                        )
                    }
                }
                when (tool) {
                    AnnotationTool.FreeText -> {
                        OutlinedTextField(
                            value = freeText,
                            onValueChange = { if (it.length <= 2_000) freeText = it },
                            label = { Text(stringResource(R.string.annotate_pdf_text_hint)) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("annotate_free_text"),
                        )
                        PlacementSliders(
                            centerX, centerY, widthFraction,
                            onCenterX = { centerX = it },
                            onCenterY = { centerY = it },
                            onWidth = { widthFraction = it },
                        )
                        AnnotationSlider(
                            stringResource(R.string.annotate_pdf_font_size, fontSize.roundToInt()),
                            fontSize, 8f..40f, "annotate_font_size", { fontSize = it },
                        )
                        Button(
                            enabled = pageIndex in 0 until pageCount && freeText.isNotBlank(),
                            onClick = {
                                addAnnotation(PdfAnnotationItem.FreeText(
                                    checkNotNull(pageIndex), freeText.trim(), centerX, centerY,
                                    widthFraction.coerceIn(0.15f, 0.9f), fontSize,
                                ))
                                freeText = ""
                            },
                            modifier = Modifier.fillMaxWidth().testTag("annotate_add_text"),
                        ) { Text(stringResource(R.string.annotate_pdf_add_text)) }
                    }
                    AnnotationTool.Ink -> {
                        AnnotationInkPad(
                            strokes = inkStrokes,
                            currentStroke = currentInkStroke,
                            onStrokeStarted = { currentInkStroke = listOf(it) },
                            onStrokeChanged = { currentInkStroke = it },
                            onStrokeFinished = { stroke ->
                                if (stroke.size > 1) inkStrokes = inkStrokes + listOf(stroke)
                                currentInkStroke = emptyList()
                            },
                        )
                        AnnotationSlider(
                            stringResource(R.string.annotate_pdf_line_width, lineWidth.roundToInt()),
                            lineWidth, 1f..12f, "annotate_line_width", { lineWidth = it },
                        )
                        TextButton(
                            enabled = inkStrokes.isNotEmpty(),
                            onClick = { inkStrokes = emptyList() },
                            modifier = Modifier.fillMaxWidth().testTag("annotate_clear_ink"),
                        ) { Text(stringResource(R.string.annotate_pdf_clear_ink)) }
                        Button(
                            enabled = pageIndex in 0 until pageCount && inkStrokes.isNotEmpty(),
                            onClick = {
                                addAnnotation(PdfAnnotationItem.Ink(
                                    checkNotNull(pageIndex), inkStrokes, lineWidth,
                                ))
                                inkStrokes = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("annotate_add_ink"),
                        ) { Text(stringResource(R.string.annotate_pdf_add_ink)) }
                    }
                    AnnotationTool.Highlight -> {
                        PlacementSliders(
                            centerX, centerY, widthFraction,
                            onCenterX = { centerX = it },
                            onCenterY = { centerY = it },
                            onWidth = { widthFraction = it },
                        )
                        AnnotationSlider(
                            stringResource(R.string.annotate_pdf_height, (heightFraction * 100).roundToInt()),
                            heightFraction, 0.02f..0.3f, "annotate_height", { heightFraction = it },
                        )
                        Button(
                            enabled = pageIndex in 0 until pageCount,
                            onClick = { addAnnotation(PdfAnnotationItem.Highlight(
                                checkNotNull(pageIndex), centerX, centerY,
                                widthFraction.coerceIn(0.05f, 0.95f), heightFraction,
                            )) },
                            modifier = Modifier.fillMaxWidth().testTag("annotate_add_highlight"),
                        ) { Text(stringResource(R.string.annotate_pdf_add_highlight)) }
                    }
                }
                Text(
                    stringResource(R.string.annotate_pdf_items, annotations.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp).testTag("annotate_item_count"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(
                        enabled = annotations.isNotEmpty(),
                        onClick = {
                            redoAnnotations = listOf(annotations.last()) + redoAnnotations
                            annotations = annotations.dropLast(1)
                        },
                        modifier = Modifier.testTag("annotate_undo"),
                    ) { Text(stringResource(R.string.annotate_pdf_undo)) }
                    TextButton(
                        enabled = redoAnnotations.isNotEmpty(),
                        onClick = {
                            annotations = annotations + redoAnnotations.first()
                            redoAnnotations = redoAnnotations.drop(1)
                        },
                        modifier = Modifier.testTag("annotate_redo"),
                    ) { Text(stringResource(R.string.annotate_pdf_redo)) }
                }
                annotations.forEachIndexed { index, annotation ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(when (annotation) {
                            is PdfAnnotationItem.FreeText -> R.string.annotate_pdf_item_text
                            is PdfAnnotationItem.Ink -> R.string.annotate_pdf_item_ink
                            is PdfAnnotationItem.Highlight -> R.string.annotate_pdf_item_highlight
                        }, annotation.pageIndex + 1))
                        TextButton(
                            onClick = {
                                annotations = annotations.filterIndexed { itemIndex, _ -> itemIndex != index }
                                redoAnnotations = emptyList()
                            },
                            modifier = Modifier.testTag("annotate_delete_$index"),
                        ) { Text(stringResource(R.string.annotate_pdf_delete)) }
                    }
                }
                if (annotations.isNotEmpty()) {
                    Text(
                        stringResource(R.string.annotate_pdf_preview),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    AnnotationSlider(
                        stringResource(R.string.annotate_pdf_zoom, (previewZoom * 100).roundToInt()),
                        previewZoom, 1f..2f, "annotate_preview_zoom", { previewZoom = it },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 380.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clipToBounds()
                            .testTag("annotate_preview"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (previewLoading) CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        preview?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = stringResource(R.string.annotate_pdf_preview_description),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 380.dp)
                                    .graphicsLayer(scaleX = previewZoom, scaleY = previewZoom),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = annotations.isNotEmpty() && preview != null,
                onClick = { onConfirm(annotations) },
                modifier = Modifier.testTag("annotate_save"),
            ) { Text(stringResource(R.string.annotate_pdf_save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("annotate_cancel")) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = Modifier.testTag("annotate_dialog"),
    )
}

@Composable
private fun PlacementSliders(
    centerX: Float,
    centerY: Float,
    width: Float,
    onCenterX: (Float) -> Unit,
    onCenterY: (Float) -> Unit,
    onWidth: (Float) -> Unit,
) {
    AnnotationSlider(
        stringResource(R.string.annotate_pdf_horizontal, (centerX * 100).roundToInt()),
        centerX, 0f..1f, "annotate_horizontal", onCenterX,
    )
    AnnotationSlider(
        stringResource(R.string.annotate_pdf_vertical, (centerY * 100).roundToInt()),
        centerY, 0f..1f, "annotate_vertical", onCenterY,
    )
    AnnotationSlider(
        stringResource(R.string.annotate_pdf_width, (width * 100).roundToInt()),
        width, 0.15f..0.9f, "annotate_width", onWidth,
    )
}

@Composable
private fun AnnotationSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    onValueChange: (Float) -> Unit,
) {
    Text(label, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun AnnotationInkPad(
    strokes: List<List<AnnotationPoint>>,
    currentStroke: List<AnnotationPoint>,
    onStrokeStarted: (AnnotationPoint) -> Unit,
    onStrokeChanged: (List<AnnotationPoint>) -> Unit,
    onStrokeFinished: (List<AnnotationPoint>) -> Unit,
) {
    val description = stringResource(R.string.annotate_pdf_drawing_description)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(top = 8.dp)
            .background(Color.White)
            .semantics { contentDescription = description }
            .testTag("annotate_ink_pad")
            .pointerInput(Unit) {
                var activeStroke = emptyList<AnnotationPoint>()
                fun normalized(position: Offset) = AnnotationPoint(
                    (position.x / size.width).coerceIn(0f, 1f),
                    (1f - position.y / size.height).coerceIn(0f, 1f),
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
                moveTo(stroke.first().x * size.width, (1f - stroke.first().y) * size.height)
                stroke.drop(1).forEach { lineTo(it.x * size.width, (1f - it.y) * size.height) }
            }
            drawPath(
                path, Color(0xFF132F55),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
