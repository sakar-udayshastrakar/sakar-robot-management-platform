package com.sakarrobotics.installationassistant.ui.mapping

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.MapStatus
import com.sakarrobotics.installation.core.model.MappingPhase
import com.sakarrobotics.installation.core.model.MappingSessionState
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
import com.sakarrobotics.installationassistant.ui.components.Tone
import com.sakarrobotics.installationassistant.ui.theme.SakarColors
import com.sakarrobotics.installationassistant.ui.toFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MappingViewModel(private val services: AssistantServices, initialFloorId: String?) : ViewModel() {

    private val repo = services.mapping

    val session: StateFlow<MappingSessionState> = repo.session
    val maps: StateFlow<List<SimMap>> = repo.maps
    val loadedMap: StateFlow<SimMap?> = repo.loadedMap
    val floors: StateFlow<List<Floor>> = services.floors.floors

    private val _selectedFloorId = MutableStateFlow(
        initialFloorId?.takeIf { id -> floors.value.any { it.id == id } } ?: floors.value.firstOrNull()?.id
    )
    val selectedFloorId: StateFlow<String?> = _selectedFloorId.asStateFlow()

    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    fun selectFloor(id: String) {
        if (!session.value.isActive) _selectedFloorId.value = id
    }

    fun start() {
        val floorId = _selectedFloorId.value
        _feedback.value = if (floorId == null) {
            Feedback("Add a floor in Floor Management before starting a simulated session.", Tone.DANGER)
        } else repo.startSession(floorId).toFeedback()
    }

    /** Called when the workspace is left or the app stops: a running simulation is paused, never left ticking. */
    fun interrupt(reason: String) = repo.interrupt(reason)

    fun pause() = report(repo.pause())
    fun resume() = report(repo.resume())
    fun finish() = report(repo.finish())
    fun cancel() = report(repo.cancel())
    fun close() = repo.closeLoadedMap()
    fun open(id: String) = report(repo.loadMap(id))

    /** Returns an error message to show next to the name field, or null on success. */
    fun save(name: String): String? = when (val result = repo.saveMap(name)) {
        is Outcome.Success -> {
            _feedback.value = Feedback("Saved simulated map \"${result.value.name}\".", Tone.SUCCESS)
            null
        }
        is Outcome.Failure -> result.message
    }

    fun dismissFeedback() {
        _feedback.value = null
    }

    private fun <T> report(outcome: Outcome<T>) {
        _feedback.value = outcome.toFeedback()
    }
}

@Composable
fun MappingScreen(initialFloorId: String?, onBack: () -> Unit, onOpenFloors: () -> Unit) {
    val services = LocalServices.current
    val vm: MappingViewModel = viewModel(
        factory = viewModelFactory { initializer { MappingViewModel(services, initialFloorId) } }
    )
    val session by vm.session.collectAsState()
    val maps by vm.maps.collectAsState()
    val loaded by vm.loadedMap.collectAsState()
    val floors by vm.floors.collectAsState()
    val selectedFloorId by vm.selectedFloorId.collectAsState()
    val feedback by vm.feedback.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.interrupt("Paused because you left the workspace or the app went to the background.")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.interrupt("Paused because you left the workspace or the app went to the background.")
        }
    }

    AssistantScaffold(title = "Mapping Workspace", onBack = onBack) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            val canvas: @Composable (Modifier) -> Unit = { m ->
                CanvasPanel(session, loaded, floors, m)
            }
            val controls: @Composable (Modifier) -> Unit = { m ->
                ControlsPanel(
                    vm, session, floors, selectedFloorId, maps, loaded, feedback, onOpenFloors, m
                )
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState())) { canvas(Modifier) }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls(Modifier) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    canvas(Modifier)
                    controls(Modifier)
                }
            }
        }
    }
}

@Composable
private fun CanvasPanel(session: MappingSessionState, loaded: SimMap?, floors: List<Floor>, modifier: Modifier) {
    SectionCard(modifier = modifier.testTag("map_canvas_card"), title = "Simulated map canvas", trailing = { SimulatedTag() }) {
        val grid = session.grid
        when {
            grid != null -> {
                OccupancyGridView(
                    grid, Modifier.fillMaxWidth().testTag("map_canvas"),
                    robotX = session.robotX, robotY = session.robotY,
                    description = "Simulated map canvas, ${session.progressPercent} percent scanned. Not a real map."
                )
                Text(
                    "Simulated session on ${floors.firstOrNull { it.id == session.floorId }?.name ?: "selected floor"}",
                    style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted
                )
            }
            loaded != null -> {
                OccupancyGridView(
                    loaded.preview, Modifier.fillMaxWidth().testTag("map_canvas"),
                    description = "Preview of saved simulated map ${loaded.name}. Not a real map."
                )
                Text(
                    "Saved simulated map: ${loaded.name} (${floors.firstOrNull { it.id == loaded.floorId }?.name ?: "unknown floor"})",
                    style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted
                )
            }
            else -> {
                OccupancyGridView(
                    com.sakarrobotics.installation.core.model.OccupancyGrid.unknown(32, 20),
                    Modifier.fillMaxWidth().testTag("map_canvas"),
                    description = "Empty simulated map canvas"
                )
                Text(
                    "Nothing scanned yet. Choose a floor and start a simulated session, or open a saved simulated map.",
                    style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted
                )
            }
        }
        MapLegend()
    }
}

@Composable
private fun ControlsPanel(
    vm: MappingViewModel,
    session: MappingSessionState,
    floors: List<Floor>,
    selectedFloorId: String?,
    maps: List<SimMap>,
    loaded: SimMap?,
    feedback: Feedback?,
    onOpenFloors: () -> Unit,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MessageBanner(
            "This is a SIMULATED mapping session. The physical robot is not mapping and no real map is created.",
            Tone.WARNING,
            Modifier.testTag("mapping_sim_notice")
        )
        feedback?.let { MessageBanner(it.text, it.tone, Modifier.testTag("mapping_feedback"), onDismiss = vm::dismissFeedback) }

        SectionCard(title = "Floor") {
            if (floors.isEmpty()) {
                EmptyState(Icons.Filled.Layers, "No floors", "Add a floor to start a simulated session.", actionLabel = "Floor Management", onAction = onOpenFloors)
            } else {
                Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    floors.forEach { floor ->
                        FilterChip(
                            selected = floor.id == selectedFloorId,
                            onClick = { vm.selectFloor(floor.id) },
                            enabled = !session.isActive,
                            label = { Text(floor.name) },
                            modifier = Modifier.testTag("floor_chip_${floor.id}")
                        )
                    }
                }
            }
        }

        SectionCard(title = "Simulated session", trailing = { PhaseChip(session.phase) }) {
            LinearProgressIndicator(
                progress = { session.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Simulated progress ${session.progressPercent} percent" },
                color = SakarColors.Orange
            )
            Text("${session.progressPercent}% of the simulated floor plan scanned", modifier = Modifier.testTag("mapping_progress"))
            session.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted, modifier = Modifier.testTag("mapping_message"))
            }
            SessionButtons(vm, session, floors.isNotEmpty())
            if (session.phase == MappingPhase.FINISHED) {
                SaveForm(vm, floors.firstOrNull { it.id == session.floorId }?.name)
            }
        }

        SectionCard(title = "Saved simulated maps") {
            if (maps.isEmpty()) {
                EmptyState(Icons.Filled.Map, "No saved maps yet", "Finish a simulated scan and save it to see it here.")
            } else {
                maps.forEach { map ->
                    SavedMapRow(
                        map = map,
                        floorName = floors.firstOrNull { it.id == map.floorId }?.name ?: "Unknown floor",
                        open = loaded?.id == map.id,
                        onOpen = { vm.open(map.id) }
                    )
                }
            }
            if (loaded != null) {
                AssistantButton("Close preview", onClick = vm::close, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Close, modifier = Modifier.testTag("close_preview"))
            }
            AssistantButton("Manage maps and floors", onClick = onOpenFloors, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Layers)
        }
    }
}

@Composable
private fun SessionButtons(vm: MappingViewModel, session: MappingSessionState, hasFloors: Boolean) {
    var confirm by remember { mutableStateOf<DiscardAction?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hScroll()) {
        when (session.phase) {
            MappingPhase.IDLE, MappingPhase.CANCELLED, MappingPhase.FINISHED -> {
                AssistantButton(
                    if (session.phase == MappingPhase.FINISHED) "Start again" else "Start simulated mapping",
                    onClick = {
                        // A finished but unsaved scan would be replaced, so ask first.
                        if (session.phase == MappingPhase.FINISHED) confirm = DiscardAction.RESTART else vm.start()
                    },
                    icon = Icons.Filled.PlayArrow, enabled = hasFloors,
                    kind = if (session.phase == MappingPhase.FINISHED) ButtonKind.SECONDARY else ButtonKind.PRIMARY,
                    modifier = Modifier.testTag("btn_start")
                )
                if (session.phase == MappingPhase.FINISHED) {
                    AssistantButton("Discard", onClick = { confirm = DiscardAction.DISCARD }, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Close, modifier = Modifier.testTag("btn_discard"))
                }
            }
            MappingPhase.MAPPING -> {
                AssistantButton("Pause", onClick = vm::pause, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Pause, modifier = Modifier.testTag("btn_pause"))
                AssistantButton("Finish", onClick = vm::finish, icon = Icons.Filled.Stop, modifier = Modifier.testTag("btn_finish"))
                AssistantButton("Cancel", onClick = { confirm = DiscardAction.CANCEL }, kind = ButtonKind.DANGER, icon = Icons.Filled.Close, modifier = Modifier.testTag("btn_cancel"))
            }
            MappingPhase.PAUSED -> {
                AssistantButton("Resume", onClick = vm::resume, icon = Icons.Filled.PlayArrow, modifier = Modifier.testTag("btn_resume"))
                AssistantButton("Finish", onClick = vm::finish, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Stop, modifier = Modifier.testTag("btn_finish"))
                AssistantButton("Cancel", onClick = { confirm = DiscardAction.CANCEL }, kind = ButtonKind.DANGER, icon = Icons.Filled.Close, modifier = Modifier.testTag("btn_cancel"))
            }
        }
    }
    if (!hasFloors) {
        Text("Add a floor first - Start is unavailable without one.", style = MaterialTheme.typography.bodySmall, color = SakarColors.Muted)
    }
    confirm?.let { action ->
        ConfirmDialog(
            title = action.title,
            text = "The simulated scan (${session.progressPercent}%) has not been saved as a map and will be lost. " +
                "Nothing real is affected - this is a simulation.",
            confirmLabel = action.confirmLabel, danger = true,
            onConfirm = {
                confirm = null
                if (action == DiscardAction.RESTART) {
                    vm.cancel()
                    vm.start()
                } else {
                    vm.cancel()
                }
            },
            onDismiss = { confirm = null }
        )
    }
}

private enum class DiscardAction(val title: String, val confirmLabel: String) {
    CANCEL("Cancel the simulated session?", "Discard scan"),
    DISCARD("Discard the finished scan?", "Discard scan"),
    RESTART("Start a new scan?", "Discard and start again")
}

@Composable
private fun SaveForm(vm: MappingViewModel, floorName: String?) {
    var name by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Name this simulated map", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Map name${floorName?.let { " ($it)" } ?: ""}") },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.fillMaxWidth().testTag("map_name_input")
        )
        if (error != null) {
            Text(error!!, color = SakarColors.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("map_name_error"))
        }
        AssistantButton("Save simulated map", onClick = {
            val result = vm.save(name)
            if (result == null) name = "" else error = result
        }, icon = Icons.Filled.Save, modifier = Modifier.testTag("btn_save_map"))
    }
}

@Composable
private fun PhaseChip(phase: MappingPhase) {
    val (text, tone) = when (phase) {
        MappingPhase.IDLE -> "Idle" to Tone.NEUTRAL
        MappingPhase.MAPPING -> "Simulated mapping" to Tone.BRAND
        MappingPhase.PAUSED -> "Paused (simulated)" to Tone.WARNING
        MappingPhase.FINISHED -> "Finished (simulated)" to Tone.SUCCESS
        MappingPhase.CANCELLED -> "Cancelled" to Tone.NEUTRAL
    }
    StatusChip(text, tone, Modifier.testTag("mapping_phase"))
}

@Composable
private fun SavedMapRow(map: SimMap, floorName: String, open: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("saved_map_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(map.name, style = MaterialTheme.typography.titleSmall)
            Text("$floorName - ${map.coveragePercent}% coverage", style = MaterialTheme.typography.bodySmall, color = SakarColors.Muted)
        }
        StatusChip(
            if (map.status == MapStatus.COMPLETE) "Complete (sim)" else "Partial (sim)",
            if (map.status == MapStatus.COMPLETE) Tone.SUCCESS else Tone.WARNING
        )
        AssistantButton(
            if (open) "Opened" else "Open",
            onClick = onOpen, kind = ButtonKind.SECONDARY, enabled = !open,
            modifier = Modifier.testTag("open_map_${map.name}")
        )
    }
}

/** Keeps wide button/chip rows usable on narrow panels by letting them scroll sideways. */
@Composable
private fun Modifier.hScroll(): Modifier = this.horizontalScroll(rememberScrollState())
