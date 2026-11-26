package com.readyroom.app.data.detector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.readyroom.app.domain.model.Detection
import com.readyroom.app.domain.model.LetterboxTransform
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

/**
 * Runs the bundled YOLO model on-device.
 *
 * The model is **NCHW** — input `float32 [1, 3, 640, 640]`. That is the
 * opposite of the usual TFLite layout, so the buffer is written plane by plane
 * (`[all R][all G][all B]`), not pixel-interleaved. Writing the conventional
 * NHWC order here would compile, run, and quietly produce nonsense detections.
 *
 * Inference is synchronous and must be called off the main thread.
 */
class ObjectDetector private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
) {

    /** Reused input buffer: `1 * 3 * 640 * 640` floats. */
    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(INPUT_ELEMENTS * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    /** Reused output buffer: `[1][17][8400]`. */
    private val outputBuffer: Array<Array<FloatArray>> =
        Array(1) { Array(OUTPUT_ROWS) { FloatArray(OUTPUT_BOXES) } }

    /** Reused scratch for scaled pixels, grown as needed. */
    private var pixelScratch = IntArray(0)

    /**
     * Detects objects in [bitmap].
     *
     * @return detections in [bitmap]'s pixel space, already NMS-filtered.
     */
    fun detect(bitmap: Bitmap): List<Detection> {
        val transform = YoloDecoder.letterboxFor(bitmap.width, bitmap.height)

        fillInputBuffer(bitmap, transform)
        interpreter.run(inputBuffer, outputBuffer)

        return YoloDecoder.decode(
            output = outputBuffer[0],
            labels = labels,
            transform = transform,
        )
    }

    /** Releases native resources. Safe to call once. */
    fun close() = interpreter.close()

    /**
     * Writes the letterboxed frame into [inputBuffer] in NCHW order.
     *
     * Padding is 114/255, matching the export's training preprocessing.
     */
    private fun fillInputBuffer(bitmap: Bitmap, transform: LetterboxTransform) {
        inputBuffer.rewind()
        val floats = inputBuffer.asFloatBuffer()

        val scaledW = (bitmap.width * transform.scale).roundToInt().coerceAtLeast(1)
        val scaledH = (bitmap.height * transform.scale).roundToInt().coerceAtLeast(1)
        val padX = ((INPUT_SIZE - scaledW) / 2f).roundToInt()
        val padY = ((INPUT_SIZE - scaledH) / 2f).roundToInt()

        // Start with every plane at the pad colour.
        floats.put(FloatArray(INPUT_ELEMENTS) { PAD_VALUE })

        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)

        val needed = scaledW * scaledH
        if (pixelScratch.size < needed) pixelScratch = IntArray(needed)
        scaled.getPixels(pixelScratch, 0, scaledW, 0, 0, scaledW, scaledH)
        if (scaled !== bitmap) scaled.recycle()

        val planeStride = INPUT_SIZE * INPUT_SIZE
        for (y in 0 until scaledH) {
            val destY = y + padY
            if (destY < 0 || destY >= INPUT_SIZE) continue
            for (x in 0 until scaledW) {
                val destX = x + padX
                if (destX < 0 || destX >= INPUT_SIZE) continue

                val argb = pixelScratch[y * scaledW + x]
                // NCHW: same pixel index within each of the three planes.
                val index = destY * INPUT_SIZE + destX
                floats.put(0 * planeStride + index, ((argb shr 16) and 0xFF) / 255f)
                floats.put(1 * planeStride + index, ((argb shr 8) and 0xFF) / 255f)
                floats.put(2 * planeStride + index, (argb and 0xFF) / 255f)
            }
        }

        inputBuffer.rewind()
    }

    companion object {
        private const val TAG = "ObjectDetector"

        /** Must match the exported graph's input side. */
        private const val INPUT_SIZE = YoloDecoder.INPUT_SIZE

        private const val INPUT_ELEMENTS = 3 * INPUT_SIZE * INPUT_SIZE

        /** 4 box values + 13 class scores. */
        private const val OUTPUT_ROWS = 17

        /** YOLO's fixed candidate count for a 640 input. */
        private const val OUTPUT_BOXES = 8400

        /** Letterbox pad colour used in training: 114/255. */
        private const val PAD_VALUE = 114f / 255f

        private const val MODEL_ASSET = "ready_room.tflite"
        private const val LABELS_ASSET = "labels.txt"

        /**
         * Loads the model and labels from assets.
         *
         * The model is memory-mapped rather than read into a byte array, so the
         * 9.9 MB file is not duplicated on the heap. That requires the asset to
         * be stored uncompressed in the APK, which `androidResources.noCompress`
         * in `build.gradle.kts` guarantees.
         */
        fun create(context: Context): ObjectDetector {
            val model = loadModelFile(context)
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            }
            val interpreter = Interpreter(model, options)
            val labels = loadLabels(context)

            Log.d(TAG, "model ready, ${labels.size} labels, ${interpreter.inputTensorCount} inputs")

            return ObjectDetector(interpreter, labels)
        }

        private fun loadModelFile(context: Context): ByteBuffer {
            val descriptor = context.assets.openFd(MODEL_ASSET)
            FileInputStream(descriptor.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                )
            }
        }

        /**
         * Reads class names, one per line.
         *
         * Order matters: index 0 is the first line, and that index is what the
         * model's output rows 4..16 mean. A reordered file silently mislabels
         * every detection.
         */
        private fun loadLabels(context: Context): List<String> =
            context.assets.open(LABELS_ASSET).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }
    }
}
