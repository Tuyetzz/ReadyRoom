package com.readyroom.app.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the frame-to-view coordinate mapping.
 *
 * The maths here is what puts boxes in the right place on screen; an error
 * shows up as boxes drifting off the objects they describe.
 */
class OverlayMappingTest {

    @Test
    fun `identical aspect ratios map one to one`() {
        // Frame and view are both 4:3 — no crop, only scaling.
        val m = OverlayMapping.fillCenter(
            frameWidth = 640,
            frameHeight = 480,
            viewWidth = 320f,
            viewHeight = 240f,
        )

        assertEquals(0.5f, m.scale, 1e-4f)
        assertEquals(0f, m.offsetX, 1e-4f)
        assertEquals(0f, m.offsetY, 1e-4f)
        assertEquals(160f, m.mapX(320f), 1e-4f)
        assertEquals(120f, m.mapY(240f), 1e-4f)
    }

    @Test
    fun `taller frame than view is cropped vertically`() {
        // Frame 4:3 (640x480) into a 300x300 square view.
        // scale = max(300/640, 300/480) = max(0.469, 0.625) = 0.625
        // scaled frame = 400x300 -> offsetX = (300-400)/2 = -50, offsetY = 0
        val m = OverlayMapping.fillCenter(
            frameWidth = 640,
            frameHeight = 480,
            viewWidth = 300f,
            viewHeight = 300f,
        )

        assertEquals(0.625f, m.scale, 1e-4f)
        assertEquals(-50f, m.offsetX, 1e-4f)
        assertEquals(0f, m.offsetY, 1e-4f)

        // Frame centre lands at view centre.
        assertEquals(150f, m.mapX(320f), 1e-3f)
        assertEquals(150f, m.mapY(240f), 1e-3f)
    }

    @Test
    fun `wider frame than view is cropped horizontally`() {
        // Frame 16:9 (1600x900) into a 400x400 view.
        // scale = max(0.25, 0.444) = 0.444...
        // scaled = 711.1 x 400 -> offsetX = (400-711.1)/2 = -155.56, offsetY = 0
        val m = OverlayMapping.fillCenter(
            frameWidth = 1600,
            frameHeight = 900,
            viewWidth = 400f,
            viewHeight = 400f,
        )

        assertEquals(400f / 900f, m.scale, 1e-4f)
        assertEquals(0f, m.offsetY, 1e-3f)
        assertEquals(200f, m.mapX(800f), 1e-2f)
        assertEquals(200f, m.mapY(450f), 1e-2f)
    }

    @Test
    fun `frame centre always lands at view centre`() {
        val cases = listOf(
            Triple(640, 480, 300f to 500f),
            Triple(1920, 1080, 400f to 400f),
            Triple(480, 640, 800f to 300f),
        )

        for ((fw, fh, view) in cases) {
            val (vw, vh) = view
            val m = OverlayMapping.fillCenter(fw, fh, vw, vh)

            assertEquals(
                "centre x for ${fw}x$fh in ${vw}x$vh",
                vw / 2f, m.mapX(fw / 2f), 1e-2f,
            )
            assertEquals(
                "centre y for ${fw}x$fh in ${vw}x$vh",
                vh / 2f, m.mapY(fh / 2f), 1e-2f,
            )
        }
    }

    @Test
    fun `frame corners are covered or beyond the view edges`() {
        // Under FILL_CENTER the scaled frame must cover the whole view, so
        // every view corner lies inside the mapped frame rectangle.
        val m = OverlayMapping.fillCenter(
            frameWidth = 640,
            frameHeight = 480,
            viewWidth = 300f,
            viewHeight = 700f,
        )

        val left = m.mapX(0f)
        val top = m.mapY(0f)
        val right = m.mapX(640f)
        val bottom = m.mapY(480f)

        // assertTrue, not assert(): the JVM disables assert() by default, so
        // these would silently pass and verify nothing.
        assertTrue("left edge $left should be at or beyond 0", left <= 0f)
        assertTrue("top edge $top should be at or beyond 0", top <= 0f)
        assertTrue("right edge $right should cover view width", right >= 300f)
        assertTrue("bottom edge $bottom should cover view height", bottom >= 700f)
    }

    @Test
    fun `degenerate sizes fall back to identity`() {
        val zeroFrame = OverlayMapping.fillCenter(0, 0, 300f, 300f)
        assertEquals(1f, zeroFrame.scale, 1e-4f)

        val zeroView = OverlayMapping.fillCenter(640, 480, 0f, 0f)
        assertEquals(1f, zeroView.scale, 1e-4f)
    }
}
