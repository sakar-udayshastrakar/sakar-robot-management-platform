package com.sakarrobotics.installationassistant.ui.floors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.logic.ReportBuilder
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.MapStatus
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installationassistant.ui.Feedback
import com.sakarrobotics.installationassistant.ui.LocalServices
import com.sakarrobotics.installationassistant.ui.components.AssistantButton
import com.sakarrobotics.installationassistant.ui.components.AssistantScaffold
import com.sakarrobotics.installationassistant.ui.components.ButtonKind
import com.sakarrobotics.installationassistant.ui.components.ConfirmDialog
import com.sakarrobotics.installationassistant.ui.components.EmptyState
import com.sakarrobotics.installationassistant.ui.components.MessageBanner
import com.sakarrobotics.installationassistant.ui.components.SectionCard
import com.sakarrobotics.installationassistant.ui.components.SimulatedTag
import com.sakarrobotics.installationassistant.ui.components.StatusChip
import com.sakarrobotics.installationassistant.ui.components.TextInputDialog
import com.sakarrobotics.installationassistant.ui.components.Tone
import com.sakarrobotics.installationassistant.ui.mapping.OccupancyGridView
import com.sakarrobotics.installationassistant.ui.theme.SakarColors
import com.sakarrobotics.installationassistant.ui.toFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FloorsViewModel(services: AssistantServices) : ViewModel() {
    private val floorRepo = services.floors
    private val mapRepo = services.mapping

    val floors: StateFlow<List<Floor>> = floorRepo.floors
    val maps: StateFlow<List<SimMap>> = mapRepo.maps

    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    fun addFloor(name: String): String? = nameResult(floorRepo.addFloor(name)) { "Added floor \"${it.name}\"." }
    fun renameFloor(id: String, name: String): String? = nameResult(floorRepo.renameFloor(id, name)) { "Renamed floor to \"${it.name}\"." }
    fun renameMap(id: String, name: String): String? = nameResult(mapRepo.renameMap(id, name)) { "Renamed map to \"${it.name}\"." }

    fun deleteFloor(id: String) {
        _feedback.value = floorRepo.deleteFloor(id).toFeedback("Deleted the floor.")
    }

    fun deleteMap(id: String) {
        _feedback.value = mapRepo.deleteMap(id).toFeedback("Deleted the simulated map.")
    }

    fun dismissFeedback() {
        _feedback.value = null
    }

    private fun <T> nameResult(outcome: Outcome<T>, success: (T) -> String): String? = when (outcome) {
        is Outcome.Success -> {
            _feedback.value = Feedback(success(outcome.value), Tone.SUCCESS)
            null
        }
        is Outcome.Failure -> outcome.message
    }
}

private sealed interface Dialog {
    data object AddFloor : Dialog
    data class RenameFloor(val floor: Floor) : Dialog
    data class DeleteFloor(val floor: Floor) : Dialog
    data class RenameMap(val map: SimMap) : Dialog
    data class DeleteMap(val map: SimMap) : Dialog
}

@Composable
fun FloorsScreen(onBack: () -> Unit, onOpenMapping: (String) -> Unit) {
    val services = LocalServices.current
    val vm: FloorsViewModel = viewModel(factory = viewModelFactory { initializer { FloorsViewModel(services) } })
    val floors by vm.floors.collectAsState()
    val maps by vm.maps.collectAsState()
    val feedback by vm.feedback.collectAsState()
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    AssistantScaffold(title = "Floor Management", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("floors_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    feedback?.let { MessageBanner(it.text, it.tone, Modifier.testTag("floors_feedback"), onDismiss = vm::dismissFeedback) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "${floors.size} floor(s), ${maps.size} simulated map(s). Changes affect demo data on this tablet only.",
                            style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted,
                            modifier = Modifier.weight(1f)
                        )
                        AssistantButton("Add floor", onClick = { dialog = Dialog.AddFloor }, icon = Icons.Filled.Add, modifier = Modifier.testTag("btn_add_floor"))
                    }
                }
            }
            if (floors.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.Layers, "No floors", "Add a floor to start creating simulated maps.", actionLabel = "Add floor", onAction = { dialog = Dialog.AddFloor })
                }
            }
            items(floors, key = { it.id }) { floor ->
                FloorCard(
                    floor = floor,
                    maps = maps.filter { it.floorId == floor.id },
                    onMap = { onOpenMapping(floor.id) },
                    onRename = { dialog = Dialog.RenameFloor(floor) },
                    onDelete = { dialog = Dialog.DeleteFloor(floor) },
                    onRenameMap = { dialog = Dialog.RenameMap(it) },
                    onDeleteMap = { dialog = Dialog.DeleteMap(it) }
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.AddFloor -> TextInputDialog(
            "Add floor", "Floor name", "", "Add",
            onConfirm = vm::addFloor, onDismiss = { dialog = null }
        )
        is Dialog.RenameFloor -> TextInputDialog(
            "Rename floor", "Floor name", d.floor.name, "Rename",
            onConfirm = { vm.renameFloor(d.floor.id, it) }, onDismiss = { dialog = null }
        )
        is Dialog.RenameMap -> TextInputDialog(
            "Rename simulated map", "Map name", d.map.name, "Rename",
            onConfirm = { vm.renameMap(d.map.id, it) }, onDismiss = { dialog = null }
        )
        is Dialog.DeleteFloor -> ConfirmDialog(
            title = "Delete floor \"${d.floor.name}\"?",
            text = "This removes the floor from this tablet's demo data. A floor that still has simulated maps cannot be deleted.",
            confirmLabel = "Delete floor", danger = true,
            onConfirm = { vm.deleteFloor(d.floor.id); dialog = null },
            onDismiss = { dialog = null }
        )
        is Dialog.DeleteMap -> ConfirmDialog(
            title = "Delete simulated map \"${d.map.name}\"?",
            text = "This only removes demo data on this tablet. It cannot be undone, and no real map is affected.",
            confirmLabel = "Delete map", danger = true,
            onConfirm = { vm.deleteMap(d.map.id); dialog = null },
            onDismiss = { dialog = null }
        )
    }
}

@Composable
private fun FloorCard(
    floor: Floor,
    maps: List<SimMap>,
    onMap: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRenameMap: (SimMap) -> Unit,
    onDeleteMap: (SimMap) -> Unit
) {
    SectionCard(modifier = Modifier.testTag("floor_card_${floor.name}"), title = floor.name, trailing = { StatusChip("Level ${floor.level}", Tone.NEUTRAL) }) {
        if (maps.isEmpty()) {
            Text("No simulated maps on this floor yet.", style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
        }
        maps.forEach { map -> MapRow(map, onRenameMap, onDeleteMap) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AssistantButton("Map this floor", onMap, icon = Icons.Filled.Map, modifier = Modifier.testTag("map_floor_${floor.name}"))
            AssistantButton("Rename", onRename, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Edit, modifier = Modifier.testTag("rename_floor_${floor.name}"))
            AssistantButton("Delete", onDelete, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Delete, modifier = Modifier.testTag("delete_floor_${floor.name}"))
        }
    }
}

@Composable
private fun MapRow(map: SimMap, onRename: (SimMap) -> Unit, onDelete: (SimMap) -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("map_row_${map.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OccupancyGridView(
            map.preview, Modifier.width(120.dp),
            description = "Preview of simulated map ${map.name}"
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(map.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "ID ${map.id} - created ${ReportBuilder.formatTimestamp(map.createdAtMillis)}",
                style = MaterialTheme.typography.bodySmall, color = SakarColors.Muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(map.status.label, if (map.status == MapStatus.COMPLETE) Tone.SUCCESS else Tone.WARNING)
                SimulatedTag()
                Text("${map.coveragePercent}% coverage", style = MaterialTheme.typography.bodySmall, color = SakarColors.Muted)
            }
        }
        AssistantButton("Rename", { onRename(map) }, kind = ButtonKind.SECONDARY, modifier = Modifier.testTag("rename_map_${map.name}"))
        AssistantButton("Delete", { onDelete(map) }, kind = ButtonKind.SECONDARY, modifier = Modifier.testTag("delete_map_${map.name}"))
    }
}
