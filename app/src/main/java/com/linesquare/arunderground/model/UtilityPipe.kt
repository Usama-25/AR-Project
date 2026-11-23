package com.linesquare.arunderground.model

/**
 * Data model for a subsurface utility pipeline asset.
 *
 * @param id Unique asset identifier (e.g. "W-101")
 * @param utilityType Utility classification (Water, Sewer, Gas, Sanitary)
 * @param diameterMm Physical pipe diameter in millimeters
 * @param depthM Vertical burial depth below ground surface in meters (positive value)
 * @param material Construction material (e.g., "PVC", "HDPE", "Concrete", "Ductile Iron")
 * @param installYear Year installed
 * @param coordinates Ordered GIS waypoints defining the pipeline trajectory
 */
data class UtilityPipe(
    val id: String,
    val utilityType: UtilityType,
    val diameterMm: Int,
    val depthM: Float,
    val material: String,
    val installYear: Int,
    val coordinates: List<GeoPoint>
) {
    val diameterMeters: Float
        get() = diameterMm / 1000f

    val radiusMeters: Float
        get() = diameterMeters / 2f
}
