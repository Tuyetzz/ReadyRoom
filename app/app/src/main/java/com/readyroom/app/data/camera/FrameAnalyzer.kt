package com.readyroom.app.data.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.readyroom.app.data.detector.ObjectDetector
import com.readyroom.app.domain.model.Detection
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the scan screen needs to draw. */
data class FrameResult(
    /** Detections in the analysed bitmap's pixel space. */
    val detections: List<Detection>,
    /** Size of the bitmap those detections are relative to. */
    val frameWidth: Int,
    val frameHeight: Int,
)

/**
 * Runs detection on each camera frame and publishes the result.
 *
 * Pair with `STRATEGY_KEEP_ONLY_LATEST` so frames are dropped rather than
 * queued when inference cannot keep up — a backlog would draw stale boxes over
 * live video, which reads as lag rather than as a lower detection rate.
 *
 * A frame is skipped while the previous inference is still running, so a slow
 * device degrades to fewer detections per second instead of unbounded latency.
 */
class FrameAnalyzer(
    private val detector: ObjectDetector,
) : ImageAnalysis.Analyzer {

    private val _results = MutableStateFlow<FrameResult?>(null)
    val results: StateFlow<FrameResult?> = _results.asStateFlow()

    private val busy = AtomicBoolean(false)

    /**
     * Analyses one frame.
     *
     * `ImageProxy.toBitmap()` returns the frame already rotated to match the
     * display, so detection coordinates line up with the preview without any
     * extra rotation handling.
     */
    override fun analyze(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }

        try {
            val bitmap = image.toBitmap()
            val detections = detector.detect(bitmap)

            _results.value = FrameResult(
                detections = detections,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height,
            )

            // toBitmap() allocates a new Bitmap; recycle it once copied out.
            bitmap.recycle()
        } catch (e: Exception) {
            // One bad frame should not kill the analysis pipeline.
            Log.w(TAG, "frame dropped: ${e.message}")
        } finally {
            busy.set(false)
            image.close()
        }
    }

    private companion object {
        const val TAG = "FrameAnalyzer"
    }
}
