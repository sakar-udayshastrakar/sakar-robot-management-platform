package com.sakarrobotics.installationassistant.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sakarrobotics.installation.core.logic.WizardLogic
import com.sakarrobotics.installation.core.model.BackendMode
import com.sakarrobotics.installationassistant.ui.LocalServices
import com.sakarrobotics.installationassistant.ui.components.AssistantScaffold
import com.sakarrobotics.installationassistant.ui.components.BigTile
import com.sakarrobotics.installationassistant.ui.components.SakarWordmark
import com.sakarrobotics.installationassistant.ui.components.SectionCard
import com.sakarrobotics.installationassistant.ui.components.SimulatedTag
import com.sakarrobotics.installationassistant.ui.theme.SakarColors

private data class HomeTile(
    val tag: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val emphasized: Boolean,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    onNewInstallation: () -> Unit,
    onMapping: () -> Unit,
    onDiagnostics: () -> Unit,
    onFloors: () -> Unit,
    onChecklist: () -> Unit,
    onReport: () -> Unit,
    onSettings: () -> Unit
) {
    val services = LocalServices.current
    val installation by services.installations.current.collectAsState()
    val floors by services.floors.floors.collectAsState()
    val maps by services.mapping.maps.collectAsState()
    val reports by services.reports.reports.collectAsState()

    val statuses = remember(installation, floors, maps) { WizardLogic.statuses(installation, floors, maps) }
    val done = statuses.count { it.isComplete }
    val checklistDone = installation?.checklist?.count {
        it.state == com.sakarrobotics.installation.core.model.ChecklistState.DONE ||
            it.state == com.sakarrobotics.installation.core.model.ChecklistState.NOT_APPLICABLE
    }

    val tiles = listOf(
        HomeTile(
            "tile_new_installation", "New Installation",
            if (installation == null) "Start a guided, simulated installation"
            else "Resume ${installation!!.id} - $done of ${statuses.size} steps complete",
            Icons.Filled.PlayCircle, true, onNewInstallation
        ),
        HomeTile(
            "tile_mapping", "Mapping & Maps",
            "Simulated mapping workspace - ${maps.size} saved simulated map(s)",
            Icons.Filled.Map, false, onMapping
        ),
        HomeTile(
            "tile_diagnostics", "Sensor Diagnostics",
            "Simulated sensor and service checks with troubleshooting guidance",
            Icons.Filled.Sensors, false, onDiagnostics
        ),
        HomeTile(
            "tile_floors", "Floor Management",
            "${floors.size} floor(s) - manage simulated maps per floor",
            Icons.Filled.Layers, false, onFloors
        ),
        HomeTile(
            "tile_checklist", "Installation Checklist",
            if (checklistDone == null) "Track site-readiness items with notes"
            else "$checklistDone of ${installation!!.checklist.size} items resolved",
            Icons.Filled.Checklist, false, onChecklist
        ),
        HomeTile(
            "tile_report", "Installation Report",
            if (reports.isEmpty()) "Generate a local SIMULATION ONLY report"
            else "${reports.size} saved report(s)",
            Icons.Filled.Description, false, onReport
        ),
        HomeTile(
            "tile_settings", "Settings & About",
            "Version, backup and restore, reset demo data",
            Icons.Filled.Settings, false, onSettings
        )
    )

    AssistantScaffold(title = "Installation Home", onBack = null) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.fillMaxSize().testTag("home_grid"),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SakarWordmark()
                    Text(
                        "Robot Installation Assistant",
                        style = MaterialTheme.typography.headlineMedium,
                        color = SakarColors.Ink
                    )
                    BackendCard(services.mapping.backend.mode, services.mapping.backend.description)
                }
            }
            items(tiles, key = { it.tag }) { tile ->
                BigTile(
                    title = tile.title,
                    subtitle = tile.subtitle,
                    icon = tile.icon,
                    onClick = tile.onClick,
                    emphasized = tile.emphasized,
                    modifier = Modifier.testTag(tile.tag)
                )
            }
        }
    }
}

@Composable
private fun BackendCard(mode: BackendMode, description: String) {
    SectionCard(modifier = Modifier.testTag("backend_card")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SimulatedTag()
            Text(
                "Backend: ${mode.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("backend_mode")
            )
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
        Text(
            "Hardware integration: not implemented and disabled in this release.",
            style = MaterialTheme.typography.bodyMedium,
            color = SakarColors.Muted
        )
    }
}
