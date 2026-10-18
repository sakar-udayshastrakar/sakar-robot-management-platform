package com.sakarrobotics.installationassistant.ui.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.installation.core.logic.ReportBuilder
import com.sakarrobotics.installation.core.model.DiagnosticsRun
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.Installation
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installation.core.model.SimulationScenario
import com.sakarrobotics.installation.core.model.StepState
import com.sakarrobotics.installation.core.model.StepStatus
import com.sakarrobotics.installation.core.model.ValidationResult
import com.sakarrobotics.installation.core.model.WizardStep
import com.sakarrobotics.installationassistant.ui.LocalServices
import com.sakarrobotics.installationassistant.ui.components.AssistantButton
import com.sakarrobotics.installationassistant.ui.components.AssistantScaffold
import com.sakarrobotics.installationassistant.ui.components.ButtonKind
import com.sakarrobotics.installationassistant.ui.components.ConfirmDialog
import com.sakarrobotics.installationassistant.ui.components.EmptyState
import com.sakarrobotics.installationassistant.ui.components.LoadingBlock
import com.sakarrobotics.installationassistant.ui.components.MessageBanner
import com.sakarrobotics.installationassistant.ui.components.SectionCard
import com.sakarrobotics.installationassistant.ui.components.StatusChip
import com.sakarrobotics.installationassistant.ui.components.Tone
import com.sakarrobotics.installationassistant.ui.diagnostics.DiagnosticRunView
import com.sakarrobotics.installationassistant.ui.theme.SakarColors

@Composable
fun WizardScreen(
    onBack: () -> Unit,
    onOpenMapping: (String) -> Unit,
    onOpenFloors: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenReport: () -> Unit,
    onFinished: () -> Unit
) {
    val services = LocalServices.current
    val vm: WizardViewModel = viewModel(factory = viewModelFactory { initializer { WizardViewModel(services) } })
    val installation by vm.installation.collectAsState()
    val floors by vm.floors.collectAsState()
    val maps by vm.maps.collectAsState()
    val statuses by vm.statuses.collectAsState()
    val validation by vm.validation.collectAsState()
    val step by vm.step.collectAsState()
    val scenario by vm.scenario.collectAsState()
    val readinessRunning by vm.readinessRunning.collectAsState()
    val sensorsRunning by vm.sensorsRunning.collectAsState()
    var confirmRestart by remember { mutableStateOf(false) }

    AssistantScaffold(title = "Installation Wizard", onBack = onBack) {
        val inst = installation
        if (inst == null) {
            EmptyState(
                Icons.Filled.PlayCircle,
                "No installation in progress",
                "Start a guided installation. Every robot-dependent result in this release is simulated and labelled as such.",
                modifier = Modifier.testTag("wizard_empty"),
                actionLabel = "Start new simulated installation",
                onAction = vm::startNew
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Installation ${inst.id}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).testTag("wizard_installation_id")
                    )
                    AssistantButton("Start over", { confirmRestart = true }, kind = ButtonKind.SECONDARY, modifier = Modifier.testTag("btn_start_over"))
                }
                Stepper(statuses, step, onSelect = vm::go)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StepHeader(step, statuses[step.index])
                    when (step) {
                        WizardStep.OVERVIEW -> OverviewBody(inst, vm::acknowledgeOverview)
                        WizardStep.READINESS -> RunBody(
                            run = inst.readiness, running = readinessRunning, runLabel = "Run simulated readiness checks",
                            tag = "btn_run_readiness", scenario = scenario, onScenario = vm::setScenario, onRun = vm::runReadiness
                        )
                        WizardStep.SENSORS -> {
                            RunBody(
                                run = inst.sensors, running = sensorsRunning, runLabel = "Run simulated sensor diagnostics",
                                tag = "btn_run_sensors", scenario = scenario, onScenario = vm::setScenario, onRun = vm::runSensors
                            )
                            AssistantButton("Open Sensor Diagnostics", onOpenDiagnostics, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Sensors)
                        }
                        WizardStep.MAPPING_SETUP -> MappingSetupBody(inst, floors, vm::toggleFloor, onOpenFloors)
                        WizardStep.MAP_CONFIG -> MapConfigBody(inst, floors, maps, onOpenMapping)
                        WizardStep.VALIDATION -> ValidationBody(inst, validation, vm::runValidation, onOpenChecklist)
                        WizardStep.REPORT -> ReportBody(inst, vm::generateReport, onOpenReport)
                    }
                }
                BottomBar(
                    step = step,
                    status = statuses[step.index],
                    allComplete = statuses.all { it.isComplete },
                    onBack = vm::back,
                    onNext = vm::next,
                    onFinish = onFinished
                )
            }
        }
    }

    if (confirmRestart) {
        ConfirmDialog(
            title = "Start a new installation?",
            text = "This replaces the current simulated installation (steps, floor selection and checklist). Saved simulated maps and reports are kept.",
            confirmLabel = "Start over", danger = true,
            onConfirm = { vm.startNew(); confirmRestart = false },
            onDismiss = { confirmRestart = false }
        )
    }
}

// ---------------------------------------------------------------------- chrome

@Composable
private fun Stepper(statuses: List<StepStatus>, selected: WizardStep, onSelect: (WizardStep) -> Unit) {
    val firstIncomplete = com.sakarrobotics.installation.core.logic.WizardLogic.firstIncomplete(statuses)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp).testTag("wizard_stepper"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        statuses.forEach { status ->
            val enterable = status.step.index <= firstIncomplete
            val isSelected = status.step == selected
            val stateText = when (status.state) {
                StepState.COMPLETE -> "complete"
                StepState.COMPLETE_WITH_WARNINGS -> "complete with warnings"
                StepState.NOT_STARTED -> if (enterable) "not complete" else "locked"
            }
            Surface(
                color = if (isSelected) SakarColors.OrangeSoft else SakarColors.Surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) SakarColors.Orange else SakarColors.Border),
                modifier = Modifier
                    .width(168.dp)
                    .testTag("step_${status.step.index}")
                    .clickable(enabled = enterable, role = Role.Button) { onSelect(status.step) }
                    .semantics { contentDescription = "Step ${status.step.index + 1} of ${statuses.size}: ${status.step.title}, $stateText" }
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepBadge(status, enterable)
                    Column {
                        Text(status.step.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2,
                            color = if (enterable) SakarColors.Ink else SakarColors.Faint)
                        Text(stateText, style = MaterialTheme.typography.bodySmall, color = SakarColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBadge(status: StepStatus, enterable: Boolean) {
    val (bg, fg) = when (status.state) {
        StepState.COMPLETE -> SakarColors.Success to Color.White
        StepState.COMPLETE_WITH_WARNINGS -> SakarColors.Orange to Color.White
        StepState.NOT_STARTED -> (if (enterable) SakarColors.OrangeSoftStrong else SakarColors.Border) to SakarColors.Ink
    }
    Box(Modifier.size(28.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        when (status.state) {
            StepState.COMPLETE -> Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            StepState.COMPLETE_WITH_WARNINGS -> Icon(Icons.Filled.Warning, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            StepState.NOT_STARTED -> Text("${status.step.index + 1}", color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StepHeader(step: WizardStep, status: StepStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Step ${step.index + 1} of ${WizardStep.values().size}", style = MaterialTheme.typography.labelLarge, color = SakarColors.Orange)
        Text(step.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("step_title"))
        Text(step.summary, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
        if (status.warnings.isNotEmpty()) {
            MessageBanner(
                "Completed with warnings: " + status.warnings.joinToString("; ") + ".",
                Tone.WARNING, Modifier.testTag("step_warnings")
            )
        }
    }
}

@Composable
private fun BottomBar(
    step: WizardStep,
    status: StepStatus,
    allComplete: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    val last = step == WizardStep.values().last()
    Surface(color = SakarColors.Surface, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!status.isComplete && status.blockedReason != null) {
                Text(
                    "To continue: ${status.blockedReason}",
                    style = MaterialTheme.typography.bodyMedium, color = SakarColors.Warning,
                    modifier = Modifier.testTag("step_hint")
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistantButton(
                    "Back", onBack, kind = ButtonKind.SECONDARY, icon = Icons.AutoMirrored.Filled.ArrowBack,
                    enabled = step.index > 0, modifier = Modifier.testTag("btn_wizard_back")
                )
                Spacer(Modifier.weight(1f))
                Text("Step ${step.index + 1} of ${WizardStep.values().size}", style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
                Spacer(Modifier.weight(1f))
                if (last) {
                    AssistantButton(
                        "Finish", onFinish, icon = Icons.Filled.Done,
                        enabled = allComplete, modifier = Modifier.testTag("btn_wizard_finish")
                    )
                } else {
                    AssistantButton(
                        "Next", onNext, icon = Icons.AutoMirrored.Filled.ArrowForward,
                        enabled = status.isComplete, modifier = Modifier.testTag("btn_wizard_next")
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- step bodies

@Composable
private fun OverviewBody(inst: Installation, onAcknowledge: () -> Unit) {
    SectionCard(title = "What this installation covers") {
        listOf(
            "Simulated readiness checks and sensor diagnostics.",
            "Choosing floors and creating simulated maps for them.",
            "Validating the installation and generating a local report."
        ).forEach { Text("- $it", style = MaterialTheme.typography.bodyMedium) }
    }
    MessageBanner(
        "Everything in this wizard is SIMULATED. No robot is connected, no sensor is read and no real map is created. " +
            "Results cannot be used as evidence of a real installation.",
        Tone.WARNING, Modifier.testTag("overview_sim_notice")
    )
    if (inst.overviewAcknowledged) {
        StatusChip("Acknowledged", Tone.SUCCESS, Modifier.testTag("overview_acknowledged"), Icons.Filled.Check)
    } else {
        AssistantButton("I understand - continue", onAcknowledge, icon = Icons.Filled.Check, modifier = Modifier.testTag("btn_acknowledge"))
    }
}

@Composable
private fun RunBody(
    run: DiagnosticsRun?,
    running: Boolean,
    runLabel: String,
    tag: String,
    scenario: SimulationScenario,
    onScenario: (SimulationScenario) -> Unit,
    onRun: () -> Unit
) {
    SectionCard(title = "Demo scenario") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SimulationScenario.values().forEach { s ->
                FilterChip(selected = s == scenario, onClick = { onScenario(s) }, label = { Text(s.label) }, modifier = Modifier.testTag("wizard_scenario_${s.name}"))
            }
        }
        Text(scenario.description, style = MaterialTheme.typography.bodySmall, color = SakarColors.Muted)
        AssistantButton(
            if (running) "Running..." else if (run == null) runLabel else "Run again",
            onRun, enabled = !running, icon = Icons.Filled.Refresh, modifier = Modifier.testTag(tag)
        )
    }
    when {
        running -> LoadingBlock("Running simulated checks...")
        run != null -> DiagnosticRunView(run)
    }
}

@Composable
private fun MappingSetupBody(inst: Installation, floors: List<Floor>, onToggle: (String) -> Unit, onOpenFloors: () -> Unit) {
    if (floors.isEmpty()) {
        EmptyState(Icons.Filled.Layers, "No floors defined", "Add at least one floor, then return here to select it.", actionLabel = "Open Floor Management", onAction = onOpenFloors)
        return
    }
    SectionCard(title = "Floors to map (simulated)") {
        floors.forEach { floor ->
            val checked = floor.id in inst.selectedFloorIds
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onToggle(floor.id) }.padding(vertical = 4.dp).testTag("floor_check_${floor.name}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Text(floor.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp).weight(1f))
                if (checked) StatusChip("Selected", Tone.SUCCESS)
            }
        }
        Text("${inst.selectedFloorIds.size} floor(s) selected. Mapping is simulated for each selected floor.", style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
        AssistantButton("Manage floors", onOpenFloors, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Layers)
    }
}

@Composable
private fun MapConfigBody(inst: Installation, floors: List<Floor>, maps: List<SimMap>, onOpenMapping: (String) -> Unit) {
    val selected = inst.selectedFloorIds.mapNotNull { id -> floors.firstOrNull { it.id == id } }
    if (selected.isEmpty()) {
        EmptyState(Icons.Filled.Layers, "No floors selected", "Go back to Mapping setup and choose at least one floor.")
        return
    }
    selected.forEach { floor ->
        val floorMaps = maps.filter { it.floorId == floor.id }
        SectionCard(modifier = Modifier.testTag("mapconfig_${floor.name}"), title = floor.name, trailing = {
            if (floorMaps.isEmpty()) StatusChip("No map", Tone.DANGER) else StatusChip("${floorMaps.size} map(s)", Tone.SUCCESS)
        }) {
            if (floorMaps.isEmpty()) Text("No saved simulated map yet.", style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
            floorMaps.forEach {
                Text("${it.name} - ${it.status.label} - ${it.coveragePercent}%", style = MaterialTheme.typography.bodyMedium)
            }
            AssistantButton("Open mapping workspace", { onOpenMapping(floor.id) }, icon = Icons.Filled.Map, modifier = Modifier.testTag("btn_open_mapping_${floor.name}"))
        }
    }
}

@Composable
private fun ValidationBody(inst: Installation, validation: ValidationResult, onRun: () -> Unit, onOpenChecklist: () -> Unit) {
    val ran = inst.validationRunAtMillis != null
    SectionCard(title = "Validation (simulated data)") {
        Text("Checks the simulated installation for blocking errors and outstanding warnings.", style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
        AssistantButton(
            if (ran) "Run validation again" else "Run validation", onRun,
            icon = Icons.Filled.Verified, modifier = Modifier.testTag("btn_run_validation")
        )
        AssistantButton("Open checklist", onOpenChecklist, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Checklist)
    }
    if (ran) {
        StatusChip(
            if (validation.passed) "Validation passed (simulated)" else "Validation failed - ${validation.errors.size} error(s)",
            if (validation.passed) Tone.SUCCESS else Tone.DANGER,
            Modifier.testTag("validation_result")
        )
        validation.errors.forEach { MessageBanner(it.message, Tone.DANGER, Modifier.testTag("validation_error")) }
        validation.warnings.forEach { MessageBanner(it.message, Tone.WARNING, Modifier.testTag("validation_warning")) }
        if (validation.errors.isEmpty() && validation.warnings.isEmpty()) {
            MessageBanner("No errors or warnings.", Tone.SUCCESS)
        }
    }
}

@Composable
private fun ReportBody(inst: Installation, onGenerate: () -> Unit, onOpenReport: () -> Unit) {
    SectionCard(title = "Completion report") {
        Text(
            "Creates a local report with the installation ID, selected floors, simulated mapping status, checklist results, " +
                "outstanding warnings and a SIMULATION ONLY notice. It contains no customer or personal data.",
            style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted
        )
        if (inst.reportId != null) {
            StatusChip("Report generated: ${inst.reportId}", Tone.SUCCESS, Modifier.testTag("wizard_report_generated"), Icons.Filled.Check)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistantButton(
                if (inst.reportId == null) "Generate report" else "Regenerate report", onGenerate,
                icon = Icons.Filled.Description, modifier = Modifier.testTag("btn_wizard_generate_report")
            )
            if (inst.reportId != null) {
                AssistantButton("View report", onOpenReport, kind = ButtonKind.SECONDARY, modifier = Modifier.testTag("btn_wizard_view_report"))
            }
        }
    }
}
