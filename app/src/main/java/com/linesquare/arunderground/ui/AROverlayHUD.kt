package com.linesquare.arunderground.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linesquare.arunderground.model.UtilityPipe
import com.linesquare.arunderground.model.UtilityType
import com.linesquare.arunderground.ui.theme.*
import com.linesquare.arunderground.viewmodel.ARUiState
import com.linesquare.arunderground.viewmodel.ARViewModel
import com.linesquare.arunderground.viewmodel.RenderMode

@Composable
fun AROverlayHUD(
    state: ARUiState,
    viewModel: ARViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP SECTION: Header & Layer Filter Chips
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Utility X-Ray AR",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (dotColor, modeTitle) = when (state.renderMode) {
                            RenderMode.SIMULATION -> UtilityGas to "Simulation Mode"
                            RenderMode.LIVE_CAMERA -> Color(0xFF00E676) to "Live Camera X-Ray"
                            RenderMode.ARCORE_NATIVE -> Color(0xFF2979FF) to "Live AR (ARCore 6-DoF)"
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = modeTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Calibration button
                    IconButton(
                        onClick = { viewModel.toggleCalibrationPanel() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.isCalibrationOpen) AccentOrange else Color(0x33FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Calibrate GPS",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Simulation / Camera toggle
                    IconButton(
                        onClick = { viewModel.toggleSimulationMode() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        Icon(
                            imageVector = if (state.renderMode == RenderMode.SIMULATION) Icons.Default.Videocam else Icons.Default.Science,
                            contentDescription = "Toggle Mode",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Layer Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UtilityType.values().forEach { type ->
                    val isEnabled = state.enabledTypes.contains(type)
                    val count = state.allPipes.count { it.utilityType == type }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isEnabled) GlassSurface else Color(0x40000000))
                            .border(
                                width = if (isEnabled) 1.5.dp else 1.dp,
                                color = if (isEnabled) type.composeColor else GlassBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.toggleLayer(type) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(type.composeColor)
                            )
                            Text(
                                text = "${type.displayName.take(5)} ($count)",
                                fontSize = 11.sp,
                                fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Normal,
                                color = if (isEnabled) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // MANUAL CALIBRATION CONTROLS (D-Pad on bottom-left)
        AnimatedVisibility(
            visible = state.isCalibrationOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = if (state.selectedPipe != null) 170.dp else 16.dp)
        ) {
            CalibrationControlPad(
                state = state,
                viewModel = viewModel
            )
        }

        // SELECTED PIPE INSPECTION CARD (Bottom)
        AnimatedVisibility(
            visible = state.selectedPipe != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            state.selectedPipe?.let { pipe ->
                PipeInspectorCard(
                    pipe = pipe,
                    onClose = { viewModel.selectPipe(null) }
                )
            }
        }
    }
}

@Composable
fun CalibrationControlPad(
    state: ARUiState,
    viewModel: ARViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "GPS Drift Calibration",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AccentOrange
        )

        val cur = state.alignmentOffset
        Text(
            text = "ΔX: ${"%.1f".format(cur.offsetX)}m | ΔZ: ${"%.1f".format(cur.offsetZ)}m | Yaw: ${cur.rotationYawDeg.toInt()}°",
            fontSize = 10.sp,
            color = TextSecondary
        )

        // D-Pad Grid
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Forward (North -Z)
            PadButton(Icons.Default.KeyboardArrowUp) { viewModel.nudge(0f, 0f, -0.2f) }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Left (-X)
                PadButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { viewModel.nudge(-0.2f, 0f, 0f) }

                // Reset to Center
                IconButton(
                    onClick = { viewModel.resetAlignment() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Right (+X)
                PadButton(Icons.AutoMirrored.Filled.KeyboardArrowRight) { viewModel.nudge(0.2f, 0f, 0f) }
            }

            // Backward (South +Z)
            PadButton(Icons.Default.KeyboardArrowDown) { viewModel.nudge(0f, 0f, 0.2f) }
        }

        // Yaw Rotation Controls
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.rotateYaw(-1f) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("-1°", fontSize = 11.sp, color = TextPrimary)
            }

            Button(
                onClick = { viewModel.rotateYaw(1f) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("+1°", fontSize = 11.sp, color = TextPrimary)
            }
        }
    }
}

@Composable
fun PadButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x33FFFFFF))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun PipeInspectorCard(
    pipe: UtilityPipe,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(pipe.utilityType.composeColor)
                    )
                    Text(
                        text = "Asset ID: ${pipe.id}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Parameter Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AttributeColumn("Utility Type", pipe.utilityType.displayName)
                AttributeColumn("Diameter", "${pipe.diameterMm} mm")
                AttributeColumn("Burial Depth", "${pipe.depthM} m")
                AttributeColumn("Material", pipe.material)
            }
        }
    }
}

@Composable
fun AttributeColumn(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
