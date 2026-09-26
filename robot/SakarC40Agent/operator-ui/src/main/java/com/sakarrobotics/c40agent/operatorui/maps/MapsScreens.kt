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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sakarrobotics.c40agent.domain.model.CleaningZone
import com.sakarrobotics.c40agent.domain.model.MapSummary
import com.sakarrobotics.c40agent.domain.model.PointXY
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
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
    var maps by remember { mutableStateOf<List<MapSummary>>(emptyList()) }
    LaunchedEffect(Unit) { maps = container.mapRepository.listMaps() }

    Scaffold(topBar = { SakarTopBar("Map Deployment", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("Map geometry is read from the robot's currently loaded map (MapComponent.getMapInfo) - creating new SLAM maps or deploying edited maps requires a confirmed map file format the vendored SDK has not validated yet.", isWarning = true)
            Spacer(Modifier.height(12.dp))
            if (maps.isEmpty()) {
                EmptyState("No map information available - connect to the robot first")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(maps, key = { it.id }) { map ->
                        SectionCard(modifier = Modifier.clickable { onOpenMap(map.id) }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(map.name, style = MaterialTheme.typography.titleMedium)
                                    if (map.inUse) Text("In use", color = SakarColors.Success)
                                }
                                CapabilityBadge(map.capability)
                            }
                        }
                    }
                }
            }
        }
    }
}

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
            InlineBanner("Tap on the canvas to place cleaning-zone corners (at least 3 points), then save. Zones are stored locally and are not yet pushed to the robot as a no-go/cleaning boundary - see Roadmap.", isWarning = true)
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
