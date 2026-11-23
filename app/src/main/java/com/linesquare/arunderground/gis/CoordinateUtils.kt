package com.linesquare.arunderground.gis

import com.linesquare.arunderground.model.AlignmentOffset
import com.linesquare.arunderground.model.GeoPoint
import kotlin.math.*

/**
 * High-precision geospatial and coordinate transformation utilities for mapping
 * WGS84 GIS data into ARCore / Filament 3D metric coordinate space.
 */
object CoordinateUtils {

    private const val EARTH_RADIUS_METERS = 6378137.0 // WGS84 Earth semi-major axis

    /**
     * Calculates great-circle distance between two geodetic coordinates in meters.
     */
    fun calculateDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Projects a WGS84 GeoPoint to Local East-North-Up (ENU) coordinates (in meters)
     * relative to an origin GeoPoint.
     *
     * @param target Point to transform
     * @param origin Reference origin (e.g. user GPS anchor or local datum mark)
     * @return Triple of (eastMeters, northMeters, upMeters)
     */
    fun geodeticToEnu(target: GeoPoint, origin: GeoPoint): Triple<Double, Double, Double> {
        val latRad = Math.toRadians(origin.latitude)
        val metersPerLat = 111132.954 - 559.822 * cos(2 * latRad) + 1.175 * cos(4 * latRad)
        val metersPerLon = 111412.84 * cos(latRad) - 93.5 * cos(3 * latRad)

        val eastMeters = (target.longitude - origin.longitude) * metersPerLon
        val northMeters = (target.latitude - origin.latitude) * metersPerLat
        val upMeters = target.altitude - origin.altitude

        return Triple(eastMeters, northMeters, upMeters)
    }

    /**
     * Transforms Local ENU coordinates into ARCore / Filament camera metric coordinates
     * applying burial depth and manual calibration offsets.
     *
     * In ARCore:
     * - +X is right (East)
     * - +Y is up (Ground is Y = 0, subsurface depth is -Y)
     * - -Z is forward (North)
     *
     * @param target GeoPoint of pipe node
     * @param origin User / Reference GPS anchor point
     * @param depthM Pipe depth in meters (positive = below ground)
     * @param offset Manual calibration adjustment (X, Y, Z, Yaw)
     * @return FloatArray of size 3: [arX, arY, arZ]
     */
    fun toArCoordinates(
        target: GeoPoint,
        origin: GeoPoint,
        depthM: Float,
        offset: AlignmentOffset = AlignmentOffset()
    ): FloatArray {
        val (east, north, _) = geodeticToEnu(target, origin)

        // Convert to base AR coordinates
        var rawX = east.toFloat()
        val rawY = -depthM // Subsurface is negative Y below ground plane
        var rawZ = -north.toFloat() // In AR, -Z is forward (North)

        // Apply manual yaw rotation offset around Y-axis (up)
        if (offset.rotationYawDeg != 0f) {
            val yawRad = Math.toRadians(offset.rotationYawDeg.toDouble()).toFloat()
            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)

            val rotatedX = rawX * cosYaw - rawZ * sinYaw
            val rotatedZ = rawX * sinYaw + rawZ * cosYaw
            rawX = rotatedX
            rawZ = rotatedZ
        }

        // Apply manual translation offsets
        val finalX = rawX + offset.offsetX
        val finalY = rawY + offset.offsetY
        val finalZ = rawZ + offset.offsetZ

        return floatArrayOf(finalX, finalY, finalZ)
    }
}
