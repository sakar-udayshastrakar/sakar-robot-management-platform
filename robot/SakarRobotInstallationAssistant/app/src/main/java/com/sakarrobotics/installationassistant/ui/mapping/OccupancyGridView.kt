package com.sakarrobotics.installationassistant.ui.mapping

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sakarrobotics.installation.core.model.CellState
import com.sakarrobotics.installation.core.model.OccupancyGrid
import com.sakarrobotics.installationassistant.ui.theme.SakarColors

/** Draws a SIMULATED occupancy grid. Purely visual: the grid is demo data, never a real map. */
@Composable
fun OccupancyGridView(
    grid: OccupancyGrid,
    modifier: Modifier = Modifier,
    robotX: Int = -1,
    robotY: Int = -1,
    description: String
) {
    val ratio = if (grid.height == 0) 1.6f else grid.width.toFloat() / grid.height.toFloat()
    Canvas(
        modifier
            .aspectRatio(ratio)
            .background(SakarColors.CellUnknown, RoundedCornerShape(6.dp))
            .semantics { contentDescription = description }
    ) {
        if (grid.width == 0 || grid.height == 0) return@Canvas
        val cell = size.width / grid.width
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                val color = when (grid[x, y]) {
                    CellState.FREE -> SakarColors.CellFree
                    CellState.WALL -> SakarColors.CellWall
                    CellState.UNKNOWN -> null
                } ?: continue
                drawRect(color, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
        if (robotX >= 0 && robotY >= 0) {
            drawCircle(
                color = SakarColors.CellRobot,
                radius = cell * 0.9f,
                center = Offset((robotX + 0.5f) * cell, (robotY + 0.5f) * cell)
            )
        }
    }
}

@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics { contentDescription = "Map legend" }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(SakarColors.CellUnknown, "Not scanned")
        LegendItem(SakarColors.CellFree, "Free space")
        LegendItem(SakarColors.CellWall, "Wall")
        LegendItem(SakarColors.CellRobot, "Simulated robot position")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(16.dp).background(color, RoundedCornerShape(3.dp)).border(1.dp, SakarColors.Border, RoundedCornerShape(3.dp))
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = SakarColors.Muted)
    }
}
