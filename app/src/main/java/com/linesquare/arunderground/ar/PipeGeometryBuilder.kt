package com.linesquare.arunderground.ar

import com.linesquare.arunderground.gis.CoordinateUtils
import com.linesquare.arunderground.model.AlignmentOffset
import com.linesquare.arunderground.model.GeoPoint
import com.linesquare.arunderground.model.UtilityPipe
import kotlin.math.sqrt

/**
 * Calculates 3D geometric segments, orientations, and coordinates for underground pipes
 * to be rendered in Google Filament / ARSceneView.
 */
object PipeGeometryBuilder {

    /**
     * Converts a collection of GIS utility pipes into 3D metric segments ready for AR rendering.
     *
     * @param pipes List of utility pipelines to generate
     * @param origin Current GPS reference origin (user location or datum)
     * @param offset Manual calibration offset (X, Y, Z, Yaw)
     * @return List of 3D pipeline segments
     */
    fun buildSegments(
        pipes: List<UtilityPipe>,
        origin: GeoPoint,
        offset: AlignmentOffset = AlignmentOffset()
    ): List<PipeSegment3D> {
        val segments = mutableListOf<PipeSegment3D>()

        for (pipe in pipes) {
            val coords = pipe.coordinates
            if (coords.size < 2) continue

            for (i in 0 until coords.size - 1) {
                val startAr = CoordinateUtils.toArCoordinates(
                    target = coords[i],
                    origin = origin,
                    depthM = pipe.depthM,
                    offset = offset
                )

                val endAr = CoordinateUtils.toArCoordinates(
                    target = coords[i + 1],
                    origin = origin,
                    depthM = pipe.depthM,
                    offset = offset
                )

                val dx = endAr[0] - startAr[0]
                val dy = endAr[1] - startAr[1]
                val dz = endAr[2] - startAr[2]
                val length = sqrt(dx * dx + dy * dy + dz * dz)

                val center = floatArrayOf(
                    (startAr[0] + endAr[0]) / 2f,
                    (startAr[1] + endAr[1]) / 2f,
                    (startAr[2] + endAr[2]) / 2f
                )

                segments.add(
                    PipeSegment3D(
                        pipe = pipe,
                        segmentIndex = i,
                        startPosition = startAr,
                        endPosition = endAr,
                        lengthMeters = length,
                        centerPosition = center
                    )
                )
            }
        }

        return segments
    }
}
