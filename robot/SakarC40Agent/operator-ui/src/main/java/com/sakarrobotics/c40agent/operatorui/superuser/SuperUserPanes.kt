package com.sakarrobotics.c40agent.operatorui.superuser

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.model.ActuatorTest
import com.sakarrobotics.c40agent.domain.model.ActuatorTestResult
import com.sakarrobotics.c40agent.domain.model.BusinessProfile
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CloudSyncState
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.CheckmarkIcon
import com.sakarrobotics.c40agent.operatorui.common.EmptyState
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.settings.PreferencesViewModel
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import com.sakarrobotics.c40agent.ui.MainActivity as LegacyDiagnosticsActivity

/**
 * Reference-matched content panes for the Super User sidebar (SuperUserHomeScreen). Each pane is
 * plain content with no Scaffold/top bar of its own - the sidebar is the only persistent chrome,
 * exactly like the vendor reference screenshots. Real data (Wi-Fi, business profile, cloud sync
 * state, actuator tests) is reused from the same repositories/use cases every other screen in
 * this app already uses; rows with no corresponding Peanut SDK or Sakar-local capability are
 * rendered disabled and tagged UNAVAILABLE rather than faked, per this app's existing pattern.
 */

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = SakarColors.TextSubtle)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
    Divider(color = SakarColors.Border)
}

@Composable
private fun ValueRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextFaint)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SakarColors.TextSubtle)
            }
        }
    }
    Divider(color = SakarColors.Border)
}

/**
 * Real Wi-Fi state via the tablet's own WifiManager (same source as the standalone Settings ->
 * Wi-Fi/Network screen, restyled here to match the reference's list-with-lock-icons layout). The
 * power toggle reflects real state but stays disabled: Android 10+ does not let apps flip Wi-Fi
 * power state anymore, so a "working" switch here would be fake.
 */
@Composable
fun SuperUserNetworkPane() {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
    }
    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val isWifiEnabled = remember { wifiManager.isWifiEnabled }
    val connectionInfo = remember(hasLocationPermission) { if (hasLocationPermission) wifiManager.connectionInfo else null }
    val connectedSsid = connectionInfo?.ssid?.trim('"')

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Wi-Fi", style = MaterialTheme.typography.headlineSmall, color = SakarColors.TextPrimary)
            Switch(checked = isWifiEnabled, onCheckedChange = {}, enabled = false)
        }
        Spacer(Modifier.height(20.dp))
        Divider(color = SakarColors.Border)
        if (!hasLocationPermission) {
            Spacer(Modifier.height(16.dp))
            InlineBanner("Location permission is required by Android to read the connected network name and scan for nearby networks.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Grant Permission") }
        } else {
            val scanResults = remember { runCatching { wifiManager.scanResults }.getOrDefault(emptyList()) }
            val networks = scanResults.distinctBy { it.SSID }.filter { it.SSID.isNotBlank() }
            if (networks.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                EmptyState("No nearby networks found")
            } else {
                networks.forEach { result ->
                    val isSecured = result.capabilities?.let { it.contains("WPA") || it.contains("WEP") } ?: false
                    NetworkRow(ssid = result.SSID, isSecured = isSecured, isConnected = result.SSID == connectedSsid)
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(ssid: String, isSecured: Boolean, isConnected: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = SakarColors.Info)
            Spacer(Modifier.width(12.dp))
            Text(ssid, style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
        }
        if (isConnected) {
            Text("Connection successful", style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
        } else if (isSecured) {
            Icon(Icons.Filled.Lock, contentDescription = "Secured", tint = SakarColors.TextSubtle, modifier = Modifier.height(18.dp))
        }
    }
    Divider(color = SakarColors.Border)
}

/**
 * Reference-matched "Online Scenes" / "Imported" layout. Sakar has no cloud-hosted scene catalog
 * yet (no Sakar Cloud - see RobotConnectionRepository.cloudSyncState, hardcoded OFFLINE), so
 * "Online Scenes" is honestly empty rather than showing fabricated downloadable packages; the
 * real, working part is "Imported" - the app's existing local BusinessProfile selector.
 */
@Composable
fun SuperUserResourceManagementPane() {
    val container = LocalAppContainer.current
    val viewModel: PreferencesViewModel = viewModel(factory = viewModelFactory { initializer { PreferencesViewModel(container) } })
    val prefs by viewModel.preferences.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Text(
            "This selects a Sakar-local operating profile only - not a downloadable resource package.",
            style = MaterialTheme.typography.bodyMedium,
            color = SakarColors.Danger
        )
        Spacer(Modifier.height(20.dp))
        SectionLabel("Online Scenes")
        EmptyState("No Sakar Cloud yet - nothing to browse or download")
        Spacer(Modifier.height(24.dp))
        SectionLabel("Imported")
        BusinessProfile.values().forEach { profile ->
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.update { it.copy(businessProfile = profile) } }.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(profile.name.replace('_', ' '), style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
                if (prefs.businessProfile == profile) CheckmarkIcon()
            }
            Divider(color = SakarColors.Border)
        }
    }
}

/**
 * Reference-matched row set. Only "Fault Do-Not-Disturb Mode" (taskDndEnabled) and "Language" are
 * backed by real Sakar-local state (DataStore, via PreferencesViewModel - see also the standalone
 * General Settings screen reachable from Settings). Everything else in the vendor reference
 * (Standard User Password, Show Map Creation Shortcut, Rotate Screen 180 deg, elevator/ROS rows)
 * has no corresponding API anywhere in this project, so those rows are disabled and shown with
 * their reference labels rather than invented values.
 */
@Composable
fun SuperUserGeneralSettingsPane() {
    val container = LocalAppContainer.current
    val viewModel: PreferencesViewModel = viewModel(factory = viewModelFactory { initializer { PreferencesViewModel(container) } })
    val prefs by viewModel.preferences.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        ValueRow("Language", prefs.language)
        ToggleRow("Standard User Password", checked = false, enabled = false)
        ToggleRow("Fault Do-Not-Disturb Mode", checked = prefs.taskDndEnabled) { checked ->
            viewModel.update { it.copy(taskDndEnabled = checked) }
        }
        ToggleRow("Show Map Creation Shortcut", checked = false, enabled = false)
        ToggleRow("Rotate Screen 180°", checked = false, enabled = false)
        ValueRow("Elevator Communication Method", "—")
        ValueRow("Re-sweep Switch for ROS Area", "Off")
        ValueRow("ROS Dynamic Path Toggle", "Not Selected")
        Spacer(Modifier.height(16.dp))
        CapabilityBadge(Capability.UNAVAILABLE)
        Spacer(Modifier.height(6.dp))
        Text(
            "Disabled rows above have no corresponding API in the licensed Peanut SDK.",
            style = MaterialTheme.typography.bodySmall,
            color = SakarColors.TextFaint
        )
    }
}

/**
 * Reference-matched flat row list. "Robot Debugging" opens this app's existing real actuator-test
 * flow (group list -> tests -> run, with confirmation for destructive tests). "Return to charge
 * stress test" runs the existing real return_to_dock_test directly. "Wash Pressure Test" and
 * "Industrial computer/ROS connection test" have no backing API anywhere in this project (the
 * cleaning/wash domain has no SDK facade at all - see CleaningRepository) and are shown honestly
 * unavailable rather than faked.
 */
@Composable
fun SuperUserRobotDebuggingPane(onOpenFullDebugging: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: DiagnosticsViewModel = viewModel(factory = viewModelFactory { initializer { DiagnosticsViewModel(container) } })
    var pendingTest by remember { mutableStateOf<ActuatorTest?>(null) }
    var lastResult by remember { mutableStateOf<ActuatorTestResult?>(null) }
    val returnToDockTest = remember { viewModel.tests("docking_comms").firstOrNull { it.id == "return_to_dock_test" } }

    Column(Modifier.fillMaxWidth()) {
        DebugRow("Robot Debugging", onClick = onOpenFullDebugging)
        DebugRow("Return to charge stress test") {
            val test = returnToDockTest
            when {
                test == null -> Toast.makeText(context, "Not available", Toast.LENGTH_SHORT).show()
                test.requiresConfirmation -> pendingTest = test
                else -> viewModel.runTest(test.id) { lastResult = it }
            }
        }
        DebugRow("Wash Pressure Test") {
            Toast.makeText(context, "Not available - no wash/pressure API in the licensed SDK", Toast.LENGTH_SHORT).show()
        }
        DebugRow("Industrial computer/ROS connection test") {
            Toast.makeText(context, "Not available - not exposed by the licensed SDK", Toast.LENGTH_SHORT).show()
        }

        lastResult?.let { result ->
            Spacer(Modifier.height(16.dp))
            SectionCard(title = if (result.success) "Result: OK" else "Result: Failed") {
                Text(result.message, color = if (result.success) SakarColors.TextMuted else SakarColors.Danger)
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
                TextButton(onClick = { viewModel.runTest(test.id) { lastResult = it }; pendingTest = null }) { Text("Proceed", color = SakarColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { pendingTest = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DebugRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SakarColors.TextSubtle)
    }
    Divider(color = SakarColors.Border)
}

/**
 * Reference-matched rows plus this app's pre-existing System-Settings-shaped content (App
 * Guardian Service, Auto-start, Legacy Diagnostics, Exit App) that used to live under the
 * confusingly-named "General Settings" sidebar entry - it belongs here instead, matching the
 * vendor reference's actual section boundaries. "Data Center Connection" reports the real
 * cloudSyncState (always OFFLINE today - no Sakar Cloud exists yet), never a fabricated success.
 */
@Composable
fun SuperUserSystemSettingsPane() {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val cloudSyncState by container.connectionRepository.cloudSyncState.collectAsState(initial = CloudSyncState.OFFLINE)

    Column(Modifier.fillMaxWidth()) {
        InlineBanner(
            "Kiosk-mode controls (auto-start on boot, hiding the system navigation bar, volume lockdown) require the app to be enrolled as Device Owner - not yet configured on this device.",
            isWarning = true
        )
        Spacer(Modifier.height(16.dp))
        ToggleRow("App Guardian Service", checked = false, enabled = false)
        ToggleRow("Auto-start App", checked = false, enabled = false)
        ToggleRow("Hide System Menu Bar", checked = false, enabled = false)
        ToggleRow("Decrease System Volume", checked = false, enabled = false)
        ValueRow("Data Center", "Not configured")
        ValueRow("Data Center Connection", if (cloudSyncState == CloudSyncState.ONLINE) "Connection successful" else "Not connected")
        Spacer(Modifier.height(20.dp))
        SectionCard(title = "Legacy Diagnostics Dashboard") {
            Text("Opens the original raw Peanut SDK diagnostic screen (connection, runtime, sensors, raw call log).", color = SakarColors.TextFaint)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { context.startActivity(Intent(context, LegacyDiagnosticsActivity::class.java)) }) { Text("Open Legacy Diagnostics") }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { (context as? Activity)?.finish() },
            colors = ButtonDefaults.buttonColors(containerColor = SakarColors.Danger),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Exit App", color = Color.White) }
    }
}
