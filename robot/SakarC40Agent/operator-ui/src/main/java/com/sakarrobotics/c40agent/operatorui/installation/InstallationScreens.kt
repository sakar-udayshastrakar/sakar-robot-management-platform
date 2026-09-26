package com.sakarrobotics.c40agent.operatorui.installation

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.model.RobotIdentity
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.nav.Routes
import com.sakarrobotics.c40agent.operatorui.settings.PreferencesViewModel
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlinx.coroutines.launch

private data class InstallationEntry(val title: String, val subtitle: String, val route: String)

@Composable
fun InstallationHomeScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val container = LocalAppContainer.current
    var identity by remember { mutableStateOf<RobotIdentity?>(null) }
    LaunchedEffect(Unit) { identity = container.identityRepository.identity() }

    val entries = listOf(
        InstallationEntry("Basic Settings", "Robot identity and network", "basic"),
        InstallationEntry("Business Settings → Elevator", "Elevator robot ID / configuration", Routes.INSTALLATION_ELEVATOR),
        InstallationEntry("Business Settings → Scheduling Path", "Waypoint graph for robot scheduling", Routes.INSTALLATION_SCHEDULING_PATH),
        InstallationEntry("Business Settings → Remote Control", "Remote-control robot via phone", Routes.INSTALLATION_REMOTE_CONTROL),
        InstallationEntry("Advanced Settings", "Function switches, zones, factory reset", Routes.INSTALLATION_ADVANCED)
    )

    Scaffold(topBar = { SakarTopBar("Robot Installation", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            identity?.let { info ->
                SectionCard(title = "Basic Settings") {
                    Text("Model: ${info.model}")
                    Text("Robot IP: ${info.robotIp ?: "Unknown"}")
                    Text("SDK Version: ${info.sdkVersion}")
                }
                Spacer(Modifier.height(16.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries.filter { it.route != "basic" }) { entry ->
                    SectionCard(modifier = Modifier.clickable { onOpen(entry.route) }) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
                    }
                }
            }
        }
    }
}

@Composable
fun InstallationElevatorScreen(onBack: () -> Unit) {
    Scaffold(topBar = { SakarTopBar("Elevator Settings", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("Elevator integration (robot-ID binding, cloud ladder control, per-store elevator config) requires an elevator vendor gateway that is not connected to this build.", isWarning = true)
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Elevator RobotId Configuration") { CapabilityBadge(com.sakarrobotics.c40agent.domain.model.Capability.UNAVAILABLE) }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Elevator List Configuration") { CapabilityBadge(com.sakarrobotics.c40agent.domain.model.Capability.UNAVAILABLE) }
        }
    }
}

@Composable
fun InstallationSchedulingPathScreen(onBack: () -> Unit) {
    Scaffold(topBar = { SakarTopBar("Scheduling Path", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("Waypoint-graph scheduling paths (used for multi-robot traffic coordination) require a fleet scheduling backend not present on this build.", isWarning = true)
            Spacer(Modifier.height(12.dp))
            SectionCard { CapabilityBadge(com.sakarrobotics.c40agent.domain.model.Capability.UNAVAILABLE) }
        }
    }
}

@Composable
fun InstallationRemoteControlScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    var identity by remember { mutableStateOf<RobotIdentity?>(null) }
    LaunchedEffect(Unit) { identity = container.identityRepository.identity() }

    Scaffold(topBar = { SakarTopBar("Remote Control Robot", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("Phone-based remote control requires a Sakar Cloud remote-session endpoint that does not exist yet - no QR code is generated to avoid implying a working link.", isWarning = true)
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Robot Network Address") {
                Text("Robot IP: ${identity?.robotIp ?: "Unknown"}", color = SakarColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                CapabilityBadge(com.sakarrobotics.c40agent.domain.model.Capability.UNAVAILABLE)
            }
        }
    }
}

@Composable
fun InstallationAdvancedScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: PreferencesViewModel = viewModel(factory = viewModelFactory { initializer { PreferencesViewModel(container) } })
    val prefs by viewModel.preferences.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    val resetScope = rememberCoroutineScope()

    Scaffold(topBar = { SakarTopBar("Advanced Settings", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            SectionCard(title = "Function Switches") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Resume cleaning from breakpoint")
                    Switch(checked = prefs.resumeFromBreakpoint, onCheckedChange = { viewModel.update { p -> p.copy(resumeFromBreakpoint = it) } })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sweeping brush auto-height")
                    Switch(checked = prefs.sweepingBrushAutoHeight, onCheckedChange = { viewModel.update { p -> p.copy(sweepingBrushAutoHeight = it) } })
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Zone Configuration / Gate Settings") {
                Text("Managed on the Maps section (cleaning zones drawn per map).", color = SakarColors.TextFaint)
                CapabilityBadge(com.sakarrobotics.c40agent.domain.model.Capability.LOCAL)
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Factory Reset") {
                Text("Clears all locally saved schedules, consumables, routes, cleaning zones, preferences, the Super User PIN, and the SDK call log on this device. This cannot be undone.", color = SakarColors.TextFaint)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SakarColors.Danger)
                ) { Text("Restore Factory Settings", color = Color.White) }
                if (resetDone) {
                    Spacer(Modifier.height(8.dp))
                    Text("Local data cleared.", color = SakarColors.Success)
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Restore Factory Settings?") },
            text = { Text("This clears all locally saved data on this device (schedules, consumables, routes, zones, preferences, the Super User PIN, and logs). This does not affect the robot's own SDK-managed state. You will need to set a new Super User PIN afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    resetScope.launch { container.systemMaintenanceRepository.factoryReset() }
                    showResetConfirm = false
                    resetDone = true
                }) { Text("Confirm", color = SakarColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }
}
