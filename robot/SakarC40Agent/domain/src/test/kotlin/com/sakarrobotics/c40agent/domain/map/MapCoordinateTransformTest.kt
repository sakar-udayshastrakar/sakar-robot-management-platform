package com.sakarrobotics.c40agent.domain.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MapCoordinateTransformTest {

    // 100x100 grid, 5cm/pixel, origin at world (0,0).
    private val info = OccupancyGridInfo(resolutionMetersPerPixel = 0.05, originX = 0.0, originY = 0.0, heightPixels = 100)

    @Test
    fun `bottom-left pixel maps near the world origin`() {
        // Row 99 (bottom image row) col 0 is the bottom-left of the world map.
        val (x, y) = MapCoordinateTransform.pixelToWorld(col = 0, row = 99, info = info)
        assertEquals(0.025, x, 1e-9)
        assertEquals(0.025, y, 1e-9)
    }

    @Test
    fun `top-left pixel maps to the top of the world map`() {
        val (_, y) = MapCoordinateTransform.pixelToWorld(col = 0, row = 0, info = info)
        // Row 0 is the TOP row -> highest world Y.
        assertEquals(100 - 0.5, y / info.resolutionMetersPerPixel, 1e-9)
    }

    @Test
    fun `worldToPixel is the inverse of pixelToWorld for cell centres`() {
        for (row in intArrayOf(0, 1, 50, 98, 99)) {
            for (col in intArrayOf(0, 1, 42, 99)) {
                val (wx, wy) = MapCoordinateTransform.pixelToWorld(col, row, info)
                val (c2, r2) = MapCoordinateTransform.worldToPixel(wx, wy, info)
                assertEquals(col, c2, "col round-trip failed at ($col,$row)")
                assertEquals(row, r2, "row round-trip failed at ($col,$row)")
            }
        }
    }

    @Test
    fun `respects a non-zero origin`() {
        val shifted = info.copy(originX = -2.0, originY = 1.0)
        val (x, y) = MapCoordinateTransform.pixelToWorld(col = 0, row = 99, info = shifted)
        assertEquals(-2.0 + 0.025, x, 1e-9)
        assertEquals(1.0 + 0.025, y, 1e-9)
    }

    @Test
    fun `rejects invalid grid metadata`() {
        assertThrows(IllegalArgumentException::class.java) {
            OccupancyGridInfo(resolutionMetersPerPixel = 0.0, originX = 0.0, originY = 0.0, heightPixels = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OccupancyGridInfo(resolutionMetersPerPixel = 0.05, originX = 0.0, originY = 0.0, heightPixels = 0)
        }
    }
}
