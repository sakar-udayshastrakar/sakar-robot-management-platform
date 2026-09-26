package com.sakarrobotics.c40agent.operatorui.superuser

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.sakarrobotics.c40agent.domain.model.ActuatorTest
import com.sakarrobotics.c40agent.domain.model.ActuatorTestResult
import com.sakarrobotics.c40agent.domain.model.DiagnosticsSnapshot
import com.sakarrobotics.c40agent.domain.usecase.DiagnosticsUseCases
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.EmptyState
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlinx.coroutines.launch

class DiagnosticsViewModel(container: AppContainer) : ViewModel() {
    private val useCases = DiagnosticsUseCases(container.diagnosticsRepository, container.sensorsRepository)

    var snapshot by androidx.compose.runtime.mutableStateOf<DiagnosticsSnapshot?>(null)
        private set

    var sensors by androidx.compose.runtime.mutableStateOf<com.sakarrobotics.c40agent.domain.model.SensorReadings?>(null)
        private set

    fun refresh() = viewModelScope.launch { snapshot = useCases.snapshot() }

    fun refreshSensors() = viewModelScope.launch { sensors = useCases.sensors() }

    fun groups() = useCases.groups()
    fun tests(groupId: String) = useCases.tests(groupId)

    fun runTest(testId: String, onDone: (ActuatorTestResult) -> Unit) {
        viewModelScope.launch { onDone(useCases.runTest(testId)) }
    }
}

@Composable
fun RobotDebuggingGroupsScreen(onBack: () -> Unit, onOpenGroup: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: DiagnosticsViewModel = viewModel(factory = viewModelFactory { initializer { DiagnosticsViewModel(container) } })

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(topBar = { SakarTopBar("Robot Debugging", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            viewModel.snapshot?.let { snap -> DiagnosticsSummaryCard(snap) }
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(viewModel.groups()) { group ->
                    SectionCard(modifier = Modifier.clickable { onOpenGroup(group.id) }) {
                        Text(group.label, style = MaterialTheme.typography.titleMedium)
                        Text(group.description, style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSummaryCard(snapshot: DiagnosticsSnapshot) {
    SectionCard(title = "Live Diagnostics Snapshot") {
        CapabilityBadge(com.sakarrobotics.c40agent.domain.model.Capability.REAL)
        Spacer(Modifier.height(8.dp))
        Text("Work mode: ${snapshot.workMode} · Power: ${snapshot.power} · Odometer: ${snapshot.totalOdometer ?: "-"}")
        Text("Emergency stop engaged: ${snapshot.emergencyStopEngaged} · Emergency enabled: ${snapshot.emergencyEnabled}")
        Text("Motor status code: ${snapshot.motorStatusCode}")
    }
}

@Composable
fun RobotDebuggingGroupDetailScreen(groupId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: DiagnosticsViewModel = viewModel(factory = viewModelFactory { initializer { DiagnosticsViewModel(container) } })
    val tests = viewModel.tests(groupId)

    var pendingTest by remember { mutableStateOf<ActuatorTest?>(null) }
    var lastResult by remember { mutableStateOf<ActuatorTestResult?>(null) }

    Scaffold(topBar = { SakarTopBar(groupId.replace('_', ' '), onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            if (tests.isEmpty()) {
                EmptyState("No tests defined for this group")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tests, key = { it.id }) { test ->
                    SectionCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(test.label, style = MaterialTheme.typography.titleMedium)
                                CapabilityBadge(test.capability)
                            }
                            Button(onClick = {
                                if (test.requiresConfirmation) pendingTest = test
                                else viewModel.runTest(test.id) { lastResult = it }
                            }) { Text("Run") }
                        }
                    }
                }
            }
            lastResult?.let { result ->
                Spacer(Modifier.height(12.dp))
                SectionCard(title = if (result.success) "Result: OK" else "Result: Failed") {
                    Text(result.message, color = if (result.success) SakarColors.TextMuted else SakarColors.Danger)
                }
            }
        }
    }

    val test = pendingTest
    if (test != null) {
        AlertDialog(
            onDismissRequest = { pendingTest = null },
            title = { Text("Confirm: ${test.label}") },
            text = { Text("This may cause the robot to actuate hardware. Confirm you want to proceed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.runTest(test.id) { lastResult = it }
                    pendingTest = null
                }) { Text("Proceed", color = SakarColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { pendingTest = null }) { Text("Cancel") } }
        )
    }
}
