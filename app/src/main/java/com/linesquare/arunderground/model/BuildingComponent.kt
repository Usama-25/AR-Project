package com.linesquare.arunderground.model

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Data model for physical building, MEP (Mechanical, Electrical, Plumbing),
 * and infrastructure components rendered in Augmented Reality.
 *
 * @param id Unique asset or BIM identifier (e.g. "COL-B1-01", "DUCT-S-101", "W-101")
 * @param name Descriptive engineering name (e.g. "Supply Air Duct", "Reinforced Concrete Column")
 * @param category Classification system (Structural, HVAC, Plumbing, Electrical, Drainage, Gas)
 * @param geometry Geometric profile type
 * @param startPoint 3D start point [x, y, z] in metric room/building space (X=East/Right, Y=Up, Z=North/Depth)
 * @param endPoint 3D end point [x, y, z] in metric room/building space
 * @param diameterMm Physical diameter in millimeters (for circular elements)
 * @param widthMm Physical cross-section width in millimeters (for rectangular ducts, beams, trays)
 * @param heightMm Physical cross-section height/depth in millimeters (for rectangular ducts, columns, beams)
 * @param lengthM True physical span length in meters
 * @param elevationOrDepthM Elevation from floor level or ground surface in meters (negative = subsurface, positive = above floor/ceiling)
 * @param wallThicknessMm Shell / material thickness in millimeters
 * @param slopePercent Drainage gradient percentage (e.g. 1.5% for gravity drains)
 * @param material Construction material specification (e.g. "Galvanized Sheet Steel", "C35/45 Concrete", "HDPE PN16")
 * @param systemLevel Building zone or vertical elevation tag (e.g. "Ceiling Plenum +3.2m", "Subsurface -1.2m", "Structural Frame Grid")
 * @param ifcType IFC Entity classification standard (e.g. "IfcPipeSegment", "IfcDuctSegment", "IfcColumn", "IfcBeam")
 * @param installStatus Asset installation / maintenance status
 */
data class BuildingComponent(
    val id: String,
    val name: String,
    val category: BuildingCategory,
    val geometry: ComponentGeometry,
    val startPoint: FloatArray,
    val endPoint: FloatArray,
    val diameterMm: Int? = null,
    val widthMm: Int? = null,
    val heightMm: Int? = null,
    val lengthM: Float,
    val elevationOrDepthM: Float,
    val wallThicknessMm: Float? = null,
    val slopePercent: Float? = null,
    val material: String,
    val systemLevel: String,
    val ifcType: String,
    val installStatus: String = "Installed / Verified"
) {
    val color: Color
        get() = category.composeColor

    /**
     * Primary dimension tag (e.g. "Ø 200 mm" or "600 × 400 mm" or "450 × 450 mm")
     */
    val dimensionPrimary: String
        get() = when {
            diameterMm != null -> "Ø ${diameterMm} mm"
            widthMm != null && heightMm != null -> "${widthMm} × ${heightMm} mm"
            widthMm != null -> "W: ${widthMm} mm"
            heightMm != null -> "H: ${heightMm} mm"
            else -> "L: ${"%.1f".format(lengthM)} m"
        }

    /**
     * Complete dimensional summary for AR HUD and badges (e.g. "600×400 mm | L: 8.4 m | Ceiling: +3.1 m")
     */
    val dimensionSummary: String
        get() {
            val dim = dimensionPrimary
            val len = "L: ${"%.1f".format(lengthM)} m"
            val elev = elevationSummary
            return "$dim • $len • $elev"
        }

    /**
     * Formatted elevation or depth relative to building datum
     */
    val elevationSummary: String
        get() = if (elevationOrDepthM < 0f) {
            "Depth: ${"%.2f".format(-elevationOrDepthM)} m"
        } else if (elevationOrDepthM > 2.0f) {
            "Ceiling: +${"%.2f".format(elevationOrDepthM)} m"
        } else {
            "Level: +${"%.2f".format(elevationOrDepthM)} m"
        }

    /**
     * Physical cross-sectional area in square meters
     */
    val crossSectionalAreaM2: Float
        get() = when {
            diameterMm != null -> {
                val r = (diameterMm / 1000f) / 2f
                (PI * r * r).toFloat()
            }
            widthMm != null && heightMm != null -> {
                (widthMm / 1000f) * (heightMm / 1000f)
            }
            else -> 0f
        }

    /**
     * Total internal volumetric capacity in cubic meters (m³)
     */
    val volumeM3: Float
        get() = crossSectionalAreaM2 * lengthM

    /**
     * True 3D center position [x, y, z] in local building coordinates
     */
    val centerPosition: FloatArray
        get() = floatArrayOf(
            (startPoint[0] + endPoint[0]) / 2f,
            (startPoint[1] + endPoint[1]) / 2f,
            (startPoint[2] + endPoint[2]) / 2f
        )

    /**
     * Direction vector in local building space [dx, dy, dz] normalized
     */
    val directionVector: FloatArray
        get() {
            val dx = endPoint[0] - startPoint[0]
            val dy = endPoint[1] - startPoint[1]
            val dz = endPoint[2] - startPoint[2]
            val mag = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.0001f)
            return floatArrayOf(dx / mag, dy / mag, dz / mag)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BuildingComponent) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
