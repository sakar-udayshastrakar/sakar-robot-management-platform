package com.sakarrobotics.c40agent.operatorui.cleaning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CleaningIntensity
import com.sakarrobotics.c40agent.domain.model.CleaningMode
import com.sakarrobotics.c40agent.domain.model.CleaningRunState
import com.sakarrobotics.c40agent.domain.model.CleaningSession
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlin.random.Random
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StartCleaningViewModel(private val container: AppContainer) : ViewModel() {
    val session: StateFlow<CleaningSession?> =
        container.cleaningRepository.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun start(mode: CleaningMode, intensity: CleaningIntensity, cycles: Int, zoneIds: List<String>) {
        container.eventLogger.info("Cleaning", "Simulated clean started: zones=${zoneIds.size} mode=$mode intensity=$intensity cycles=$cycles")
        viewModelScope.launch { container.cleaningRepository.startCleaning(zoneIds = zoneIds, mode = mode, intensity = intensity, cycles = cycles) }
    }

    fun pause() = viewModelScope.launch { container.eventLogger.info("Cleaning", "Simulated clean paused"); container.cleaningRepository.pause() }
    fun resume() = viewModelScope.launch { container.eventLogger.info("Cleaning", "Simulated clean resumed"); container.cleaningRepository.resume() }
    fun stop() = viewModelScope.launch { container.eventLogger.info("Cleaning", "Simulated clean stopped"); container.cleaningRepository.stop() }
}

/**
 * A SIMULATED cleaning zone: an irregular polygon (normalized 0..1 points within the map canvas),
 * shaped to read like a real occupancy-map room rather than a tidy rectangle. This is demo
 * floor-plan data only — the vendored SDK exposes no renderable map/zone geometry, so NONE of this
 * is live (see SimulatedCleaningRepository and the on-screen DEMO/SIMULATED banner).
 */
private data class SimZone(val id: String, val name: String, val poly: List<Offset>) {
    /** Ray-casting point-in-polygon test on the normalized points (stable at any canvas scale). */
    fun contains(nx: Float, ny: Float): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i].x; val yi = poly[i].y
            val xj = poly[j].x; val yj = poly[j].y
            if (((yi > ny) != (yj > ny)) && (nx < (xj - xi) * (ny - yi) / (yj - yi) + xi)) inside = !inside
            j = i
        }
        return inside
    }
    val centerX: Float get() = poly.map { it.x }.average().toFloat()
    val centerY: Float get() = poly.map { it.y }.average().toFloat()
}

private data class SimFloor(val label: String, val zones: List<SimZone>, val robotX: Float, val robotY: Float)

private fun pt(x: Float, y: Float) = Offset(x, y)

private val SIM_FLOORS = listOf(
    SimFloor(
        "1F",
        listOf(
            SimZone("1f-lobby", "Lobby", listOf(pt(0.06f, 0.14f), pt(0.40f, 0.10f), pt(0.44f, 0.31f), pt(0.41f, 0.52f), pt(0.08f, 0.50f))),
            SimZone("1f-cafe", "Cafe", listOf(pt(0.08f, 0.57f), pt(0.42f, 0.57f), pt(0.45f, 0.74f), pt(0.33f, 0.90f), pt(0.10f, 0.88f))),
            SimZone("1f-corridor", "Corridor", listOf(pt(0.52f, 0.12f), pt(0.94f, 0.16f), pt(0.95f, 0.31f), pt(0.53f, 0.30f))),
            SimZone("1f-office", "Office", listOf(pt(0.53f, 0.37f), pt(0.95f, 0.37f), pt(0.92f, 0.86f), pt(0.56f, 0.90f), pt(0.50f, 0.61f)))
        ),
        robotX = 0.75f, robotY = 0.72f
    ),
    SimFloor(
        "2F",
        listOf(
            SimZone("2f-room-a", "Room A", listOf(pt(0.06f, 0.12f), pt(0.40f, 0.10f), pt(0.42f, 0.31f), pt(0.40f, 0.50f), pt(0.08f, 0.48f))),
            SimZone("2f-room-b", "Room B", listOf(pt(0.50f, 0.10f), pt(0.94f, 0.14f), pt(0.95f, 0.34f), pt(0.52f, 0.34f))),
            SimZone("2f-hall", "Hall", listOf(pt(0.08f, 0.54f), pt(0.94f, 0.44f), pt(0.95f, 0.64f), pt(0.10f, 0.72f))),
            SimZone("2f-store", "Store", listOf(pt(0.11f, 0.77f), pt(0.92f, 0.71f), pt(0.90f, 0.90f), pt(0.13f, 0.92f)))
        ),
        robotX = 0.74f, robotY = 0.84f
    )
)

// Occupancy-map palette (map-domain colors, not theme tokens): unknown/unscanned, scanned free
// space, lidar walls, and a faint cell grid. Interactive/brand elements stay Sakar orange.
private val MAP_UNKNOWN = Color(0xFFBCC2CB)
private val MAP_FREE = Color(0xFFF6F7F9)
private val MAP_WALL = Color(0xFF2B2F36)
private val MAP_GRID = Color(0x18000000)

private fun CleaningRunState.label(): String = when (this) {
    CleaningRunState.IDLE -> "Idle"
    CleaningRunState.CLEANING -> "Cleaning"
    CleaningRunState.PAUSED -> "Paused"
    CleaningRunState.RETURNING_TO_DOCK -> "Returning to dock"
    CleaningRunState.COMPLETED -> "Completed"
    CleaningRunState.ERROR -> "Error"
}

private fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
fun StartCleaningScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: StartCleaningViewModel = viewModel(factory = viewModelFactory { initializer { StartCleaningViewModel(container) } })
    val session by viewModel.session.collectAsState()

    StartCleaningContent(
        session = session,
        onBack = onBack,
        onStart = { zoneIds, cycles -> viewModel.start(CleaningMode.WATER_SUCTION, CleaningIntensity.STANDARD, cycles, zoneIds) },
        onPause = { viewModel.pause() },
        onResume = { viewModel.resume() },
        onStop = { viewModel.stop() }
    )
}

/**
 * Map-based Start Cleaning, matching the vendor reference: tabs (Start Cleaning Now / Task List),
 * a floor selector, a map canvas with tap-selectable cleaning zones + Select All, a cleaning-times
 * control, and a Start Clean action. SIMULATED throughout (demo map, local simulation — no command
 * reaches a robot). Stateless so it can be rendered/UI-tested without an AppContainer.
 */
@Composable
fun StartCleaningContent(
    session: CleaningSession?,
    onBack: () -> Unit,
    onStart: (zoneIds: List<String>, cycles: Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var floor by remember { mutableStateOf(SIM_FLOORS.first()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var times by remember { mutableStateOf(1) }

    val active = session != null && session.state != CleaningRunState.IDLE

    Scaffold(topBar = { SakarTopBar("Start Cleaning", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner(
                "DEMO / SIMULATED: this is a demo floor plan — no real map is loaded and no cleaning command is sent to the robot.",
                isWarning = true
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip("Start Cleaning Now", selected = tab == 0, onClick = { tab = 0 })
                TabChip("Task List", selected = tab == 1, onClick = { tab = 1 })
            }
            Spacer(Modifier.height(12.dp))

            if (tab == 1) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Scheduled cleaning tasks are managed on the Scheduled Cleaning screen (simulated).",
                        color = SakarColors.TextMuted
                    )
                }
                return@Column
            }

            // Map area: floor selector + selectable-zone map canvas.
            Row(Modifier.fillMaxWidth().weight(1f)) {
                FloorSelector(floors = SIM_FLOORS, selected = floor, onSelect = {
                    floor = it
                    selected = emptySet()
                })
                Spacer(Modifier.width(16.dp))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CleaningMap(
                        floor = floor,
                        selected = selected,
                        enabled = !active,
                        onToggleZone = { id ->
                            selected = if (id in selected) selected - id else selected + id
                        }
                    )
                    // Select All overlay (reference bottom-left of the map).
                    Surface(
                        color = SakarColors.Surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp).clickable(enabled = !active) {
                                selected = if (selected.size == floor.zones.size) emptySet() else floor.zones.map { it.id }.toSet()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.size == floor.zones.size && floor.zones.isNotEmpty(),
                                onCheckedChange = null
                            )
                            Text("Select All", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (active && session != null) {
                ActiveProgressBar(session, onPause = onPause, onResume = onResume, onStop = onStop)
            } else {
                StartBar(
                    selectedCount = selected.size,
                    times = times,
                    onTimesChange = { times = it },
                    onStart = { onStart(selected.toList(), times) }
                )
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) SakarColors.Surface else SakarColors.Background,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = Modifier.height(48.dp)
    ) {
        Box(Modifier.padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) SakarColors.TextPrimary else SakarColors.TextMuted
            )
        }
    }
}

@Composable
private fun FloorSelector(floors: List<SimFloor>, selected: SimFloor, onSelect: (SimFloor) -> Unit) {
    Column(
        Modifier.width(96.dp).fillMaxHeight().background(SakarColors.Surface, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        floors.forEach { f ->
            val isSel = f.label == selected.label
            Surface(
                color = if (isSel) SakarColors.Primary.copy(alpha = 0.14f) else SakarColors.Background,
                shape = RoundedCornerShape(8.dp),
                onClick = { onSelect(f) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        f.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) SakarColors.Primary else SakarColors.TextPrimary
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
private fun CleaningMap(
    floor: SimFloor,
    selected: Set<String>,
    enabled: Boolean,
    onToggleZone: (String) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(MAP_UNKNOWN, RoundedCornerShape(10.dp))
            .border(1.dp, SakarColors.Border, RoundedCornerShape(10.dp))
            .pointerInput(floor.label, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { off ->
                    val nx = off.x / size.width
                    val ny = off.y / size.height
                    floor.zones.firstOrNull { it.contains(nx, ny) }?.let { onToggleZone(it.id) }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        fun px(o: Offset) = Offset(o.x * w, o.y * h)
        fun zonePath(z: SimZone) = Path().apply {
            val pts = z.poly.map { px(it) }
            moveTo(pts[0].x, pts[0].y)
            for (k in 1 until pts.size) lineTo(pts[k].x, pts[k].y)
            close()
        }

        // Faint occupancy-cell grid (reads as the mapped cells, not a live map).
        val grid = w / 22f
        var gx = 0f
        while (gx <= w) { drawLine(MAP_GRID, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f); gx += grid }
        var gy = 0f
        while (gy <= h) { drawLine(MAP_GRID, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f); gy += grid }

        // Room polygons: free-space fill + outline (selected rooms use the Sakar orange accent).
        floor.zones.forEach { z ->
            val path = zonePath(z)
            val isSel = z.id in selected
            drawPath(path, if (isSel) SakarColors.Primary.copy(alpha = 0.30f) else MAP_FREE)
            drawPath(path, if (isSel) SakarColors.Primary else SakarColors.TextSubtle, style = Stroke(width = if (isSel) 5f else 2f))
        }

        // SLAM-style speckled walls along the room boundaries. Seeded per floor so the texture is
        // deterministic across recompositions and canvas sizes.
        val rnd = Random(floor.label.hashCode())
        val jitter = grid * 0.45f
        floor.zones.forEach { z ->
            val pts = z.poly.map { px(it) }
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                for (s in 0..28) {
                    if (rnd.nextFloat() > 0.72f) continue
                    val t = s / 28f
                    val x = a.x + (b.x - a.x) * t + (rnd.nextFloat() - 0.5f) * jitter
                    val y = a.y + (b.y - a.y) * t + (rnd.nextFloat() - 0.5f) * jitter
                    drawCircle(MAP_WALL, radius = 1.6f, center = Offset(x, y))
                }
            }
        }

        // Simulated coverage hatch inside the robot's current room (a "cleaned" look).
        floor.zones.firstOrNull { it.contains(floor.robotX, floor.robotY) }?.let { rz ->
            var d = -h
            while (d < w) {
                for (s in 0..40) {
                    val t = s / 40f
                    val sx = d + h * t
                    val sy = h * t
                    if (sx in 0f..w && rz.contains(sx / w, sy / h)) {
                        drawCircle(SakarColors.Primary.copy(alpha = 0.16f), radius = 1.5f, center = Offset(sx, sy))
                    }
                }
                d += grid * 0.85f
            }
        }

        // Room labels at polygon centroids.
        floor.zones.forEach { z ->
            val measured = textMeasurer.measure(z.name, TextStyle(color = SakarColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            drawText(measured, topLeft = Offset(z.centerX * w - measured.size.width / 2, z.centerY * h - measured.size.height / 2))
        }

        // Simulated robot-position pin (Sakar orange): round head + downward tip + white center.
        val rc = Offset(floor.robotX * w, floor.robotY * h)
        val r = grid * 0.55f
        val head = Offset(rc.x, rc.y - r * 0.6f)
        val tip = Path().apply {
            moveTo(head.x - r * 0.72f, head.y + r * 0.2f)
            lineTo(head.x + r * 0.72f, head.y + r * 0.2f)
            lineTo(rc.x, rc.y + r * 1.15f)
            close()
        }
        drawPath(tip, SakarColors.Primary)
        drawCircle(SakarColors.Primary, radius = r, center = head)
        drawCircle(SakarColors.Surface, radius = r * 0.4f, center = head)
    }
}

@Composable
private fun StartBar(selectedCount: Int, times: Int, onTimesChange: (Int) -> Unit, onStart: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                if (selectedCount == 0) "Please select a cleaning area first" else "$selectedCount area(s) selected",
                color = if (selectedCount == 0) SakarColors.Danger else SakarColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (times > 1) onTimesChange(times - 1) }) { Text("-") }
                Text("$times Times", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { if (times < 10) onTimesChange(times + 1) }) { Text("+") }
            }
        }
        Button(
            onClick = onStart,
            enabled = selectedCount > 0,
            modifier = Modifier.weight(1f).height(64.dp)
        ) {
            Text("Start Clean", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveProgressBar(session: CleaningSession, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${session.state.label()} · ${session.mode.name.replace('_', ' ')} · ${session.intensity.name}",
            style = MaterialTheme.typography.titleMedium
        )
        Text("${session.progressPercent}% — cycle ${session.cyclesCompleted + 1} of ${session.cyclesRequested}")
        Text(
            "Elapsed ${formatDuration(session.elapsedSeconds)} · Remaining ~${formatDuration(session.estimatedRemainingSeconds)} (simulated estimate)",
            style = MaterialTheme.typography.bodyMedium,
            color = SakarColors.TextFaint
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { session.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        CapabilityBadge(session.capability)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (session.state) {
                CleaningRunState.CLEANING -> Button(onClick = onPause) { Text("Pause") }
                CleaningRunState.PAUSED -> Button(onClick = onResume) { Text("Resume") }
                else -> {}
            }
            OutlinedButton(onClick = onStop) { Text("Stop") }
        }
    }
}
