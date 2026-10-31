package com.sakarrobotics.c40agent.operatorui.teachroute

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.RouteRecord
import com.sakarrobotics.c40agent.domain.model.RouteRecordingState
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeachRouteViewModel(private val container: AppContainer) : ViewModel() {
    val routes: StateFlow<List<RouteRecord>> =
        container.routeRepository.routes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recordingState: StateFlow<RouteRecordingState> =
        container.routeRepository.recordingState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RouteRecordingState.IDLE)

    fun startRecording(name: String) = viewModelScope.launch { container.routeRepository.startRecording(name) }
    fun pause() = viewModelScope.launch { container.routeRepository.pauseRecording() }
    fun resume() = viewModelScope.launch { container.routeRepository.resumeRecording() }
    fun stopAndSave() = viewModelScope.launch { container.routeRepository.stopAndSaveRecording() }
    fun delete(id: String) = viewModelScope.launch { container.routeRepository.delete(id) }
    fun rename(id: String, name: String) = viewModelScope.launch { container.routeRepository.rename(id, name) }
}

/** Reference-matched Teach Route flow: route list (empty state) -> instructions -> recording. */
private enum class TeachStep { LIST, INSTRUCTIONS, RECORDING }

@Composable
fun TeachRouteScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: TeachRouteViewModel = viewModel(factory = viewModelFactory { initializer { TeachRouteViewModel(container) } })
    val routes by viewModel.routes.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    var newRouteName by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(TeachStep.LIST) }

    Scaffold(
        topBar = {
            SakarTopBar(
                title = "Teach Route",
                onBack = { if (step == TeachStep.LIST) onBack() else step = TeachStep.LIST },
                trailing = {
                    if (step == TeachStep.LIST) {
                        Button(onClick = { step = TeachStep.INSTRUCTIONS }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Create Route")
                        }
                    }
                }
            )
        },
        containerColor = SakarColors.Background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (step) {
                TeachStep.LIST -> RouteList(routes = routes, onDelete = viewModel::delete)
                TeachStep.INSTRUCTIONS -> Instructions(onNext = { step = TeachStep.RECORDING })
                TeachStep.RECORDING -> Recording(
                    recordingState = recordingState,
                    name = newRouteName,
                    onNameChange = { newRouteName = it },
                    onStart = { viewModel.startRecording(newRouteName.ifBlank { "New Route" }) },
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStopAndSave = { viewModel.stopAndSave(); newRouteName = ""; step = TeachStep.LIST }
                )
            }
        }
    }
}

@Composable
private fun RouteList(routes: List<RouteRecord>, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(ScreenPadding)) {
        if (routes.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = SakarColors.Surface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(96.dp).background(SakarColors.PrimarySoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Route, contentDescription = null, tint = SakarColors.Primary, modifier = Modifier.size(48.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("No routes yet~", color = SakarColors.TextSubtle, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(routes, key = { it.id }) { route ->
                    SectionCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(route.name, style = MaterialTheme.typography.titleMedium)
                                Text("${route.durationSeconds}s · ${route.pointCount} points", color = SakarColors.TextFaint)
                                Spacer(Modifier.height(4.dp))
                                CapabilityBadge(route.capability)
                            }
                            TextButton(onClick = { onDelete(route.id) }) { Text("Delete", color = SakarColors.Danger) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Instructions(onNext: () -> Unit) {
    Row(
        Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left: a simple Sakar-drawn "push along the route" illustration (original, not vendor art).
        Surface(
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
            color = SakarColors.PrimarySoft,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RoutePathIllustration(Modifier.fillMaxWidth().height(180.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    "Push the robot along the route",
                    style = MaterialTheme.typography.titleMedium,
                    color = SakarColors.TextPrimary
                )
            }
        }
        // Right: operation steps + notes + Next.
        Surface(
            modifier = Modifier.weight(0.58f).fillMaxHeight(),
            color = SakarColors.Surface,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Text("Operation Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SakarColors.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text("1. Push the robot along the desired cleaning path", style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
                Text("2. Finish and save the route", style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
                Spacer(Modifier.height(20.dp))
                Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SakarColors.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text("1. Do not record cleaning routes across floors", style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
                Text("2. Keep cleaning routes away from elevators or staircases", style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                InlineBanner(
                    "SIMULATED: route geometry is not captured on this hardware — only the route name and duration are saved.",
                    isWarning = true
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Dashed winding path with three robot waypoints — scales with the canvas. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoutePath() {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.15f, h * 0.18f)
        cubicTo(w * 0.95f, h * 0.22f, w * 0.05f, h * 0.60f, w * 0.85f, h * 0.84f)
    }
    drawPath(
        path,
        SakarColors.Primary.copy(alpha = 0.55f),
        style = Stroke(width = 6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
    )
    listOf(
        Offset(w * 0.15f, h * 0.18f),
        Offset(w * 0.52f, h * 0.48f),
        Offset(w * 0.85f, h * 0.84f)
    ).forEach { c ->
        drawCircle(SakarColors.Primary, radius = 15f, center = c)
        drawCircle(SakarColors.Surface, radius = 6f, center = c)
    }
}

@Composable
private fun RoutePathIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { drawRoutePath() }
}

@Composable
private fun Recording(
    recordingState: RouteRecordingState,
    name: String,
    onNameChange: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStopAndSave: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(ScreenPadding)) {
        InlineBanner(
            "Push the robot along the desired path. Route geometry capture requires a robot position API not yet confirmed on this hardware — only duration and a route name are saved.",
            isWarning = true
        )
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Record a New Route") {
            if (recordingState == RouteRecordingState.IDLE) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Route name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Recording")
                }
            } else {
                Text(if (recordingState == RouteRecordingState.RECORDING) "Recording…" else "Paused", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (recordingState == RouteRecordingState.RECORDING) {
                        OutlinedButton(onClick = onPause) { Text("Pause") }
                    } else {
                        OutlinedButton(onClick = onResume) { Text("Resume") }
                    }
                    Button(onClick = onStopAndSave) { Text("Stop & Save") }
                }
            }
        }
    }
}
