package com.linesquare.arunderground.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linesquare.arunderground.model.BuildingCategory
import com.linesquare.arunderground.model.BuildingComponent
import com.linesquare.arunderground.model.BuildingPreset
import com.linesquare.arunderground.ui.theme.*
import com.linesquare.arunderground.viewmodel.ARUiState
import com.linesquare.arunderground.viewmodel.ARViewModel
import com.linesquare.arunderground.viewmodel.RenderMode
import kotlin.math.roundToInt

@Composable
fun AROverlayHUD(
    state: ARUiState,
    viewModel: ARViewModel,
    modifier: Modifier = Modifier
) {
    var showPresetMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP SECTION: Header & Controls Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and Real-time Sensor Status
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Building AR X-Ray",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        // Preset Chip button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x33FFFFFF))
                                .clickable { showPresetMenu = !showPresetMenu }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = when (state.currentPreset) {
                                        BuildingPreset.ASLAM_MARKET -> "Aslam Market"
                                        BuildingPreset.COMMERCIAL_BUILDING -> "Office MEP"
                                        BuildingPreset.UNDERGROUND_UTILITIES -> "Utilities"
                                        BuildingPreset.INDUSTRIAL_PLANT -> "Industrial"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFFD54F)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Preset",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Live Sensor Tracking Diagnostics
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val dotColor = if (state.renderMode == RenderMode.LIVE_CAMERA) Color(0xFF00E676) else UtilityGas
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )

                        val orient = state.deviceOrientation
                        val azInt = if (orient.azimuthDeg.isFinite()) orient.azimuthDeg.toInt() else 0
                        val pitchInt = if (orient.pitchDeg.isFinite()) orient.pitchDeg.toInt() else 0
                        val headingStr = "$azInt° ${orient.compassDirection}"
                        val pitchStr = "Tilt: $pitchInt°"

                        Text(
                            text = if (state.renderMode == RenderMode.LIVE_CAMERA) {
                                "Dynamic 60 FPS • $headingStr • $pitchStr"
                            } else {
                                "Simulation Mode • $headingStr"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Header Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Toggle Dimension Badges
                    IconButton(
                        onClick = { viewModel.toggleDimensionBadges() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.showDimensionBadges) Color(0x66FFD54F) else Color(0x22FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = "Toggle Dimension Badges",
                            tint = if (state.showDimensionBadges) Color(0xFFFFD54F) else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Toggle CAD Dimension Lines
                    IconButton(
                        onClick = { viewModel.toggleDimensionLines() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.showDimensionLines) Color(0x6600E5FF) else Color(0x22FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatShapes,
                            contentDescription = "Toggle Dimension Lines",
                            tint = if (state.showDimensionLines) Color(0xFF00E5FF) else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Calibration Nudge Button
                    IconButton(
                        onClick = { viewModel.toggleCalibrationPanel() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.isCalibrationOpen) AccentOrange else Color(0x22FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Calibrate / Align",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Camera / Simulation Switch
                    IconButton(
                        onClick = { viewModel.toggleSimulationMode() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x22FFFFFF))
                    ) {
                        Icon(
                            imageVector = if (state.renderMode == RenderMode.SIMULATION) Icons.Default.Videocam else Icons.Default.Science,
                            contentDescription = "Toggle Mode",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Preset Dropdown Menu
            DropdownMenu(
                expanded = showPresetMenu,
                onDismissRequest = { showPresetMenu = false }
            ) {
                BuildingPreset.values().forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(preset.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(preset.subtitle, fontSize = 11.sp, color = Color.Gray)
                            }
                        },
                        onClick = {
                            viewModel.setPreset(preset)
                            showPresetMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (preset) {
                                    BuildingPreset.ASLAM_MARKET -> Icons.Default.Place
                                    BuildingPreset.COMMERCIAL_BUILDING -> Icons.Default.Domain
                                    BuildingPreset.UNDERGROUND_UTILITIES -> Icons.Default.WaterDrop
                                    BuildingPreset.INDUSTRIAL_PLANT -> Icons.Default.Air
                                },
                                contentDescription = null,
                                tint = if (state.currentPreset == preset) Color(0xFFFFD54F) else Color.White
                            )
                        }
                    )
                }
            }

            // Category Filter Chips (Scrollable row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BuildingCategory.values().forEach { category ->
                    val isEnabled = state.enabledCategories.contains(category)
                    val count = state.allComponents.count { it.category == category }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isEnabled) GlassSurface else Color(0x40000000))
                            .border(
                                width = if (isEnabled) 1.5.dp else 1.dp,
                                color = if (isEnabled) category.composeColor else GlassBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.toggleCategory(category) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(category.composeColor)
                            )
                            Text(
                                text = "${category.shortName} ($count)",
                                fontSize = 11.sp,
                                fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Normal,
                                color = if (isEnabled) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // MANUAL CALIBRATION / ALIGNMENT D-PAD (Bottom-Left)
        AnimatedVisibility(
            visible = state.isCalibrationOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = if (state.selectedComponent != null) 230.dp else 16.dp)
        ) {
            CalibrationControlPad(
                state = state,
                viewModel = viewModel
            )
        }

        // BUILDING COMPONENT DIMENSION INSPECTOR CARD (Bottom)
        AnimatedVisibility(
            visible = state.selectedComponent != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            state.selectedComponent?.let { comp ->
                BuildingComponentInspectorCard(
                    component = comp,
                    onClose = { viewModel.selectComponent(null) }
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
            text = "AR Wall / Floor Alignment",
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
                        contentDescription = "Reset Alignment",
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
                Text("-1° Yaw", fontSize = 11.sp, color = TextPrimary)
            }

            Button(
                onClick = { viewModel.rotateYaw(1f) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("+1° Yaw", fontSize = 11.sp, color = TextPrimary)
            }
        }

        // Pitch Tilt Controls
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.rotatePitch(-2f) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Tilt Down -2°", fontSize = 10.sp, color = TextPrimary)
            }

            Button(
                onClick = { viewModel.rotatePitch(2f) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Tilt Up +2°", fontSize = 10.sp, color = TextPrimary)
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

/**
 * Detailed Engineering & Dimension Inspector Card for the selected building component.
 */
@Composable
fun BuildingComponentInspectorCard(
    component: BuildingComponent,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Asset ID, Category Dot, and Close
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
                            .background(component.color)
                    )
                    Column {
                        Text(
                            text = component.id,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = component.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
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

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Dimensions Highlight Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x26FFD54F))
                    .border(1.dp, Color(0x66FFD54F), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("NOMINAL DIMENSIONS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                        Text(component.dimensionPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TOTAL SPAN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                        Text("${"%.2f".format(component.lengthM)} meters", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Engineering Metrics 4-Column Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AttributeColumn("Elevation / Depth", component.elevationSummary)
                AttributeColumn("Wall Thickness", component.wallThicknessMm?.let { "${it} mm" } ?: "N/A")
                AttributeColumn("Cross Area", if (component.crossSectionalAreaM2 > 0f) "${"%.3f".format(component.crossSectionalAreaM2)} m²" else "N/A")
                AttributeColumn("Volume", if (component.volumeM3 > 0f) "${"%.2f".format(component.volumeM3)} m³" else "N/A")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // BIM & Classification Metadata Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AttributeColumn("Material", component.material)
                AttributeColumn("Level / Zone", component.systemLevel)
                AttributeColumn("IFC Entity", component.ifcType)
                AttributeColumn("Status", component.installStatus)
            }
        }
    }
}

@Composable
fun AttributeColumn(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
