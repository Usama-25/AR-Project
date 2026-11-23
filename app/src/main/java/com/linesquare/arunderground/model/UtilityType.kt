package com.linesquare.arunderground.model

import androidx.compose.ui.graphics.Color

/**
 * Standard utility classification color codes as per municipal and GIS engineering standards.
 */
enum class UtilityType(
    val displayName: String,
    val hexColor: String,
    val composeColor: Color,
    val colorRgb: Triple<Float, Float, Float>
) {
    WATER(
        displayName = "Water",
        hexColor = "#3B8CFF",
        composeColor = Color(0xFF3B8CFF),
        colorRgb = Triple(0.231f, 0.549f, 1.0f)
    ),
    SEWER(
        displayName = "Sewer / Drainage",
        hexColor = "#2FC46A",
        composeColor = Color(0xFF2FC46A),
        colorRgb = Triple(0.184f, 0.768f, 0.415f)
    ),
    GAS(
        displayName = "Gas Lines",
        hexColor = "#F5C518",
        composeColor = Color(0xFFF5C518),
        colorRgb = Triple(0.961f, 0.772f, 0.094f)
    ),
    SANITARY(
        displayName = "Sanitary",
        hexColor = "#B47CFF",
        composeColor = Color(0xFFB47CFF),
        colorRgb = Triple(0.706f, 0.486f, 1.0f)
    )
}
