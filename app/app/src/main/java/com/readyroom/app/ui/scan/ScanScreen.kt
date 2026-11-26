package com.readyroom.app.ui.scan

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.readyroom.app.data.camera.CameraController
import com.readyroom.app.data.camera.FrameResult
import com.readyroom.app.data.detector.ObjectDetector
import com.readyroom.app.domain.model.CapturedScan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Live camera with detection boxes, plus a shutter.
 *
 * After a capture the screen switches to a review state showing the annotated
 * photo, with the option to keep it (which hands it to [onScanAccepted]) or
 * retake. The live preview is unbound while reviewing so the camera is not
 * held open for nothing.
 */
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onScanAccepted: (CapturedScan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<FrameResult?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf<CapturedScan?>(null) }

    // Built once: loading the model is expensive and must not repeat on
    // recomposition.
    val detector = remember { ObjectDetector.create(context) }
    val controller = remember { CameraController(context, detector) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        controller.start(
            owner = lifecycleOwner,
            // The PreviewView owns the SurfaceProvider; hand it to the Preview.
            onPreviewReady = { preview -> preview.setSurfaceProvider(previewView.surfaceProvider) },
            onError = { error = it },
        )

        val job = scope.launch {
            controller.results?.collect { results = it }
        }

        onDispose {
            job.cancel()
            scope.cancel()
            controller.stop()
            detector.close()
        }
    }

    val shot = captured
    if (shot != null) {
        ReviewContent(
            scan = shot,
            onRetake = {
                shot.bitmap.recycle()
                captured = null
            },
            onAccept = { onScanAccepted(shot) },
            modifier = modifier,
        )
    } else {
        CaptureContent(
            previewView = previewView,
            results = results,
            error = error,
            capturing = capturing,
            onBack = onBack,
            onShutter = {
                capturing = true
                error = null
                controller.capture(
                    onCaptured = {
                        captured = it
                        capturing = false
                    },
                    onError = {
                        error = it
                        capturing = false
                    },
                )
            },
            modifier = modifier,
        )
    }
}

/* ---------------------------------------------------------------- capture */

@Composable
private fun CaptureContent(
    previewView: PreviewView,
    results: FrameResult?,
    error: String?,
    capturing: Boolean,
    onBack: () -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        DetectionOverlay(
            results = results,
            modifier = Modifier.fillMaxSize(),
        )

        OverlayIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = "Back",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        StatusBar(
            results = results,
            error = error,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = 132.dp),
        )

        ShutterButton(
            capturing = capturing,
            onClick = onShutter,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
        )
    }
}

@Composable
private fun ShutterButton(
    capturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(76.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer ring.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(4.dp, Color.White, CircleShape)
        )

        Surface(
            color = if (capturing) Color.Gray else Color.White,
            shape = CircleShape,
            modifier = Modifier
                .size(60.dp)
                .clickable(enabled = !capturing, onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (capturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color.DarkGray,
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- review */

@Composable
private fun ReviewContent(
    scan: CapturedScan,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Drawn once per photo: annotation allocates a full-size bitmap.
    val annotated = remember(scan.bitmap) {
        PhotoAnnotator.annotate(scan.bitmap, scan.detections)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = annotated.asImageBitmap(),
                contentDescription = "Captured room",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        ReviewSummary(
            detections = scan.detections.size,
            onRetake = onRetake,
            onAccept = onAccept,
        )
    }
}

@Composable
private fun ReviewSummary(
    detections: Int,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
) {
    Surface(
        color = Color(0xFF121212),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (detections == 0) {
                    "No objects detected in this photo"
                } else {
                    "$detections object${if (detections == 1) "" else "s"} detected"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "Risk scoring is not wired up yet — this photo has " +
                    "been labelled but not assessed.",
                color = Color(0xFFB0B0B0),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2A2A),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Retake")
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Use photo")
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- shared */

@Composable
private fun OverlayIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = CircleShape,
        modifier = modifier,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color.White,
            )
        }
    }
}

/**
 * Draws one rectangle per detection.
 *
 * Mapping is recomputed from the draw scope's size each frame, so it stays
 * correct through rotation and window resizing.
 */
@Composable
private fun DetectionOverlay(
    results: FrameResult?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val frame = results ?: return@Canvas
        if (frame.detections.isEmpty()) return@Canvas

        val mapping = OverlayMapping.fillCenter(
            frameWidth = frame.frameWidth,
            frameHeight = frame.frameHeight,
            viewWidth = size.width,
            viewHeight = size.height,
        )

        for (detection in frame.detections) {
            val left = mapping.mapX(detection.left)
            val top = mapping.mapY(detection.top)
            val right = mapping.mapX(detection.right)
            val bottom = mapping.mapY(detection.bottom)

            drawRect(
                color = colorForClass(detection.className),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

/** Colour per hazard group, matching the grouping in the README. */
private fun colorForClass(className: String): Color = when (className) {
    // Topple / fall
    "shelf", "wardrobe", "dresser", "refrigerator", "tv",
    "ceiling_fan", "wall_art",
    -> Color(0xFFFF7043)

    // Glass / shatter
    "window", "mirror" -> Color(0xFF42A5F5)

    // Cover candidate
    "table" -> Color(0xFF66BB6A)

    // Context
    else -> Color(0xFFFFEE58)
}

@Composable
private fun StatusBar(
    results: FrameResult?,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when {
                error != null -> Text(
                    text = error,
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.bodySmall,
                )

                results == null -> Text(
                    text = "Point the camera at a room…",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )

                else -> {
                    val summary = results.detections
                        .groupBy { it.className }
                        .map { (name, hits) -> "${hits.size}× $name" }
                        .joinToString(", ")

                    Text(
                        text = summary.ifEmpty { "Nothing detected yet" },
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
