package com.rameshta.quietpdf.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

data class PageZoomState(
    val scale: Float = MinimumScale,
    val offset: Offset = Offset.Zero,
) {
    val isZoomed: Boolean get() = scale > MinimumScale + ScaleTolerance

    fun transform(
        zoomChange: Float,
        panChange: Offset,
        viewportSize: Size,
        centroid: Offset = Offset(viewportSize.width / 2f, viewportSize.height / 2f),
    ): PageZoomState {
        if (viewportSize.width <= 0f || viewportSize.height <= 0f) return this
        val newScale = (scale * zoomChange).coerceIn(MinimumScale, MaximumScale)
        if (newScale <= MinimumScale + ScaleTolerance) return PageZoomState()

        val scaleRatio = newScale / scale
        val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val centerOffset = centroid - viewportCenter
        val focalOffset = offset * scaleRatio + centerOffset * (1f - scaleRatio)
        val proposedOffset = focalOffset + panChange
        val horizontalBound = (newScale - 1f) * viewportSize.width / 2f
        val verticalBound = (newScale - 1f) * viewportSize.height / 2f

        return PageZoomState(
            scale = newScale,
            offset = Offset(
                x = proposedOffset.x.coerceIn(-horizontalBound, horizontalBound),
                y = proposedOffset.y.coerceIn(-verticalBound, verticalBound),
            ),
        )
    }

    fun toggle(viewportSize: Size, tapPosition: Offset): PageZoomState =
        if (isZoomed) {
            PageZoomState()
        } else {
            transform(
                zoomChange = DoubleTapScale,
                panChange = Offset.Zero,
                viewportSize = viewportSize,
                centroid = tapPosition,
            )
        }

    companion object {
        const val MinimumScale = 1f
        const val MaximumScale = 4f
        const val DoubleTapScale = 2.5f
        private const val ScaleTolerance = 0.01f
    }
}
