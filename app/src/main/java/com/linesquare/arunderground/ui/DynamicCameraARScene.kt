package com.linesquare.arunderground.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.linesquare.arunderground.ar.ARProjectionEngine
import com.linesquare.arunderground.ar.DeviceOrientation
import com.linesquare.arunderground.ar.ProjectedComponent
import com.linesquare.arunderground.model.AlignmentOffset
import com.linesquare.arunderground.model.BuildingComponent
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic AR Camera Scene with Real-Time Gyro-Fusion Projection & 3D Dimension Overlays.
 * Tracks device motion in 3D and renders building components with live perspective and dimensional callouts.
 * Built with full fault-tolerant crash shields across CameraX, Canvas, and gesture subsystems.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun DynamicCameraARScene(
    components: List<BuildingComponent>,
    selectedComponent: BuildingComponent?,
    deviceOrientation: DeviceOrientation,
    alignmentOffset: AlignmentOffset,
    showDimensionBadges: Boolean,
    showDimensionLines: Boolean,
    onComponentSelected: (BuildingComponent) -> Unit,
    isCameraFeedActive: Boolean = true,
    onError: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val textMeasurer = rememberTextMeasurer()

    // Stable state references for gestures so pointerInput doesn't cancel or crash on 60 FPS sensor updates
    val currentComponents by rememberUpdatedState(components)
    val currentOrientation by rememberUpdatedState(deviceOrientation)
    val currentOffset by rememberUpdatedState(alignmentOffset)
    val currentShowBadges by rememberUpdatedState(showDimensionBadges)
    val onSelectCallback by rememberUpdatedState(onComponentSelected)

    // Camera Lifecycle Clean-up Shield
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                provider.unbindAll()
            } catch (t: Throwable) {
                // Ignore disposal errors
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Live Camera Feed Background
        if (isCameraFeedActive) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }

                    try {
                        val hasCam = ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasCam) {
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } else {
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    }

                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                                } catch (e: Throwable) {
                                    onError("Camera preview fallback: ${e.localizedMessage}")
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        } else {
                            onError("Awaiting camera permissions")
                        }
                    } catch (e: Throwable) {
                        onError("Camera initialization fallback: ${e.localizedMessage}")
                    }

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dynamic 3D AR Overlay Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        try {
                            val projected = ARProjectionEngine.projectComponents(
                                components = currentComponents,
                                deviceOrientation = currentOrientation,
                                alignmentOffset = currentOffset,
                                screenWidth = size.width.toFloat(),
                                screenHeight = size.height.toFloat()
                            )

                            // Check tap hit against badges first, then lines
                            for (item in projected) {
                                if (!item.isVisible || !item.isFrontFacing) continue

                                // Badge hit test
                                if (currentShowBadges) {
                                    val b = item.badgeScreen
                                    val badgeWidth = 240f
                                    val badgeHeight = 70f
                                    if (tapOffset.x in (b.x - badgeWidth / 2)..(b.x + badgeWidth / 2) &&
                                        tapOffset.y in (b.y - badgeHeight / 2)..(b.y + badgeHeight / 2)
                                    ) {
                                        onSelectCallback(item.component)
                                        return@detectTapGestures
                                    }
                                }

                                // Line segment hit test
                                val dist = distanceToSegment(tapOffset, item.startScreen, item.endScreen)
                                if (dist < 45f) {
                                    onSelectCallback(item.component)
                                    return@detectTapGestures
                                }
                            }
                        } catch (t: Throwable) {
                            // Suppress tap gesture exception
                        }
                    }
                }
        ) {
            try {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) return@Canvas

                // In simulation mode without camera, render dark horizon background
                if (!isCameraFeedActive) {
                    drawRect(color = Color(0xF00B1120))

                    // Floor grid reference
                    val horizonY = height * 0.6f
                    drawLine(
                        color = Color(0x33FFFFFF),
                        start = Offset(0f, horizonY),
                        end = Offset(width, horizonY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                    )
                }

                // Project 3D building components dynamically using live sensors
                val projectedList = ARProjectionEngine.projectComponents(
                    components = components,
                    deviceOrientation = deviceOrientation,
                    alignmentOffset = alignmentOffset,
                    screenWidth = width,
                    screenHeight = height
                )

                // Take the nearest 12 visible components to guarantee 60 FPS performance
                val visibleToRender = projectedList
                    .filter { it.isVisible && it.isFrontFacing }
                    .take(12)

                for (projected in visibleToRender) {
                    if (!projected.startScreen.x.isFinite() || !projected.startScreen.y.isFinite() ||
                        !projected.endScreen.x.isFinite() || !projected.endScreen.y.isFinite()) continue

                    val comp = projected.component
                    val isSelected = selectedComponent?.id == comp.id
                    val color = comp.color
                    val strokeThickness = projected.strokeWidthPx

                    // Step 1: Draw true 3D volumetric mesh and details
                    renderComponentGeometry(
                        projected = projected,
                        isSelected = isSelected,
                        color = color,
                        strokeThickness = strokeThickness
                    )

                    // Step 2: Draw CAD dimension extension lines & measurement callouts
                    if (showDimensionLines || isSelected) {
                        renderCadDimensionLines(
                            projected = projected,
                            isSelected = isSelected,
                            textMeasurer = textMeasurer
                        )
                    }

                    // Step 3: Draw Dynamic Floating 3D Dimension Badge
                    if (showDimensionBadges || isSelected) {
                        renderDimensionBadge(
                            projected = projected,
                            isSelected = isSelected,
                            textMeasurer = textMeasurer
                        )
                    }
                }

                // If user is turned away from the 3D room wall, draw directional arrow guiding back
                if (visibleToRender.isEmpty()) {
                    drawDirectionalGuide(width = width, height = height)
                }

                // Center AR Reticle & Dynamic Sensor Diagnostics
                renderCenterReticle(
                    width = width,
                    height = height,
                    deviceOrientation = deviceOrientation
                )
            } catch (t: Throwable) {
                // Safeguard against any internal render glitches
            }
        }
    }
}

/**
 * Draws true 3D volumetric mesh faces, shaded planes, and engineering details (flanges, valves, rungs, cables).
 */
private fun DrawScope.renderComponentGeometry(
    projected: ProjectedComponent,
    isSelected: Boolean,
    color: Color,
    strokeThickness: Float
) {
    try {
        val sx = projected.startScreen.x
        val sy = projected.startScreen.y
        val ex = projected.endScreen.x
        val ey = projected.endScreen.y

        // Render 3D Volumetric Mesh Faces (Duct walls, pipe cylinder strips, tray beds, columns)
        if (projected.meshFaces.isNotEmpty()) {
            for (face in projected.meshFaces) {
                if (face.vertices.size >= 3) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(face.vertices[0].x, face.vertices[0].y)
                        for (i in 1 until face.vertices.size) {
                            lineTo(face.vertices[i].x, face.vertices[i].y)
                        }
                        close()
                    }
                    drawPath(
                        path = path,
                        color = if (isSelected) face.fillColor.copy(alpha = 0.70f) else face.fillColor
                    )
                    drawPath(
                        path = path,
                        color = if (isSelected) Color.White else face.strokeColor,
                        style = Stroke(width = if (isSelected) face.strokeWidth + 1.0f else face.strokeWidth)
                    )
                }
            }
        }

        // Render 3D Details (Pipe flanges, valve handwheels, ladder rungs, duct seams, electrical cables)
        if (projected.meshDetails.isNotEmpty()) {
            for (detail in projected.meshDetails) {
                drawLine(
                    color = detail.color,
                    start = detail.start,
                    end = detail.end,
                    strokeWidth = if (isSelected) detail.strokeWidth + 1.5f else detail.strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        // Selection Glow Border
        if (isSelected) {
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = strokeThickness + 10f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color.copy(alpha = 0.4f),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = strokeThickness + 18f,
                cap = StrokeCap.Round
            )
        }

        // Centerline / Flow Indicator
        drawLine(
            color = Color.White.copy(alpha = if (isSelected) 0.8f else 0.4f),
            start = Offset(sx, sy),
            end = Offset(ex, ey),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        )

        // Draw End Caps / Nodes
        drawCircle(
            color = Color.White,
            radius = (strokeThickness * 0.40f).coerceIn(4f, 10f),
            center = Offset(sx, sy)
        )
        drawCircle(
            color = Color.White,
            radius = (strokeThickness * 0.40f).coerceIn(4f, 10f),
            center = Offset(ex, ey)
        )
    } catch (t: Throwable) {
        // Safe rendering fallback
    }
}

/**
 * Directional indicator displayed when the user is pointed away from the room wall.
 */
private fun DrawScope.drawDirectionalGuide(width: Float, height: Float) {
    val cx = width / 2f
    val cy = height / 2f
    // Draw subtle guide ring pointing forward to the models
    drawCircle(
        color = Color(0x33FFD54F),
        radius = 50f,
        center = Offset(cx, cy),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    )
}

/**
 * Draws CAD-style dimension extension lines, arrows, and physical measurement tags.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.renderCadDimensionLines(
    projected: ProjectedComponent,
    isSelected: Boolean,
    textMeasurer: TextMeasurer
) {
    try {
        val comp = projected.component
        val p1 = projected.startScreen
        val p2 = projected.endScreen

        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

        // Normal vector perpendicular to segment
        val nx = -dy / len
        val ny = dx / len

        val offsetDist = if (isSelected) 36f else 28f
        val tickSize = 10f

        val dimStart = Offset(p1.x + nx * offsetDist, p1.y + ny * offsetDist)
        val dimEnd = Offset(p2.x + nx * offsetDist, p2.y + ny * offsetDist)

        val dimColor = if (isSelected) Color(0xFFFFD54F) else Color(0xDDFFFFFF)

        // Extension lines from component to dimension line
        drawLine(
            color = dimColor.copy(alpha = 0.5f),
            start = p1,
            end = Offset(p1.x + nx * (offsetDist + tickSize), p1.y + ny * (offsetDist + tickSize)),
            strokeWidth = 1.5f
        )
        drawLine(
            color = dimColor.copy(alpha = 0.5f),
            start = p2,
            end = Offset(p2.x + nx * (offsetDist + tickSize), p2.y + ny * (offsetDist + tickSize)),
            strokeWidth = 1.5f
        )

        // Main dimension line
        drawLine(
            color = dimColor,
            start = dimStart,
            end = dimEnd,
            strokeWidth = 2f
        )

        // Arrow ticks at endpoints
        drawLine(
            color = dimColor,
            start = Offset(dimStart.x - ny * tickSize, dimStart.y + nx * tickSize),
            end = Offset(dimStart.x + ny * tickSize, dimStart.y - nx * tickSize),
            strokeWidth = 2f
        )
        drawLine(
            color = dimColor,
            start = Offset(dimEnd.x - ny * tickSize, dimEnd.y + nx * tickSize),
            end = Offset(dimEnd.x + ny * tickSize, dimEnd.y - nx * tickSize),
            strokeWidth = 2f
        )

        // Dimension Measurement Label
        val measurementText = "${comp.dimensionPrimary} • L: ${"%.1f".format(comp.lengthM)} m"
        val textLayout = textMeasurer.measure(
            text = AnnotatedString(measurementText),
            style = TextStyle(
                color = dimColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )

        val textCenter = Offset((dimStart.x + dimEnd.x) / 2f, (dimStart.y + dimEnd.y) / 2f)
        val bgPadding = 4f
        drawRoundRect(
            color = Color(0xCC0B1120),
            topLeft = Offset(
                textCenter.x - textLayout.size.width / 2f - bgPadding,
                textCenter.y - textLayout.size.height / 2f - bgPadding
            ),
            size = Size(
                textLayout.size.width + bgPadding * 2f,
                textLayout.size.height + bgPadding * 2f
            ),
            cornerRadius = CornerRadius(4f, 4f)
        )

        drawText(
            textMeasurer = textMeasurer,
            text = measurementText,
            topLeft = Offset(
                textCenter.x - textLayout.size.width / 2f,
                textCenter.y - textLayout.size.height / 2f
            ),
            style = TextStyle(
                color = dimColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
    } catch (t: Throwable) {
        // Fallback silently if text rendering glitches
    }
}

/**
 * Draws the high-visibility floating 3D Dimension Badge anchored to the component in AR space.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.renderDimensionBadge(
    projected: ProjectedComponent,
    isSelected: Boolean,
    textMeasurer: TextMeasurer
) {
    try {
        val comp = projected.component
        val center = projected.badgeScreen
        val distanceStr = "${"%.1f".format(projected.distanceMeters)}m"

        val titleText = "${comp.id} • ${comp.name.take(18)}"
        val dimensionText = "${comp.dimensionPrimary} | L: ${"%.1f".format(comp.lengthM)}m"
        val levelText = "${comp.elevationSummary} ($distanceStr)"

        val titleLayout = textMeasurer.measure(
            text = AnnotatedString(titleText),
            style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
        val dimLayout = textMeasurer.measure(
            text = AnnotatedString(dimensionText),
            style = TextStyle(color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        )
        val levelLayout = textMeasurer.measure(
            text = AnnotatedString(levelText),
            style = TextStyle(color = Color(0xFFB0BEC5), fontSize = 10.sp)
        )

        val contentWidth = maxOf(titleLayout.size.width, dimLayout.size.width, levelLayout.size.width) + 32f
        val contentHeight = 64f

        val badgeLeft = center.x - contentWidth / 2f
        val badgeTop = center.y - contentHeight / 2f

        // Connecting line from component midpoint to badge
        drawLine(
            color = comp.color.copy(alpha = 0.6f),
            start = center,
            end = Offset(center.x, badgeTop + contentHeight),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )

        // Badge Background Pill
        drawRoundRect(
            color = if (isSelected) Color(0xE61E293B) else Color(0xCC0F172A),
            topLeft = Offset(badgeLeft, badgeTop),
            size = Size(contentWidth, contentHeight),
            cornerRadius = CornerRadius(10f, 10f)
        )

        // Border
        drawRoundRect(
            color = if (isSelected) Color.White else comp.color.copy(alpha = 0.7f),
            topLeft = Offset(badgeLeft, badgeTop),
            size = Size(contentWidth, contentHeight),
            cornerRadius = CornerRadius(10f, 10f),
            style = Stroke(width = if (isSelected) 2f else 1.2f)
        )

        // Category Indicator Dot
        drawCircle(
            color = comp.color,
            radius = 5f,
            center = Offset(badgeLeft + 12f, badgeTop + 14f)
        )

        // Draw 3 Text Rows inside Badge
        drawText(
            textMeasurer = textMeasurer,
            text = titleText,
            topLeft = Offset(badgeLeft + 22f, badgeTop + 6f),
            style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )

        drawText(
            textMeasurer = textMeasurer,
            text = dimensionText,
            topLeft = Offset(badgeLeft + 22f, badgeTop + 24f),
            style = TextStyle(color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        )

        drawText(
            textMeasurer = textMeasurer,
            text = levelText,
            topLeft = Offset(badgeLeft + 22f, badgeTop + 42f),
            style = TextStyle(color = Color(0xFFB0BEC5), fontSize = 10.sp)
        )
    } catch (t: Throwable) {
        // Fallback silently if badge measurement fails
    }
}

/**
 * Draws a subtle AR center reticle with compass and tilt diagnostics.
 */
private fun DrawScope.renderCenterReticle(
    width: Float,
    height: Float,
    deviceOrientation: DeviceOrientation
) {
    try {
        val cx = width / 2f
        val cy = height / 2f
        val reticleRadius = 24f

        // Center Crosshair Circle
        drawCircle(
            color = Color(0x44FFFFFF),
            radius = reticleRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )
        drawCircle(
            color = Color(0x88FFFFFF),
            radius = 3f,
            center = Offset(cx, cy)
        )

        // Dynamic Rotating North Indicator Tick on Reticle
        val azimuth = if (deviceOrientation.azimuthDeg.isFinite()) deviceOrientation.azimuthDeg else 0f
        val northAngleRad = Math.toRadians((-azimuth).toDouble()).toFloat()
        val nx = cx + (reticleRadius + 4f) * sin(northAngleRad)
        val ny = cy - (reticleRadius + 4f) * cos(northAngleRad)
        drawCircle(
            color = Color(0xFFFF5252),
            radius = 3f,
            center = Offset(nx, ny)
        )

        // Crosshair ticks
        val tickLen = 8f
        drawLine(Color(0x66FFFFFF), Offset(cx - reticleRadius - tickLen, cy), Offset(cx - reticleRadius, cy), 1.5f)
        drawLine(Color(0x66FFFFFF), Offset(cx + reticleRadius, cy), Offset(cx + reticleRadius + tickLen, cy), 1.5f)
        drawLine(Color(0x66FFFFFF), Offset(cx, cy - reticleRadius - tickLen), Offset(cx, cy - reticleRadius), 1.5f)
        drawLine(Color(0x66FFFFFF), Offset(cx, cy + reticleRadius), Offset(cx, cy + reticleRadius + tickLen), 1.5f)
    } catch (t: Throwable) {
        // Suppress reticle error
    }
}

/**
 * Computes shortest distance from point p to line segment (v, w).
 */
private fun distanceToSegment(p: Offset, v: Offset, w: Offset): Float {
    val l2 = (v.x - w.x) * (v.x - w.x) + (v.y - w.y) * (v.y - w.y)
    if (l2 == 0f) return (p - v).getDistance()
    val t = (((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2).coerceIn(0f, 1f)
    val projection = Offset(v.x + t * (w.x - v.x), v.y + t * (w.y - v.y))
    return (p - projection).getDistance()
}
