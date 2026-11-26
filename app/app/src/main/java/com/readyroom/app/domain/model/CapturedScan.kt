package com.readyroom.app.domain.model

/**
 * A photo the user chose to keep.
 *
 * [detections] are the boxes the detector found in this exact frame, already in
 * [bitmap]'s pixel space — so the annotated image and the detection list can
 * never disagree about which frame they describe.
 *
 * The bitmap is in memory only. Nothing is written to disk: the user has not
 * asked to save anything, and the README's privacy section says nothing is
 * stored by default.
 */
data class CapturedScan(
    val bitmap: android.graphics.Bitmap,
    val detections: List<Detection>,
)
