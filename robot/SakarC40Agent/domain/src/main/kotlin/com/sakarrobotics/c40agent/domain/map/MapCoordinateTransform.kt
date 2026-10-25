package com.sakarrobotics.c40agent.domain.map

import kotlin.math.floor

/**
 * Occupancy-grid metadata needed to convert between grid pixels and world metres.
 * Mirrors the fields a ROS `nav_msgs/MapMetaData` (and the backend map metadata) provides.
 */
data class OccupancyGridInfo(
    val resolutionMetersPerPixel: Double,
    val originX: Double,
    val originY: Double,
    val heightPixels: Int
) {
    init {
        require(resolutionMetersPerPixel > 0) { "resolutionMetersPerPixel must be > 0" }
        require(heightPixels > 0) { "heightPixels must be > 0" }
    }
}

/**
 * Occupancy-grid <-> world coordinate transform, standard ROS / cartographer convention
 * (`nav_msgs/MapMetaData`): the grid image is stored top-row-first while the map frame's Y axis
 * points up, so image row 0 is the TOP of the world map and world-Y increases as the row index
 * decreases.
 *
 * This is the documented, standard basis for a real-map renderer (see
 * research/c40-reverse-engineering/replica/COVERAGE_AND_GAP_REPORT.md section 4). It is NOT a
 * reproduction of the original app's (minified, unrecovered) OpenLayers projection internals, and
 * is only correct for a map that actually follows this convention. Nothing here fabricates map
 * data - it only converts coordinates for a map supplied by a real source.
 */
object MapCoordinateTransform {

    /** Pixel (col, row) cell-centre -> world (x, y) in metres, in the map frame. */
    fun pixelToWorld(col: Int, row: Int, info: OccupancyGridInfo): Pair<Double, Double> {
        val x = info.originX + (col + 0.5) * info.resolutionMetersPerPixel
        val y = info.originY + (info.heightPixels - (row + 0.5)) * info.resolutionMetersPerPixel
        return x to y
    }

    /**
     * World (x, y) in metres -> pixel (col, row). Does not clamp to grid bounds; callers that need
     * an on-grid pixel should validate against the grid width/height.
     */
    fun worldToPixel(worldX: Double, worldY: Double, info: OccupancyGridInfo): Pair<Int, Int> {
        val col = floor((worldX - info.originX) / info.resolutionMetersPerPixel).toInt()
        val row = info.heightPixels - 1 - floor((worldY - info.originY) / info.resolutionMetersPerPixel).toInt()
        return col to row
    }
}
