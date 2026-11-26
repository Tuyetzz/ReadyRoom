package com.readyroom.app.data.detector

import com.readyroom.app.domain.model.Detection
import com.readyroom.app.domain.model.LetterboxTransform
import kotlin.math.max
import kotlin.math.min

/**
 * Turns raw YOLO output into [Detection]s.
 *
 * The exported model (see `assets/model_info.json`) has:
 * - input  `float32 [1, 3, 640, 640]` — **NCHW**, RGB, 0..1, letterboxed
 * - output `float32 [1, 17, 8400]` — rows 0..3 are `cx, cy, w, h`, rows 4..16
 *   are the 13 class scores, all normalised to 0..1 against the 640 input
 *
 * Pure functions only, so this is unit-testable without a device or a model.
 */
object YoloDecoder {

    /** Model input side length. Must match the exported graph. */
    const val INPUT_SIZE = 640

    /** Boxes below this class score are discarded. From `model_info.json`. */
    const val CONFIDENCE_THRESHOLD = 0.3f

    /** IoU above which two boxes of the same class are treated as duplicates. */
    const val IOU_THRESHOLD = 0.7f

    /**
     * Builds the letterbox transform for a source frame.
     *
     * The frame is scaled to fit inside [INPUT_SIZE] preserving aspect ratio,
     * then centred with padding. Mirrors the training preprocessing.
     */
    fun letterboxFor(sourceWidth: Int, sourceHeight: Int): LetterboxTransform {
        val scale = min(
            INPUT_SIZE.toFloat() / sourceWidth,
            INPUT_SIZE.toFloat() / sourceHeight,
        )
        val scaledW = sourceWidth * scale
        val scaledH = sourceHeight * scale
        return LetterboxTransform(
            scale = scale,
            padX = (INPUT_SIZE - scaledW) / 2f,
            padY = (INPUT_SIZE - scaledH) / 2f,
        )
    }

    /**
     * Decodes model output into detections in source-frame pixels.
     *
     * @param output the model's `[17][8400]` array, flattened row-major
     * @param labels class names indexed by class id
     * @param transform the letterbox applied before inference
     */
    fun decode(
        output: Array<FloatArray>,
        labels: List<String>,
        transform: LetterboxTransform,
        confidenceThreshold: Float = CONFIDENCE_THRESHOLD,
        iouThreshold: Float = IOU_THRESHOLD,
    ): List<Detection> {
        if (output.size < 4) return emptyList()

        val candidates = mutableListOf<Detection>()
        val boxCount = output[0].size

        for (i in 0 until boxCount) {
            // Rows 4.. are class scores. Pick the best one.
            var bestClass = -1
            var bestScore = 0f
            for (c in 4 until output.size) {
                val score = output[c][i]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c - 4
                }
            }
            if (bestClass < 0 || bestScore < confidenceThreshold) continue

            // cx, cy, w, h are normalised 0..1 against the 640 input.
            val cx = output[0][i] * INPUT_SIZE
            val cy = output[1][i] * INPUT_SIZE
            val w = output[2][i] * INPUT_SIZE
            val h = output[3][i] * INPUT_SIZE

            val left = transform.toSourceX(cx - w / 2f)
            val top = transform.toSourceY(cy - h / 2f)
            val right = transform.toSourceX(cx + w / 2f)
            val bottom = transform.toSourceY(cy + h / 2f)

            candidates += Detection(
                classId = bestClass,
                className = labels.getOrElse(bestClass) { "unknown" },
                confidence = bestScore,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
            )
        }

        return nonMaxSuppression(candidates, iouThreshold)
    }

    /**
     * Removes overlapping duplicates, keeping the highest-confidence box.
     *
     * Compared per class: a table overlapping a window is not a duplicate.
     */
    fun nonMaxSuppression(
        detections: List<Detection>,
        iouThreshold: Float = IOU_THRESHOLD,
    ): List<Detection> {
        val kept = mutableListOf<Detection>()

        for (candidate in detections.sortedByDescending { it.confidence }) {
            val overlapsKept = kept.any { existing ->
                existing.classId == candidate.classId &&
                    intersectionOverUnion(existing, candidate) > iouThreshold
            }
            if (!overlapsKept) kept += candidate
        }

        return kept
    }

    /** Standard IoU. Returns 0 for boxes with no overlap. */
    fun intersectionOverUnion(a: Detection, b: Detection): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interW = interRight - interLeft
        val interH = interBottom - interTop
        if (interW <= 0f || interH <= 0f) return 0f

        val intersection = interW * interH
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
