package com.readyroom.app.ui.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.readyroom.app.domain.model.Detection

/**
 * Draws detection boxes onto a copy of the captured photo.
 *
 * Unlike the live overlay — which maps frame pixels through the preview's
 * FILL_CENTER crop — this draws in the bitmap's own coordinate space, so no
 * mapping is needed. The detections were produced from this exact bitmap.
 *
 * @return a new bitmap; the caller owns it. [source] is left untouched.
 */
object PhotoAnnotator {

    /** Stroke as a fraction of the image's shorter side, so it scales. */
    private const val STROKE_RATIO = 0.006f

    private const val LABEL_TEXT_RATIO = 0.028f
    private const val LABEL_PADDING_RATIO = 0.008f

    fun annotate(source: Bitmap, detections: List<Detection>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        if (detections.isEmpty()) return output

        val canvas = Canvas(output)
        val shorterSide = minOf(output.width, output.height)

        val stroke = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = shorterSide * STROKE_RATIO
            isAntiAlias = true
        }

        val labelBackground = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val labelText = Paint().apply {
            color = Color.WHITE
            textSize = shorterSide * LABEL_TEXT_RATIO
            isAntiAlias = true
            isFakeBoldText = true
        }

        for (detection in detections) {
            val color = colorForClass(detection.className)

            stroke.color = color
            labelBackground.color = color

            val box = RectF(
                detection.left,
                detection.top,
                detection.right,
                detection.bottom,
            )
            canvas.drawRect(box, stroke)

            drawLabel(canvas, detection, box, labelBackground, labelText, shorterSide)
        }

        return output
    }

    /**
     * Draws the class name above the box, or inside it when the box is at the
     * top edge and the label would be cut off.
     */
    private fun drawLabel(
        canvas: Canvas,
        detection: Detection,
        box: RectF,
        background: Paint,
        text: Paint,
        shorterSide: Int,
    ) {
        val label = "${detection.className} ${
            String.format("%.0f%%", detection.confidence * 100)
        }"

        val padding = shorterSide * LABEL_PADDING_RATIO
        val textWidth = text.measureText(label)
        val textHeight = text.fontMetrics.let { it.descent - it.ascent }
        val labelHeight = textHeight + padding * 2

        // Prefer above the box; drop inside if there is no room.
        val labelTop = if (box.top - labelHeight >= 0f) {
            box.top - labelHeight
        } else {
            box.top
        }

        val labelRight = (box.left + textWidth + padding * 2).coerceAtMost(
            canvas.width.toFloat()
        )

        val rect = RectF(box.left, labelTop, labelRight, labelTop + labelHeight)
        canvas.drawRect(rect, background)

        val baseline = labelTop + padding - text.fontMetrics.ascent
        canvas.drawText(label, box.left + padding, baseline, text)
    }

    /** Colour per hazard group, matching the live overlay. */
    private fun colorForClass(className: String): Int = when (className) {
        "shelf", "wardrobe", "dresser", "refrigerator", "tv",
        "ceiling_fan", "wall_art",
        -> 0xFFFF7043.toInt()

        "window", "mirror" -> 0xFF42A5F5.toInt()
        "table" -> 0xFF66BB6A.toInt()
        else -> 0xFFFFEE58.toInt()
    }
}
