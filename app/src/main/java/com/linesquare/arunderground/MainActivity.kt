package com.linesquare.arunderground

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.linesquare.arunderground.ar.PipeSegment3D
import com.linesquare.arunderground.model.UtilityPipe
import com.linesquare.arunderground.ui.AROverlayHUD
import com.linesquare.arunderground.ui.theme.ARUndergroundTheme
import com.linesquare.arunderground.viewmodel.ARViewModel
import com.linesquare.arunderground.viewmodel.RenderMode
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView

import com.google.android.filament.utils.Utils

class MainActivity : ComponentActivity() {

    private val viewModel: ARViewModel by viewModels()
    private var userRequestedInstall = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Utils.init()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        setContent {
            ARUndergroundTheme {
                val uiState by viewModel.uiState.collectAsState()
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                var showArCoreInstallDialog by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
                    if (!hasCameraPermission) {
                        Toast.makeText(
                            this,
                            "Camera permission is required to view live camera feed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                    checkArCoreAvailability()
                }

                // If user toggles to ARCore but ARCore needs installation, handle prompt
                LaunchedEffect(uiState.renderMode) {
                    if (uiState.renderMode == RenderMode.ARCORE_NATIVE) {
                        try {
                            val availability = ArCoreApk.getInstance().checkAvailability(this@MainActivity)
                            if (availability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED) {
                                showArCoreInstallDialog = true
                            }
                        } catch (e: Throwable) {
                            viewModel.setRenderMode(RenderMode.LIVE_CAMERA)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (uiState.renderMode) {
                        RenderMode.ARCORE_NATIVE -> {
                            if (hasCameraPermission && uiState.isArCoreAvailable) {
                                NativeARScene(
                                    segments = uiState.segments,
                                    onPipeSelected = { pipe -> viewModel.selectPipe(pipe) },
                                    onTrackingUpdate = { status -> viewModel.updateTrackingStatus(status) },
                                    onFallbackNeeded = { reason ->
                                        Toast.makeText(this@MainActivity, reason, Toast.LENGTH_SHORT).show()
                                        viewModel.setRenderMode(RenderMode.LIVE_CAMERA)
                                    }
                                )
                            } else if (hasCameraPermission) {
                                LiveCameraOverlayScene(
                                    segments = uiState.segments,
                                    selectedPipe = uiState.selectedPipe,
                                    onPipeTapped = { pipe -> viewModel.selectPipe(pipe) },
                                    onError = { error ->
                                        viewModel.updateTrackingStatus(error)
                                    }
                                )
                            } else {
                                CameraPermissionFallbackView {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.CAMERA,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
                            }
                        }
                        RenderMode.LIVE_CAMERA -> {
                            if (hasCameraPermission) {
                                LiveCameraOverlayScene(
                                    segments = uiState.segments,
                                    selectedPipe = uiState.selectedPipe,
                                    onPipeTapped = { pipe -> viewModel.selectPipe(pipe) },
                                    onError = { error ->
                                        viewModel.updateTrackingStatus(error)
                                    }
                                )
                            } else {
                                CameraPermissionFallbackView {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.CAMERA,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
                            }
                        }
                        RenderMode.SIMULATION -> {
                            SimulationCanvasView(
                                segments = uiState.segments,
                                selectedPipe = uiState.selectedPipe,
                                onPipeTapped = { pipe -> viewModel.selectPipe(pipe) },
                                isCameraBackground = false
                            )
                        }
                    }

                    // Dialog to install ARCore if supported but not yet installed
                    if (showArCoreInstallDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showArCoreInstallDialog = false
                                viewModel.setRenderMode(RenderMode.LIVE_CAMERA)
                            },
                            title = { Text("Google Play Services for AR") },
                            text = {
                                Text("ARCore is supported on your device for 6-DoF AR tracking. Would you like to install it from Google Play, or continue using the Live Camera feed?")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showArCoreInstallDialog = false
                                    try {
                                        when (ArCoreApk.getInstance().requestInstall(this@MainActivity, userRequestedInstall)) {
                                            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                                                userRequestedInstall = false
                                            }
                                            ArCoreApk.InstallStatus.INSTALLED -> {
                                                viewModel.setArCoreAvailable(true)
                                                viewModel.setRenderMode(RenderMode.ARCORE_NATIVE)
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        Toast.makeText(this@MainActivity, "Could not open Play Store: ${e.message}", Toast.LENGTH_SHORT).show()
                                        viewModel.setRenderMode(RenderMode.LIVE_CAMERA)
                                    }
                                }) {
                                    Text("Install from Play Store")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showArCoreInstallDialog = false
                                    viewModel.setRenderMode(RenderMode.LIVE_CAMERA)
                                }) {
                                    Text("Use Live Camera")
                                }
                            }
                        )
                    }

                    // JETPACK COMPOSE MATERIAL 3 HUD OVERLAY
                    AROverlayHUD(
                        state = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkArCoreAvailability()
    }

    private fun checkArCoreAvailability() {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(this)
            if (availability.isTransient) {
                window.decorView.postDelayed({ checkArCoreAvailability() }, 200)
            } else if (availability == ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                viewModel.setArCoreAvailable(true)
                viewModel.updateTrackingStatus("ARCore 6-DoF Ready")
            } else {
                viewModel.setArCoreAvailable(false)
                viewModel.updateTrackingStatus("Live Camera Mode Ready")
            }
        } catch (e: Throwable) {
            viewModel.setArCoreAvailable(false)
            viewModel.updateTrackingStatus("Live Camera Mode Ready")
        }
    }
}

/**
 * Displayed when camera permission is requested but not yet granted.
 */
@Composable
fun CameraPermissionFallbackView(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "To view live underground utility pipes overlaid on your real-world surroundings, camera access is required.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Grant Camera Permission")
            }
        }
    }
}

/**
 * Robust Live Camera Feed using CameraX with pipeline projection overlay.
 * Works on 100% of Android devices with a camera, regardless of ARCore availability.
 */
@Composable
fun LiveCameraOverlayScene(
    segments: List<PipeSegment3D>,
    selectedPipe: UtilityPipe?,
    onPipeTapped: (UtilityPipe) -> Unit,
    onError: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = modifier.fillMaxSize()) {
        // Rear Camera Preview Feed
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                    } catch (e: Throwable) {
                        onError("Camera hardware error: ${e.localizedMessage}")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subsurface Pipeline Overlay Projection over Camera Stream
        SimulationCanvasView(
            segments = segments,
            selectedPipe = selectedPipe,
            onPipeTapped = onPipeTapped,
            isCameraBackground = true
        )
    }
}

/**
 * Native Google Filament AR SceneView integration.
 * Safely initializes ARCore with comprehensive error recovery.
 */
@Composable
fun NativeARScene(
    segments: List<PipeSegment3D>,
    onPipeSelected: (UtilityPipe) -> Unit,
    onTrackingUpdate: (String) -> Unit,
    onFallbackNeeded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ARScene(
        modifier = modifier.fillMaxSize(),
        sessionConfiguration = { _, config ->
            try {
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.depthMode = Config.DepthMode.AUTOMATIC
                config.focusMode = Config.FocusMode.AUTO
            } catch (e: Throwable) {
                onTrackingUpdate("ARCore config warning: ${e.localizedMessage}")
            }
        },
        onSessionCreated = {
            onTrackingUpdate("ARCore Session Initialized (6-DoF + Filament)")
        },
        onSessionFailed = { exception ->
            onTrackingUpdate("ARCore Session Failed: ${exception.localizedMessage}")
            onFallbackNeeded("ARCore unavailable: ${exception.localizedMessage}. Using Live Camera.")
        }
    )
}

/**
 * High-performance 3D projection simulation canvas.
 * Allows instant testing of the entire subsurface pipe network, depth projection,
 * and manual GPS calibration without requiring on-site field visits.
 */
@Composable
fun SimulationCanvasView(
    segments: List<PipeSegment3D>,
    selectedPipe: UtilityPipe?,
    onPipeTapped: (UtilityPipe) -> Unit,
    isCameraBackground: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(segments) {
                detectTapGestures { tapOffset ->
                    val width = size.width
                    val height = size.height
                    val cx = width / 2f
                    val cy = height * 0.65f
                    val scale = width / 30f

                    for (segment in segments) {
                        val sx = cx + segment.startPosition[0] * scale
                        val sy = cy + segment.startPosition[2] * scale + segment.startPosition[1] * (scale * 0.5f)
                        val ex = cx + segment.endPosition[0] * scale
                        val ey = cy + segment.endPosition[2] * scale + segment.endPosition[1] * (scale * 0.5f)

                        val d = distanceToSegment(tapOffset, Offset(sx, sy), Offset(ex, ey))
                        if (d < 30f) {
                            onPipeTapped(segment.pipe)
                            return@detectTapGestures
                        }
                    }
                }
            }
    ) {
        // Draw dark background only in pure simulation mode (not on live camera)
        if (!isCameraBackground) {
            drawRect(color = Color(0xCC0B1120))
        }

        val cx = size.width / 2f
        val cy = size.height * 0.65f
        val scale = size.width / 30f

        // Draw subsurface ground horizon grid line
        drawLine(
            color = if (isCameraBackground) Color(0x88FFFFFF) else Color(0x33FFFFFF),
            start = Offset(0f, cy),
            end = Offset(size.width, cy),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        )

        // Draw each 3D pipe segment with diameter and depth perspective
        for (segment in segments) {
            val sx = cx + segment.startPosition[0] * scale
            val sy = cy + segment.startPosition[2] * scale + segment.startPosition[1] * (scale * 0.5f)

            val ex = cx + segment.endPosition[0] * scale
            val ey = cy + segment.endPosition[2] * scale + segment.endPosition[1] * (scale * 0.5f)

            val isSelected = selectedPipe?.id == segment.pipe.id
            val strokeThickness = (segment.pipe.diameterMm / 20f).coerceIn(6f, 28f)

            // Outer Glow / Selection border
            if (isSelected) {
                drawLine(
                    color = Color.White,
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = strokeThickness + 8f,
                    cap = StrokeCap.Round
                )
            }

            // Pipe Body in Utility Industry Color
            drawLine(
                color = segment.pipe.utilityType.composeColor,
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = strokeThickness,
                cap = StrokeCap.Round
            )

            // Pipe Centerline indicator
            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun distanceToSegment(p: Offset, v: Offset, w: Offset): Float {
    val l2 = (v.x - w.x) * (v.x - w.x) + (v.y - w.y) * (v.y - w.y)
    if (l2 == 0f) return (p - v).getDistance()
    val t = (((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2).coerceIn(0f, 1f)
    val projection = Offset(v.x + t * (w.x - v.x), v.y + t * (w.y - v.y))
    return (p - projection).getDistance()
}
