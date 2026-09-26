package com.sakarrobotics.c40agent.operatorui.teachroute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.sakarrobotics.c40agent.operatorui.common.EmptyState
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

@Composable
fun TeachRouteScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: TeachRouteViewModel = viewModel(factory = viewModelFactory { initializer { TeachRouteViewModel(container) } })
    val routes by viewModel.routes.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    var newRouteName by remember { mutableStateOf("") }

    Scaffold(topBar = { SakarTopBar("Teach Route", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner(
                "Push the robot along the desired path. Route geometry capture requires a robot position API not yet confirmed on this hardware - only duration and a route name are saved.",
                isWarning = true
            )
            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Record a New Route") {
                if (recordingState == RouteRecordingState.IDLE) {
                    OutlinedTextField(
                        value = newRouteName,
                        onValueChange = { newRouteName = it },
                        label = { Text("Route name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.startRecording(newRouteName.ifBlank { "New Route" }) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start Recording")
                    }
                } else {
                    Text(if (recordingState == RouteRecordingState.RECORDING) "Recording…" else "Paused", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (recordingState == RouteRecordingState.RECORDING) {
                            OutlinedButton(onClick = viewModel::pause) { Text("Pause") }
                        } else {
                            OutlinedButton(onClick = viewModel::resume) { Text("Resume") }
                        }
                        Button(onClick = { viewModel.stopAndSave(); newRouteName = "" }) { Text("Stop & Save") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("Saved Routes", style = MaterialTheme.typography.titleMedium, color = SakarColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            if (routes.isEmpty()) {
                EmptyState("No routes yet")
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
                                TextButton(onClick = { viewModel.delete(route.id) }) { Text("Delete", color = SakarColors.Danger) }
                            }
                        }
                    }
                }
            }
        }
    }
}
