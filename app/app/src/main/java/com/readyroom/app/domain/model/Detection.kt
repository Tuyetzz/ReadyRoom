package com.readyroom.app.domain.model

/**
 * One detected object in a camera frame.
 *
 * Coordinates are in **source bitmap space** (pixels), not the model's
 * letterboxed 640x640 space — see [com.readyroom.app.data.detector.YoloDecoder].
 */
data class Detection(
    val classId: Int,
    val className: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Scale and padding applied when fitting a frame into the model's square input.
 *
 * Kept so detections can be mapped back to the original frame: a box in
 * model space must have the padding removed and the scale undone.
 */
data class LetterboxTransform(
    val scale: Float,
    val padX: Float,
    val padY: Float,
) {
    /** Maps an x coordinate in model space back to source pixels. */
    fun toSourceX(modelX: Float): Float = (modelX - padX) / scale

    /** Maps a y coordinate in model space back to source pixels. */
    fun toSourceY(modelY: Float): Float = (modelY - padY) / scale
}
