package com.rameshta.quietpdf.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageZoomStateTest {
    private val viewport = Size(width = 1_000f, height = 1_400f)

    @Test
    fun transform_clampsScaleAndPanToVisibleContent() {
        val transformed = PageZoomState().transform(
            zoomChange = 10f,
            panChange = Offset(10_000f, -10_000f),
            viewportSize = viewport,
        )

        assertEquals(PageZoomState.MaximumScale, transformed.scale, 0.001f)
        assertEquals(1_500f, transformed.offset.x, 0.001f)
        assertEquals(-2_100f, transformed.offset.y, 0.001f)
    }

    @Test
    fun transform_keepsTouchedPointStableWhileZooming() {
        val transformed = PageZoomState().transform(
            zoomChange = 2f,
            panChange = Offset.Zero,
            viewportSize = viewport,
            centroid = Offset.Zero,
        )

        assertEquals(500f, transformed.offset.x, 0.001f)
        assertEquals(700f, transformed.offset.y, 0.001f)
    }

    @Test
    fun toggle_zoomsThenResets() {
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val zoomed = PageZoomState().toggle(viewport, center)
        val reset = zoomed.toggle(viewport, center)

        assertTrue(zoomed.isZoomed)
        assertEquals(PageZoomState.DoubleTapScale, zoomed.scale, 0.001f)
        assertFalse(reset.isZoomed)
        assertEquals(Offset.Zero, reset.offset)
    }

    @Test
    fun sequentialPinchDeltasAccumulateAndReturnToFit() {
        var state = PageZoomState()
        listOf(2f, 1.5f, 4f / 3f).forEach { delta ->
            state = state.transform(delta, Offset.Zero, viewport)
        }
        assertEquals(PageZoomState.MaximumScale, state.scale, 0.001f)

        listOf(0.5f, 0.5f).forEach { delta ->
            state = state.transform(delta, Offset.Zero, viewport)
        }
        assertEquals(PageZoomState(), state)
    }
}
