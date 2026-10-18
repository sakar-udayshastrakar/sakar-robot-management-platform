package com.sakarrobotics.installationassistant.ui.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.logic.ReportBuilder
import com.sakarrobotics.installation.core.model.DiagnosticItem
import com.sakarrobotics.installation.core.model.DiagnosticState
import com.sakarrobotics.installation.core.model.DiagnosticsRun
import com.sakarrobotics.installation.core.model.SimulationScenario
import com.sakarrobotics.installationassistant.ui.LocalServices
import com.sakarrobotics.installationassistant.ui.components.AssistantButton
import com.sakarrobotics.installationassistant.ui.components.AssistantScaffold
import com.sakarrobotics.installationassistant.ui.components.EmptyState
import com.sakarrobotics.installationassistant.ui.components.LoadingBlock
import com.sakarrobotics.installationassistant.ui.components.MessageBanner
import com.sakarrobotics.installationassistant.ui.components.SectionCard
import com.sakarrobotics.installationassistant.ui.components.SimulatedTag
import com.sakarrobotics.installationassistant.ui.components.StatusChip
import com.sakarrobotics.installationassistant.ui.components.Tone
import com.sakarrobotics.installationassistant.ui.theme.SakarColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(private val services: AssistantServices) : ViewModel() {
    val scenario: StateFlow<SimulationScenario> = services.diagnostics.scenario
    val latest: StateFlow<DiagnosticsRun?> = services.diagnostics.latestSensors

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setScenario(s: SimulationScenario) = services.diagnostics.setScenario(s)

    fun run() {
        if (_running.value) return
        _running.value = true
        viewModelScope.launch {
            try {
                // Standalone run: updates the latest-results view only. The installation wizard
                // records its own run explicitly, so there is no hidden side effect here.
                services.diagnostics.runSensors()
            } finally {
                _running.value = false
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val services = LocalServices.current
    val vm: DiagnosticsViewModel = viewModel(factory = viewModelFactory { initializer { DiagnosticsViewModel(services) } })
    val scenario by vm.scenario.collectAsState()
    val latest by vm.latest.collectAsState()
    val running by vm.running.collectAsState()

    AssistantScaffold(title = "Sensor Diagnostics", onBack = onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MessageBanner(
                "All rows below are SIMULATED. No physical sensor or service has been checked.",
                Tone.WARNING, Modifier.testTag("diagnostics_sim_notice")
            )
            SectionCard(title = "Demo scenario") {
                Text(scenario.description, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SimulationScenario.values().forEach { s ->
                        FilterChip(
                            selected = s == scenario,
                            onClick = { vm.setScenario(s) },
                            label = { Text(s.label) },
                            modifier = Modifier.testTag("scenario_${s.name}")
                        )
                    }
                }
                AssistantButton(
                    if (running) "Running simulated diagnostics..." else "Run simulated diagnostics",
                    onClick = vm::run, enabled = !running, icon = Icons.Filled.Refresh,
                    modifier = Modifier.testTag("btn_run_diagnostics")
                )
            }
            when {
                running -> LoadingBlock("Running simulated diagnostics...")
                latest == null -> EmptyState(
                    Icons.Filled.Sensors, "No diagnostics run yet",
                    "Run the simulated diagnostics to see example results and troubleshooting guidance."
                )
                else -> DiagnosticRunView(latest!!)
            }
        }
    }
}

@Composable
fun DiagnosticRunView(run: DiagnosticsRun, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusChip(
                "Overall: ${run.worst.label}",
                stateTone(run.worst), Modifier.testTag("diagnostics_overall")
            )
            Text(
                "Simulated run at ${ReportBuilder.formatTimestamp(run.runAtMillis)} (${run.scenario.label})",
                style = MaterialTheme.typography.bodySmall, color = SakarColors.Muted
            )
        }
        run.items.forEach { DiagnosticRow(it) }
    }
}

fun stateTone(state: DiagnosticState): Tone = when (state) {
    DiagnosticState.OK -> Tone.SUCCESS
    DiagnosticState.WARNING -> Tone.WARNING
    DiagnosticState.FAILED -> Tone.DANGER
    DiagnosticState.NOT_RUN -> Tone.NEUTRAL
}

@Composable
fun DiagnosticRow(item: DiagnosticItem, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(item.state != DiagnosticState.OK) }
    SectionCard(modifier = modifier.testTag("diag_row_${item.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusChip(item.state.label, stateTone(item.state))
            SimulatedTag()
        }
        Text(item.message, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Ink)
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (expanded) "Hide troubleshooting guidance" else "Show troubleshooting guidance",
                style = MaterialTheme.typography.labelLarge, color = SakarColors.Orange, modifier = Modifier.weight(1f)
            )
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = SakarColors.Orange)
        }
        if (expanded) {
            item.troubleshooting.forEach {
                Text("- $it", style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
            }
        }
    }
}
