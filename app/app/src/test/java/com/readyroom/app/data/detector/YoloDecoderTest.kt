package com.readyroom.app.data.detector

import com.readyroom.app.domain.model.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the YOLO decode maths against known-good values.
 *
 * These run on the JVM with hand-built model output, so they pin down the
 * coordinate handling without needing a device or the model file.
 */
class YoloDecoderTest {

    private val labels = listOf(
        "shelf", "wardrobe", "dresser", "refrigerator", "tv", "ceiling_fan",
        "wall_art", "window", "mirror", "table", "bed", "sofa", "door",
    )

    /** Builds a `[17][8400]` output with every box empty but one. */
    private fun outputWith(
        index: Int,
        cx: Float, cy: Float, w: Float, h: Float,
        classId: Int, score: Float,
    ): Array<FloatArray> {
        val out = Array(17) { FloatArray(8400) }
        out[0][index] = cx
        out[1][index] = cy
        out[2][index] = w
        out[3][index] = h
        out[4 + classId][index] = score
        return out
    }

    @Test
    fun `square frame has no padding`() {
        val t = YoloDecoder.letterboxFor(640, 640)

        assertEquals(1f, t.scale, 1e-4f)
        assertEquals(0f, t.padX, 1e-4f)
        assertEquals(0f, t.padY, 1e-4f)
    }

    @Test
    fun `wide frame is letterboxed with vertical padding`() {
        // 1280x640 -> scale 0.5, scaled to 640x320, padY = 160, padX = 0
        val t = YoloDecoder.letterboxFor(1280, 640)

        assertEquals(0.5f, t.scale, 1e-4f)
        assertEquals(0f, t.padX, 1e-4f)
        assertEquals(160f, t.padY, 1e-4f)
    }

    @Test
    fun `tall frame is letterboxed with horizontal padding`() {
        // 640x1280 -> scale 0.5, scaled to 320x640, padX = 160, padY = 0
        val t = YoloDecoder.letterboxFor(640, 1280)

        assertEquals(0.5f, t.scale, 1e-4f)
        assertEquals(160f, t.padX, 1e-4f)
        assertEquals(0f, t.padY, 1e-4f)
    }

    @Test
    fun `decodes a centred box back to source pixels`() {
        // Unit transform: 640x640 source, box centred, half the frame wide.
        val transform = YoloDecoder.letterboxFor(640, 640)
        val output = outputWith(
            index = 0,
            cx = 0.5f, cy = 0.5f, w = 0.5f, h = 0.25f,
            classId = 9, score = 0.9f, // table
        )

        val detections = YoloDecoder.decode(output, labels, transform)

        assertEquals(1, detections.size)
        val d = detections.single()
        assertEquals("table", d.className)
        assertEquals(9, d.classId)
        assertEquals(0.9f, d.confidence, 1e-4f)
        // cx 0.5*640 = 320, w 0.5*640 = 320 -> left 160, right 480
        assertEquals(160f, d.left, 1e-3f)
        assertEquals(480f, d.right, 1e-3f)
        // cy 320, h 160 -> top 240, bottom 400
        assertEquals(240f, d.top, 1e-3f)
        assertEquals(400f, d.bottom, 1e-3f)
    }

    @Test
    fun `undoes letterbox padding when mapping back to a wide frame`() {
        // 1280x640 source: scale 0.5, padY 160. A box at model y=320 maps to
        // source y = (320 - 160) / 0.5 = 320.
        val transform = YoloDecoder.letterboxFor(1280, 640)
        val output = outputWith(
            index = 0,
            cx = 0.5f, cy = 0.5f, w = 0.1f, h = 0.1f,
            classId = 1, score = 0.8f, // wardrobe
        )

        val d = YoloDecoder.decode(output, labels, transform).single()

        assertEquals(640f, d.centerX, 1e-2f)
        assertEquals(320f, d.centerY, 1e-2f)
        // Width: 0.1*640 = 64 model px -> /0.5 = 128 source px
        assertEquals(128f, d.width, 1e-2f)
    }

    @Test
    fun `drops boxes below the confidence threshold`() {
        val transform = YoloDecoder.letterboxFor(640, 640)
        val output = outputWith(
            index = 0,
            cx = 0.5f, cy = 0.5f, w = 0.2f, h = 0.2f,
            classId = 9, score = 0.29f, // just under 0.3
        )

        assertTrue(YoloDecoder.decode(output, labels, transform).isEmpty())
    }

    @Test
    fun `picks the highest scoring class for a box`() {
        val transform = YoloDecoder.letterboxFor(640, 640)
        val output = Array(17) { FloatArray(8400) }
        output[0][0] = 0.5f; output[1][0] = 0.5f
        output[2][0] = 0.2f; output[3][0] = 0.2f
        output[4 + 9][0] = 0.55f   // table
        output[4 + 10][0] = 0.75f  // bed — should win

        val d = YoloDecoder.decode(output, labels, transform).single()

        assertEquals("bed", d.className)
        assertEquals(0.75f, d.confidence, 1e-4f)
    }

    @Test
    fun `unknown class ids do not crash`() {
        val transform = YoloDecoder.letterboxFor(640, 640)
        val output = Array(17) { FloatArray(8400) }
        output[0][0] = 0.5f; output[1][0] = 0.5f
        output[2][0] = 0.2f; output[3][0] = 0.2f
        output[4 + 12][0] = 0.9f // door, but with an empty label list

        val d = YoloDecoder.decode(output, emptyList(), transform).single()

        assertEquals("unknown", d.className)
    }

    @Test
    fun `nms keeps the higher confidence box of an overlapping pair`() {
        val strong = Detection(9, "table", 0.9f, 0f, 0f, 100f, 100f)
        val weak = Detection(9, "table", 0.5f, 5f, 5f, 105f, 105f)

        val kept = YoloDecoder.nonMaxSuppression(listOf(weak, strong))

        assertEquals(1, kept.size)
        assertEquals(0.9f, kept.single().confidence, 1e-4f)
    }

    @Test
    fun `nms keeps boxes of different classes even when they overlap`() {
        // A table in front of a window is two real objects, not a duplicate.
        val table = Detection(9, "table", 0.9f, 0f, 0f, 100f, 100f)
        val window = Detection(7, "window", 0.8f, 0f, 0f, 100f, 100f)

        val kept = YoloDecoder.nonMaxSuppression(listOf(table, window))

        assertEquals(2, kept.size)
    }

    @Test
    fun `nms keeps non-overlapping boxes of the same class`() {
        val a = Detection(9, "table", 0.9f, 0f, 0f, 50f, 50f)
        val b = Detection(9, "table", 0.8f, 200f, 200f, 250f, 250f)

        assertEquals(2, YoloDecoder.nonMaxSuppression(listOf(a, b)).size)
    }

    @Test
    fun `iou is zero for disjoint boxes`() {
        val a = Detection(0, "shelf", 0.9f, 0f, 0f, 10f, 10f)
        val b = Detection(0, "shelf", 0.9f, 100f, 100f, 110f, 110f)

        assertEquals(0f, YoloDecoder.intersectionOverUnion(a, b), 1e-6f)
    }

    @Test
    fun `iou is one for identical boxes`() {
        val a = Detection(0, "shelf", 0.9f, 0f, 0f, 10f, 10f)

        assertEquals(1f, YoloDecoder.intersectionOverUnion(a, a), 1e-6f)
    }

    @Test
    fun `iou matches a hand-computed overlap`() {
        // Two 10x10 boxes offset by 5 in x: intersection 5x10 = 50,
        // union 100 + 100 - 50 = 150 -> 1/3
        val a = Detection(0, "shelf", 0.9f, 0f, 0f, 10f, 10f)
        val b = Detection(0, "shelf", 0.9f, 5f, 0f, 15f, 10f)

        assertEquals(1f / 3f, YoloDecoder.intersectionOverUnion(a, b), 1e-4f)
    }

    @Test
    fun `handles output with no boxes`() {
        val transform = YoloDecoder.letterboxFor(640, 640)
        val empty = Array(17) { FloatArray(0) }

        assertTrue(YoloDecoder.decode(empty, labels, transform).isEmpty())
    }
}
