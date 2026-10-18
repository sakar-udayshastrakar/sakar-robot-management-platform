package com.sakarrobotics.installation.core.sim

import com.sakarrobotics.installation.core.model.CellState
import com.sakarrobotics.installation.core.model.OccupancyGrid

/**
 * Deterministic fake "building" for a floor. It exists only so the simulated mapping canvas has
 * something to reveal - it is generated from the floor id and is not derived from any real site.
 */
object SimulatedFloorPlan {
    const val WIDTH = 32
    const val HEIGHT = 20

    /** Ground truth: [CellState.WALL] or [CellState.FREE] only. */
    fun generate(floorId: String): OccupancyGrid {
        val seed = floorId.fold(17) { acc, c -> acc * 31 + c.code }.let { if (it < 0) -it else it }
        val cells = Array(HEIGHT) { CharArray(WIDTH) { CellState.FREE.symbol } }

        for (x in 0 until WIDTH) { cells[0][x] = '#'; cells[HEIGHT - 1][x] = '#' }
        for (y in 0 until HEIGHT) { cells[y][0] = '#'; cells[y][WIDTH - 1] = '#' }

        // Two interior partition walls, each with a doorway whose position depends on the seed.
        val wallA = WIDTH / 3
        val wallB = 2 * WIDTH / 3
        val doorA = 3 + seed % (HEIGHT - 7)
        val doorB = 3 + (seed / 7) % (HEIGHT - 7)
        for (y in 1 until HEIGHT - 1) {
            if (y < doorA || y > doorA + 2) cells[y][wallA] = '#'
            if (y < doorB || y > doorB + 2) cells[y][wallB] = '#'
        }

        // A few pillars so different floors do not look identical.
        val pillars = 2 + seed % 3
        for (i in 0 until pillars) {
            val px = 2 + (seed / (i + 3) + i * 5) % (WIDTH - 4)
            val py = 2 + (seed / (i + 5) + i * 3) % (HEIGHT - 4)
            if (cells[py][px] == '.') cells[py][px] = '#'
        }
        return OccupancyGrid(cells.map { String(it) })
    }
}

/**
 * Pure, timer-free mapping progress model. [step] reveals more of the [truth] plan along a
 * sweeping path; walls next to revealed floor cells are revealed with them. Nothing here talks to
 * a robot: it only manipulates an in-memory grid.
 */
class MappingSimulation(
    private val truth: OccupancyGrid,
    private val cellsPerStep: Int = 10
) {
    private val width = truth.width
    private val height = truth.height
    private val revealed = Array(height) { BooleanArray(width) }

    /** Free cells in sweep order (boustrophedon: left-to-right, then right-to-left, ...). */
    private val path: List<Pair<Int, Int>> = buildList {
        for (y in 0 until height) {
            val xs = if (y % 2 == 0) 0 until width else (width - 1 downTo 0)
            for (x in xs) if (truth[x, y] == CellState.FREE) add(x to y)
        }
    }

    private var pathIndex = 0
    var robotX: Int = path.firstOrNull()?.first ?: -1
        private set
    var robotY: Int = path.firstOrNull()?.second ?: -1
        private set

    val totalFree: Int get() = path.size
    val revealedFree: Int get() = pathIndex

    val progressPercent: Int
        get() = if (totalFree == 0) 100 else (revealedFree * 100) / totalFree

    val isComplete: Boolean get() = revealedFree >= totalFree

    /** Reveals the next chunk of the sweep. Returns true while more remains. */
    fun step(): Boolean {
        var n = 0
        while (n < cellsPerStep && pathIndex < path.size) {
            val (x, y) = path[pathIndex]
            reveal(x, y)
            robotX = x
            robotY = y
            pathIndex++
            n++
        }
        return !isComplete
    }

    private fun reveal(x: Int, y: Int) {
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height) {
                // Floor cells are only revealed when the sweep reaches them; adjacent walls come with it.
                if (dx == 0 && dy == 0 || truth[nx, ny] == CellState.WALL) revealed[ny][nx] = true
            }
        }
    }

    fun snapshot(): OccupancyGrid = OccupancyGrid(
        List(height) { y ->
            buildString {
                for (x in 0 until width) {
                    append(if (revealed[y][x]) truth[x, y].symbol else CellState.UNKNOWN.symbol)
                }
            }
        }
    )
}
