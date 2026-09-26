package com.sakarrobotics.c40agent.operatorui.superuser

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.ManualDriveDirection
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.manualdrive.ManualDriveViewModel
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

/**
 * Reference-matched "Debug" screen (the vendor's full hardware-actuation dashboard). Real data:
 * Emergency Stop / raw motor status (DiagnosticsSnapshot), raw IMU and Battery reads (Capability
 * REAL, shown as their actual SDK strings rather than guess-parsed into fabricated Heading/
 * Current/Voltage fields whose response shape was never confirmed - see BatteryState's own KDoc
 * for why that guess-parsing is deliberately avoided elsewhere in this app too), and the embedded
 * manual-drive pad (reuses the exact same ManualDriveViewModel/RobotMotionController built for
 * Manual Drive - REAL or SIMULATED depending on the active backend).
 *
 * Everything else on this screen - every brush/pump/fan/water/workstation/light/bumper control -
 * has no corresponding API anywhere in the licensed Peanut SDK: CleanComponent (the vendor's own
 * facade for all of this) is confirmed absent at the class level (see the project's own
 * COMPATIBILITY_REPORT.md / reverse-engineering audit). Those rows are rendered in the exact
 * reference shape and order but disabled, with placeholder "--"/"Not available" values instead of
 * fabricated live readings, so the layout matches without claiming capability this app does not
 * have.
 */
@Composable
fun RobotHardwareDebugScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val diagViewModel: DiagnosticsViewModel = viewModel(factory = viewModelFactory { initializer { DiagnosticsViewModel(container) } })
    val driveViewModel: ManualDriveViewModel = viewModel(factory = viewModelFactory { initializer { ManualDriveViewModel(container) } })

    LaunchedEffect(Unit) {
        diagViewModel.refresh()
        diagViewModel.refreshSensors()
        container.batteryRepository.refresh()
    }
    val battery by container.batteryRepository.battery.collectAsState(
        initial = BatteryState(null, false, null, null, Capability.REAL)
    )

    Scaffold(topBar = { SakarTopBar("Debug", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding)
        ) {
            InlineBanner(
                "The brush/pump/fan/water/workstation controls below match the reference layout, but " +
                    "CleanComponent (the vendor's facade for all of them) is absent from the licensed Peanut " +
                    "SDK - they are shown disabled, not simulated as if working.",
                isWarning = true
            )
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    SectionCard {
                        val snapshot = diagViewModel.snapshot
                        DebugValueRow("Emergency Stop Status", if (snapshot?.emergencyStopEngaged == true) "On" else "Off")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Hub Motor Status", style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
                            Switch(checked = false, onCheckedChange = {}, enabled = false)
                        }
                        Spacer(Modifier.height(4.dp))
                        CapabilityBadge(Capability.UNAVAILABLE)
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionCard(title = "IMU") {
                        CapabilityBadge(Capability.REAL)
                        Spacer(Modifier.height(6.dp))
                        Text(diagViewModel.sensors?.imu?.value ?: "Reading...", color = SakarColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionCard(title = "Battery") {
                        CapabilityBadge(Capability.REAL)
                        Spacer(Modifier.height(6.dp))
                        Text(battery.raw ?: "Reading...", color = SakarColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Column(Modifier.weight(1f)) {
                    SectionCard {
                        listOf(
                            "Left Side Brush", "Right Side Brush",
                            "Front Roller Brush Chamber (Sweeping Brush/Dust-Push Brush/Fiber Brush)",
                            "Rear Roller Brush Chamber (Washing Brush)", "Dust Bin / Dust Bag",
                            "Wastewater Tank", "Armrest Status", "Top Cover / Skull"
                        ).forEach { label -> DebugValueRow(label, "--") }
                        CapabilityBadge(Capability.UNAVAILABLE)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionCard {
                DebugValueRow("Hub Speed", "Left --, Right --")
                DebugValueRow("Pressure Sensor - Tank Vacuum", "--")
                DebugValueRow("Pressure Sensor - Duct Vacuum", "--")
                DebugValueRow("Clean Water Volume", "--")
                DebugValueRow("Dirty Water Volume", "--")
                DebugValueRow("Carpet Detection", "--")
                CapabilityBadge(Capability.UNAVAILABLE)
            }

            Spacer(Modifier.height(20.dp))
            Text("Component & Speed Test", style = MaterialTheme.typography.labelLarge, color = SakarColors.TextSubtle)
            Spacer(Modifier.height(8.dp))

            DebugSpeedTestCard("Fan")
            DebugSpeedTestCard("Side Brush (RPM)")
            DebugSpeedTestCard("Washing Brush", hasRaiseLower = true)
            DebugStepperCard("Clean Water Pump", "Washing Nozzle Flow Rate (0-100%)")
            DebugSpeedTestCard("Sweeping Brush / Dust-Push Brush / Fiber Brush", hasRaiseLower = true)

            Spacer(Modifier.height(20.dp))
            Text("Manual Drive (embedded)", style = MaterialTheme.typography.labelLarge, color = SakarColors.TextSubtle)
            Spacer(Modifier.height(8.dp))
            SectionCard {
                CapabilityBadge(if (driveViewModel.backendMode.name == "SIMULATED") Capability.SIMULATED else Capability.GATED)
                Spacer(Modifier.height(8.dp))
                EmbeddedDrivePad(driveViewModel)
            }

            Spacer(Modifier.height(20.dp))
            DebugActuatorSection(
                title = "Front Roller Brush (Sweeping Brush) + Side Brush",
                rows = listOf(
                    "Front Roller Brush: Forward/Reverse Rotation (0/1)" to "0",
                    "Front Roller Brush: Speed (0-900) r/min" to "0 r/m",
                    "Front Roller Brush: Lowering Distance (Ground Pressure) (0-30)" to "0 mm",
                    "Side Brush: Speed (0-100)" to "-- r/min",
                    "Fan (Vacuum) (0-100)%" to "0 r/m"
                )
            )
            Spacer(Modifier.height(12.dp))
            DebugActuatorSection(
                title = "Rear Roller Brush (Washing / Dust Mop / Wet Mopping Brush) + Squeegee",
                rows = listOf(
                    "Rear Roller Brush: Forward/Reverse Rotation (0/1)" to "0",
                    "Rear Roller Brush: Speed (0-900) r/min" to "0 r/m",
                    "Rear Roller Brush + Squeegee: Lowering Distance (0-30)" to "0 mm",
                    "Clean Water Pump: Washing Nozzle Flow Rate (0-200) ml/min" to "0 ml/min",
                    "Side Brush: Speed (0-100)" to "-- r/min",
                    "Fan - Water Suction (0-100)%" to "0 r/m"
                )
            )
            Spacer(Modifier.height(12.dp))
            DebugActuatorSection(
                title = "Workstation Settings (Water Refill, Drainage, Cleaning Agent)",
                rows = listOf(
                    "Machine Drainage Pump" to "--",
                    "Workstation Water Refill" to "Flow Rate: -- ml/min",
                    "Workstation Drain Pump" to "Wastewater Tank Level: Unknown",
                    "Workstation Detergent Pump" to "Flow Rate: -- ml/min"
                )
            )

            Spacer(Modifier.height(20.dp))
            Text("Other", style = MaterialTheme.typography.labelLarge, color = SakarColors.TextSubtle)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    SectionCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Left Light Strip", style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
                            Switch(checked = false, onCheckedChange = {}, enabled = false)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Right Light Strip", style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
                            Switch(checked = false, onCheckedChange = {}, enabled = false)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionCard(title = "Anti-collision Bumper") {
                        DebugValueRow("Left", "--")
                        DebugValueRow("Medium", "--")
                        DebugValueRow("Right", "--")
                    }
                }
                Column(Modifier.weight(1f)) {
                    SectionCard(title = "Geomagnetic Detection") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Left", style = MaterialTheme.typography.labelLarge, color = SakarColors.TextSubtle)
                                DebugValueRow("X", "--"); DebugValueRow("Y", "--"); DebugValueRow("Z", "--"); DebugValueRow("TOTAL", "--")
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Right", style = MaterialTheme.typography.labelLarge, color = SakarColors.TextSubtle)
                                DebugValueRow("X", "--"); DebugValueRow("Y", "--"); DebugValueRow("Z", "--"); DebugValueRow("TOTAL", "--")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DebugValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextPrimary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
    }
}

@Composable
private fun DebugSpeedTestCard(title: String, hasRaiseLower: Boolean = false) {
    SectionCard(title = title) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Speed Level", style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("1", "2", "3").forEach { level ->
                    Surface(shape = CircleShape, color = SakarColors.Background, modifier = Modifier.size(36.dp)) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(level, color = SakarColors.TextSubtle)
                        }
                    }
                }
            }
        }
        if (hasRaiseLower) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Raise / Lower", style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
                Switch(checked = false, onCheckedChange = {}, enabled = false)
            }
        }
        Spacer(Modifier.height(4.dp))
        CapabilityBadge(Capability.UNAVAILABLE)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DebugStepperCard(title: String, fieldLabel: String) {
    SectionCard(title = title) {
        Text(fieldLabel, style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = SakarColors.Background) {
                Text("60", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = SakarColors.TextSubtle)
            }
            Switch(checked = false, onCheckedChange = {}, enabled = false)
        }
        Spacer(Modifier.height(4.dp))
        CapabilityBadge(Capability.UNAVAILABLE)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DebugActuatorSection(title: String, rows: List<Pair<String, String>>) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = SakarColors.TextPrimary)
            Switch(checked = false, onCheckedChange = {}, enabled = false)
        }
        Spacer(Modifier.height(6.dp))
        rows.forEach { (label, value) -> DebugValueRow(label, value) }
        Spacer(Modifier.height(4.dp))
        CapabilityBadge(Capability.UNAVAILABLE)
    }
}

/**
 * Compact reference-shaped joystick (Forward top, Turn Left / STOP / Turn Right middle, Reverse
 * bottom - STOP in the cross's center, matching the vendor layout). Reuses the exact same
 * ManualDriveViewModel built for Manual Drive: same one-command-per-press contract (press sends
 * one command, release always sends STOP), same REAL/SIMULATED backend selection, same safety
 * gates. Unlike the Manual Drive dialog's D-pad, this does not include the numeric speed-level
 * stepper shown next to the reference's pad - only the five motion buttons are wired here.
 */
@Composable
private fun EmbeddedDrivePad(viewModel: ManualDriveViewModel) {
    val statusMessage by viewModel.statusMessage.collectAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EmbeddedDriveButton("Forward", ManualDriveDirection.FORWARD, viewModel)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmbeddedDriveButton("Turn Left", ManualDriveDirection.LEFT, viewModel)
            Spacer(Modifier.width(8.dp))
            EmbeddedStopButton(viewModel)
            Spacer(Modifier.width(8.dp))
            EmbeddedDriveButton("Turn Right", ManualDriveDirection.RIGHT, viewModel)
        }
        Spacer(Modifier.height(8.dp))
        EmbeddedDriveButton("Reverse", ManualDriveDirection.REVERSE, viewModel)
        Spacer(Modifier.height(10.dp))
        Text(statusMessage ?: "Idle", style = MaterialTheme.typography.bodySmall, color = if (statusMessage != null) SakarColors.Danger else SakarColors.TextSubtle)
    }
}

@Composable
private fun EmbeddedDriveButton(label: String, direction: ManualDriveDirection, viewModel: ManualDriveViewModel) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SakarColors.PrimarySoft,
        modifier = Modifier
            .width(96.dp)
            .height(44.dp)
            .pointerInput(direction) {
                detectTapGestures(
                    onPress = {
                        viewModel.onPress(direction)
                        tryAwaitRelease()
                        viewModel.onRelease()
                    }
                )
            }
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Primary)
        }
    }
}

@Composable
private fun EmbeddedStopButton(viewModel: ManualDriveViewModel) {
    Surface(
        shape = CircleShape,
        color = SakarColors.Danger,
        modifier = Modifier
            .size(56.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { viewModel.onPress(ManualDriveDirection.STOP) })
            }
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White)
        }
    }
}
