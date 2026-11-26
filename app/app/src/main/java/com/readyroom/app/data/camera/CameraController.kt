package com.readyroom.app.data.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.readyroom.app.data.detector.ObjectDetector
import com.readyroom.app.domain.model.CapturedScan
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the camera and the detection pipeline.
 *
 * Frames go to both the preview and the analyzer. The analyzer runs on a
 * single background thread: inference is synchronous, so it must never run on
 * the main thread, and one thread keeps results ordered.
 */
class CameraController(
    private val context: Context,
    private val detector: ObjectDetector,
) {

    private var provider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var analyzer: FrameAnalyzer? = null
    private var imageCapture: ImageCapture? = null

    /** The most recent frame's detections, or null before the first frame. */
    val results get() = analyzer?.results

    /**
     * Binds the camera to [owner]'s lifecycle.
     *
     * @param onPreviewReady receives the `Preview` to attach to a `PreviewView`
     * @param onError receives a message if the camera could not be started
     */
    fun start(
        owner: LifecycleOwner,
        onPreviewReady: (Preview) -> Unit,
        onError: (String) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                bind(owner, cameraProvider, onPreviewReady)
            } catch (e: Exception) {
                Log.e(TAG, "camera start failed", e)
                onError(e.message ?: "Could not start the camera")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(
        owner: LifecycleOwner,
        cameraProvider: ProcessCameraProvider,
        onPreviewReady: (Preview) -> Unit,
    ) {
        // Unbind first so re-entering the screen does not throw.
        cameraProvider.unbindAll()

        val preview = Preview.Builder().build().also(onPreviewReady)

        val capture = ImageCapture.Builder()
            // Let CameraX choose the still size; the detector runs on the
            // analysis stream, not this one.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
            .also { imageCapture = it }

        val executor = Executors.newSingleThreadExecutor().also { analysisExecutor = it }

        val frameAnalyzer = FrameAnalyzer(detector).also { analyzer = it }

        val analysis = ImageAnalysis.Builder()
            // Drop frames instead of queueing them: a backlog would draw stale
            // boxes over live video.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // Hand the analyzer a Bitmap rather than raw YUV planes. For this
            // input size the copy is cheaper than writing a YUV converter, and
            // it arrives already rotated to match the display.
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            // ResolutionSelector only — setting setTargetResolution as well
            // throws during resolution, since the two configure the same thing.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    // 4:3 matches the sensor's native shape, so the preview
                    // crops away less of the frame.
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        )
                    )
                    .build()
            )
            .build()
            .also { it.setAnalyzer(executor, frameAnalyzer) }

        try {
            cameraProvider.bindToLifecycle(
                owner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                capture,
            )
        } catch (e: Exception) {
            Log.e(TAG, "bind failed", e)
            throw e
        }
    }

    /**
     * Takes a still and runs detection on it.
     *
     * The still is a different frame from the preview's analysis stream, so the
     * detector runs again on the captured image rather than reusing the live
     * detections — otherwise the boxes would describe a frame the photo does
     * not show. That is one extra inference per capture, which is fine at
     * button-press frequency.
     *
     * @param onCaptured receives the bitmap and its detections, on the main
     *   thread. The caller owns the bitmap and must recycle it.
     */
    fun capture(onCaptured: (CapturedScan) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onError("Camera is not ready yet.")
            return
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        val detections = detector.detect(bitmap)
                        onCaptured(CapturedScan(bitmap, detections))
                    } catch (e: Exception) {
                        Log.e(TAG, "capture analysis failed", e)
                        onError("Could not analyse the photo.")
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "capture failed", exception)
                    onError("Could not take the photo.")
                }
            },
        )
    }

    /** Unbinds the camera and shuts the analysis thread down. */
    fun stop() {
        provider?.unbindAll()
        imageCapture = null
        analyzer = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }

    private companion object {
        const val TAG = "CameraController"
    }
}
