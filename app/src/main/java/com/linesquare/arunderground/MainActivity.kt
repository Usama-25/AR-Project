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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.linesquare.arunderground.ar.DeviceOrientationTracker
import com.linesquare.arunderground.ui.AROverlayHUD
import com.linesquare.arunderground.ui.DynamicCameraARScene
import com.linesquare.arunderground.ui.theme.ARUndergroundTheme
import com.linesquare.arunderground.viewmodel.ARViewModel
import com.linesquare.arunderground.viewmodel.RenderMode

class MainActivity : ComponentActivity() {

    private val viewModel: ARViewModel by viewModels()
    private lateinit var orientationTracker: DeviceOrientationTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            orientationTracker = DeviceOrientationTracker(this)
            orientationTracker.start()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        setContent {
            ARUndergroundTheme {
                val uiState by viewModel.uiState.collectAsState()
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    try {
                        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
                        if (!hasCameraPermission) {
                            Toast.makeText(
                                this@MainActivity,
                                "Camera permission is required to overlay building components in live AR",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

                // Prompt permissions immediately on first launch safely
                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        try {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }

                    // Feed hardware orientation sensors safely to ViewModel for dynamic AR tracking
                    while (true) {
                        try {
                            if (::orientationTracker.isInitialized) {
                                val currentOrient = orientationTracker.orientationState.value
                                viewModel.updateOrientation(currentOrient)
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                        kotlinx.coroutines.delay(50)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (uiState.renderMode) {
                        RenderMode.LIVE_CAMERA, RenderMode.ARCORE_NATIVE -> {
                            if (hasCameraPermission) {
                                DynamicCameraARScene(
                                    components = uiState.visibleComponents,
                                    selectedComponent = uiState.selectedComponent,
                                    deviceOrientation = uiState.deviceOrientation,
                                    alignmentOffset = uiState.alignmentOffset,
                                    showDimensionBadges = uiState.showDimensionBadges,
                                    showDimensionLines = uiState.showDimensionLines,
                                    onComponentSelected = { comp -> viewModel.selectComponent(comp) },
                                    isCameraFeedActive = true,
                                    onError = { error ->
                                        viewModel.updateTrackingStatus(error)
                                    }
                                )
                            } else {
                                CameraPermissionFallbackView {
                                    try {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.CAMERA,
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    } catch (e: Throwable) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                        RenderMode.SIMULATION -> {
                            DynamicCameraARScene(
                                components = uiState.visibleComponents,
                                selectedComponent = uiState.selectedComponent,
                                deviceOrientation = uiState.deviceOrientation,
                                alignmentOffset = uiState.alignmentOffset,
                                showDimensionBadges = uiState.showDimensionBadges,
                                showDimensionLines = uiState.showDimensionLines,
                                onComponentSelected = { comp -> viewModel.selectComponent(comp) },
                                isCameraFeedActive = false
                            )
                        }
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
        try {
            if (::orientationTracker.isInitialized) {
                orientationTracker.start()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        try {
            if (::orientationTracker.isInitialized) {
                orientationTracker.stop()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        super.onPause()
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
                text = "To view live building components and subsurface utilities overlaid on your real-world surroundings, camera access is required.",
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
