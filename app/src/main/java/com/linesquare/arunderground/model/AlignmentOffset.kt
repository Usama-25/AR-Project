package com.linesquare.arunderground.model

/**
 * Represents real-time manual calibration offsets applied to the AR scene
 * to compensate for satellite GPS drift and align digital pipelines to physical landmarks (e.g. manhole covers).
 *
 * @param offsetX East/West lateral offset in meters
 * @param offsetY Vertical offset in meters (depth adjustment)
 * @param offsetZ North/South longitudinal offset in meters
 * @param rotationYawDeg Heading yaw rotation offset in degrees
 */
data class AlignmentOffset(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val rotationYawDeg: Float = 0f
)
