package com.linesquare.arunderground.model

/**
 * Represents a geographical coordinate in WGS-84 coordinate reference system.
 *
 * @param latitude Decimal degrees
 * @param longitude Decimal degrees
 * @param altitude Elevation above sea level or surface in meters
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
)
