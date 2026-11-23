package com.linesquare.arunderground.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Standard Building and Infrastructure classifications according to BIM / IFC / AEC standards.
 */
enum class BuildingCategory(
    val displayName: String,
    val shortName: String,
    val hexColor: String,
    val composeColor: Color,
    val icon: ImageVector
) {
    STRUCTURAL(
        displayName = "Structural (Columns/Beams)",
        shortName = "Structure",
        hexColor = "#AB47BC",
        composeColor = Color(0xFFAB47BC),
        icon = Icons.Default.Domain
    ),
    HVAC(
        displayName = "HVAC & Ducts",
        shortName = "HVAC",
        hexColor = "#00E5FF",
        composeColor = Color(0xFF00E5FF),
        icon = Icons.Default.Air
    ),
    PLUMBING(
        displayName = "Plumbing & Water",
        shortName = "Plumbing",
        hexColor = "#2196F3",
        composeColor = Color(0xFF2196F3),
        icon = Icons.Default.WaterDrop
    ),
    ELECTRICAL(
        displayName = "Electrical & Cable Trays",
        shortName = "Electrical",
        hexColor = "#FFB300",
        composeColor = Color(0xFFFFB300),
        icon = Icons.Default.Bolt
    ),
    DRAINAGE(
        displayName = "Drainage & Sewer",
        shortName = "Drainage",
        hexColor = "#4CAF50",
        composeColor = Color(0xFF4CAF50),
        icon = Icons.Default.DeleteSweep
    ),
    GAS(
        displayName = "Gas & Fuel",
        shortName = "Gas",
        hexColor = "#FF7043",
        composeColor = Color(0xFFFF7043),
        icon = Icons.Default.LocalFireDepartment
    )
}

/**
 * Geometric profile classification for 3D extrusion in AR.
 */
enum class ComponentGeometry {
    CYLINDRICAL_PIPE,      // Round pipes (water, gas, sewer, sprinkler)
    RECTANGULAR_DUCT,      // Rectangular HVAC air ducts
    CABLE_TRAY,            // Perforated channel / ladder tray
    STRUCTURAL_COLUMN,     // Vertical pillar / column (concrete or steel)
    STRUCTURAL_BEAM,       // Horizontal load-bearing I-beam
    SLAB_FOUNDATION        // Planar floor / footing slab
}
