package com.linesquare.arunderground.ar

import com.linesquare.arunderground.model.UtilityPipe

/**
 * Metadata container for a 3D subsurface pipeline entity in AR space.
 * Holds reference to the underlying engineering data model for raycast selection.
 *
 * @param pipe The utility pipe asset specification
 * @param segmentIndex Index of the pipe segment (between waypoint[i] and waypoint[i+1])
 * @param startPosition 3D metric start position [x, y, z] in AR space
 * @param endPosition 3D metric end position [x, y, z] in AR space
 */
data class PipeSegment3D(
    val pipe: UtilityPipe,
    val segmentIndex: Int,
    val startPosition: FloatArray,
    val endPosition: FloatArray,
    val lengthMeters: Float,
    val centerPosition: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PipeSegment3D) return false
        return pipe.id == other.pipe.id && segmentIndex == other.segmentIndex
    }

    override fun hashCode(): Int {
        var result = pipe.id.hashCode()
        result = 31 * result + segmentIndex
        return result
    }
}
