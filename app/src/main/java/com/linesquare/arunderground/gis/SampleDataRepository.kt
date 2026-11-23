package com.linesquare.arunderground.gis

import com.linesquare.arunderground.model.GeoPoint
import com.linesquare.arunderground.model.UtilityPipe
import com.linesquare.arunderground.model.UtilityType

/**
 * Pre-configured municipal subsurface dataset providing test scenarios for
 * Water, Sewer, Gas, and Sanitary networks with accurate engineering attributes.
 */
object SampleDataRepository {

    /**
     * Default demonstration site origin (Islamabad civic center / benchmark datum)
     */
    val DEFAULT_ORIGIN = GeoPoint(
        latitude = 33.6844,
        longitude = 73.0479,
        altitude = 540.0
    )

    fun getSamplePipes(): List<UtilityPipe> = listOf(
        // Water Pipeline 1 - Major Distribution Trunk
        UtilityPipe(
            id = "W-101",
            utilityType = UtilityType.WATER,
            diameterMm = 200,
            depthM = 1.2f,
            material = "PVC Class 12",
            installYear = 2014,
            coordinates = listOf(
                GeoPoint(33.6844539, 73.0477273),
                GeoPoint(33.6844539, 73.0478460),
                GeoPoint(33.6844539, 73.0479540),
                GeoPoint(33.6844539, 73.0480943)
            )
        ),

        // Water Pipeline 2 - Service Connection
        UtilityPipe(
            id = "W-102",
            utilityType = UtilityType.WATER,
            diameterMm = 100,
            depthM = 0.9f,
            material = "HDPE PN16",
            installYear = 2019,
            coordinates = listOf(
                GeoPoint(33.6843461, 73.0479864),
                GeoPoint(33.6844359, 73.0479864),
                GeoPoint(33.6845258, 73.0479864),
                GeoPoint(33.6846336, 73.0479864)
            )
        ),

        // Sewer Main Line - Deep Gravity Sewer
        UtilityPipe(
            id = "S-201",
            utilityType = UtilityType.SEWER,
            diameterMm = 400,
            depthM = 2.6f,
            material = "Reinforced Concrete",
            installYear = 2005,
            coordinates = listOf(
                GeoPoint(33.6843102, 73.0478352),
                GeoPoint(33.6844000, 73.0478352),
                GeoPoint(33.6844943, 73.0478568),
                GeoPoint(33.6845976, 73.0478784),
                GeoPoint(33.6847054, 73.0479000)
            )
        ),

        // Sewer Lateral Branch
        UtilityPipe(
            id = "S-202",
            utilityType = UtilityType.SEWER,
            diameterMm = 250,
            depthM = 1.9f,
            material = "Vitrified Clay",
            installYear = 2008,
            coordinates = listOf(
                GeoPoint(33.6845078, 73.0476949),
                GeoPoint(33.6845033, 73.0477920),
                GeoPoint(33.6844943, 73.0478568)
            )
        ),

        // High-Pressure Gas Line
        UtilityPipe(
            id = "G-301",
            utilityType = UtilityType.GAS,
            diameterMm = 110,
            depthM = 0.8f,
            material = "Polyethylene PE100",
            installYear = 2017,
            coordinates = listOf(
                GeoPoint(33.6844269, 73.0477705),
                GeoPoint(33.6844269, 73.0479000),
                GeoPoint(33.6844269, 73.0480295),
                GeoPoint(33.6844269, 73.0481375)
            )
        ),

        // Sanitary Drain Line
        UtilityPipe(
            id = "SN-401",
            utilityType = UtilityType.SANITARY,
            diameterMm = 150,
            depthM = 1.5f,
            material = "uPVC Drainage Pipe",
            installYear = 2011,
            coordinates = listOf(
                GeoPoint(33.6843281, 73.0479324),
                GeoPoint(33.6844000, 73.0479324),
                GeoPoint(33.6844808, 73.0479324),
                GeoPoint(33.6845976, 73.0479324)
            )
        )
    )
}
