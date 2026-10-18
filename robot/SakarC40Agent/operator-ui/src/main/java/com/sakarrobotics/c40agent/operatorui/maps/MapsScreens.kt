package com.sakarrobotics.c40agent.operatorui.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CleaningZone
import com.sakarrobotics.c40agent.domain.model.MapListResult
import com.sakarrobotics.c40agent.domain.model.PointXY
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.domain.model.displayName
import com.sakarrobotics.c40agent.domain.model.formatAge
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.CapabilityRow
import com.sakarrobotics.c40agent.operatorui.common.EmptyState
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun MapsListScreen(onBack: () -> Unit, onOpenMap: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val linkState by container.connectionRepository.linkState.collectAsState(initial = RobotLinkState.DISCONNECTED)
    var result by remember { mutableStateOf<MapListResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var lastReadMillis by remember { mutableStateOf<Long?>(null) }

    // Explicit, read-only request: only runs when this screen opens or the operator taps Refresh.
    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            result = container.mapRepository.listMapsDetailed()
            lastReadMillis = System.currentTimeMillis()
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    MapsListContent(
        linkState = linkState,
        result = result,
        loading = loading,
        lastReadMillis = lastReadMillis,
        onBack = onBack,
        onRefresh = { refresh() },
        onOpenMap = onOpenMap
    )
}

/** Stateless Maps list, so it can be rendered (and UI-tested) without an AppContainer. */
@Composable
fun MapsListContent(
    linkState: RobotLinkState,
    result: MapListResult?,
    loading: Boolean,
    lastReadMillis: Long?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMap: (String) -> Unit
) {
    Scaffold(topBar = { SakarTopBar("Maps", onBack) }, containerColor = SakarColors.Background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(ScreenPadding).testTag(TAG_MAPS_LIST),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                InlineBanner(
                    "Read-only view of what the robot reports. Creating SLAM maps, previewing the floor plan and deploying edited maps are not available - no confirmed map format or ROS mapping path exists yet.",
                    isWarning = true
                )
            }
            item {
                SectionCard("Robot link") {
                    // A link STATE, not a data reading: no LIVE badge (see ConnectionStateChip).
                    CapabilityRow("SDK link", linkState.displayName(), capability = null)
                    val read = lastReadMillis
                    Text(
                        if (read == null) "Map info not read yet" else "Map info last read ${formatAge(read, System.currentTimeMillis())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SakarColors.TextFaint
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRefresh, enabled = !loading) {
                        Text(if (loading) "Reading..." else "Refresh map info")
                    }
                }
            }
            item {
                SectionCard("Capabilities") {
                    CapabilityRow("Map information (raw response)", "Read from the robot's loaded map", Capability.UNKNOWN)
                    CapabilityRow("Floor-plan image preview", "No renderable image format confirmed", Capability.UNAVAILABLE)
                    CapabilityRow("Create / edit SLAM maps", "Needs ROS mapping path", Capability.UNAVAILABLE)
                    CapabilityRow("Deploy map to robot", "Needs confirmed map file format", Capability.UNAVAILABLE)
                }
            }
            val current = result
            when {
                current == null -> item { EmptyState("Reading map information...") }
                current.maps.isEmpty() -> item {
                    EmptyState(current.error ?: "No map information available")
                }
                else -> items(current.maps, key = { it.id }) { map ->
                    SectionCard(modifier = Modifier.clickable { onOpenMap(map.id) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(map.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "In use: ${map.inUse?.let { if (it) "yes" else "no" } ?: "unknown"}  |  Size: ${map.sizeBytes?.let { "$it bytes" } ?: "unknown"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SakarColors.TextFaint
                                )
                            }
                            CapabilityBadge(map.capability)
                        }
                        map.rawInfo?.let { raw ->
                            Spacer(Modifier.height(8.dp))
                            Text("Raw response (not decoded):", style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
                            Text(
                                raw.take(MAX_RAW_MAP_CHARS) + if (raw.length > MAX_RAW_MAP_CHARS) " ..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = SakarColors.TextPrimary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to open the local cleaning-zone sketch for this map", style = MaterialTheme.typography.bodySmall, color = SakarColors.TextSubtle)
                    }
                }
            }
        }
    }
}

/** Test tag (used by the instrumentation tests in src/androidTest). */
const val TAG_MAPS_LIST = "maps_list"

private const val MAX_RAW_MAP_CHARS = 1500

@Composable
fun MapDetailScreen(mapId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var zones by remember { mutableStateOf<List<CleaningZone>>(emptyList()) }
    var currentPolygon by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var zoneName by remember { mutableStateOf("") }

    LaunchedEffect(mapId) { zones = container.cleaningRepository.listZones(mapId) }

    Scaffold(topBar = { SakarTopBar("Map: $mapId", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("This blank canvas is a local sketch pad, NOT the robot's map (the floor-plan image is unavailable). Tap to place cleaning-zone corners (at least 3 points), then save. Zones are stored on this tablet only and are never sent to the robot - see Roadmap.", isWarning = true)
            Spacer(Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .background(SakarColors.Border.copy(alpha = 0.3f))
                    .pointerInput(Unit) {
                        detectTapGestures { offset -> currentPolygon = currentPolygon + offset }
                    }
            ) {
                zones.forEach { zone -> drawPolygonZone(zone, size, SakarColors.Info) }
                if (currentPolygon.size >= 2) {
                    for (i in 0 until currentPolygon.size - 1) {
                        drawLine(SakarColors.PrimaryStrong, currentPolygon[i], currentPolygon[i + 1], strokeWidth = 4f)
                    }
                }
                currentPolygon.forEach { point -> drawCircle(SakarColors.PrimaryStrong, radius = 8f, center = point) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = zoneName, onValueChange = { zoneName = it }, label = { Text("Zone name") }, modifier = Modifier.weight(1f))
                Button(
                    enabled = currentPolygon.size >= 3 && zoneName.isNotBlank(),
                    onClick = {
                        val zone = CleaningZone(
                            id = UUID.randomUUID().toString(),
                            mapId = mapId,
                            name = zoneName,
                            polygon = currentPolygon.map { PointXY(it.x, it.y) },
                            floor = 1
                        )
                        scope.launch {
                            container.cleaningRepository.saveZone(zone)
                            zones = container.cleaningRepository.listZones(mapId)
                            currentPolygon = emptyList()
                            zoneName = ""
                        }
                    }
                ) { Text("Save Zone") }
                OutlinedButton(onClick = { currentPolygon = emptyList() }) { Text("Clear") }
            }
            Spacer(Modifier.height(16.dp))
            Text("Saved Zones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(zones, key = { it.id }) { zone ->
                    SectionCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${zone.name} (${zone.polygon.size} points)")
                            TextButton(onClick = {
                                scope.launch {
                                    container.cleaningRepository.deleteZone(zone.id)
                                    zones = container.cleaningRepository.listZones(mapId)
                                }
                            }) { Text("Delete", color = SakarColors.Danger) }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygonZone(zone: CleaningZone, canvasSize: androidx.compose.ui.geometry.Size, color: Color) {
    if (zone.polygon.size < 2) return
    val points = zone.polygon.map { Offset(it.x, it.y) }
    for (i in points.indices) {
        val next = points[(i + 1) % points.size]
        drawLine(color, points[i], next, strokeWidth = 3f)
    }
}
