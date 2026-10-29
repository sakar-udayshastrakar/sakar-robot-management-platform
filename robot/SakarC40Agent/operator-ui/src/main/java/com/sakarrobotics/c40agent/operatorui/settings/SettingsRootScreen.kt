package com.sakarrobotics.c40agent.operatorui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

/**
 * Two-pane master-detail Settings, matching the vendor reference layout: a persistent left sidebar
 * of sections with the selected section's content in the right pane. Each section reuses the exact
 * same detail composable as its standalone route (rendered with `embedded = true`, so there is no
 * duplicate back top-bar) — see [SettingsPane]. Sakar-branded (orange accent / Sakar wordmark kept),
 * layout matched to the reference's sidebar + detail structure.
 */
private enum class SettingsSection(val label: String, val icon: ImageVector, val tint: Color) {
    CONSUMABLES("Consumables", Icons.Filled.Build, SakarColors.Primary),
    CLEANING_DATA("Cleaning Data", Icons.Filled.BarChart, SakarColors.Info),
    WORKSTATION("Workstation", Icons.Filled.Dock, SakarColors.Info),
    CHARGING("Charging Settings", Icons.Filled.BatteryChargingFull, SakarColors.Success),
    SCREEN_LOCK("Screen Lock Settings", Icons.Filled.Lock, SakarColors.TextMuted),
    GENERAL("General Settings", Icons.Filled.Tune, SakarColors.Warning),
    SOUND("Sound", Icons.Filled.VolumeUp, SakarColors.Primary),
    DISPLAY("Display", Icons.Filled.Brightness6, SakarColors.Info),
    NETWORK("Wi-Fi / Network", Icons.Filled.Wifi, SakarColors.Success),
    ROBOT_INFO("Robot Information", Icons.Filled.SmartToy, SakarColors.Info),
    RESOURCE_MGMT("Resource Management", Icons.Filled.Folder, SakarColors.Warning),
    SYSTEM_INFO("System Information", Icons.Filled.Info, SakarColors.TextMuted)
}

@Composable
fun SettingsRootScreen(onBack: () -> Unit, @Suppress("UNUSED_PARAMETER") onOpen: (String) -> Unit = {}) {
    var selected by remember { mutableStateOf(SettingsSection.CONSUMABLES) }
    val noBack: () -> Unit = {}

    Row(Modifier.fillMaxSize().background(SakarColors.Background)) {
        SettingsSidebar(selected = selected, onSelect = { selected = it }, onBackToHome = onBack)
        Box(Modifier.weight(1f).fillMaxHeight().background(SakarColors.Background)) {
            when (selected) {
                SettingsSection.CONSUMABLES -> ConsumablesScreen(onBack = noBack, embedded = true)
                SettingsSection.CLEANING_DATA -> CleaningDataScreen(onBack = noBack, embedded = true)
                SettingsSection.WORKSTATION -> WorkstationScreen(onBack = noBack, embedded = true)
                SettingsSection.CHARGING -> ChargingSettingsScreen(onBack = noBack, embedded = true)
                SettingsSection.SCREEN_LOCK -> ScreenLockSettingsScreen(onBack = noBack, embedded = true)
                SettingsSection.GENERAL -> GeneralSettingsScreen(onBack = noBack, embedded = true)
                SettingsSection.SOUND -> SoundSettingsScreen(onBack = noBack, embedded = true)
                SettingsSection.DISPLAY -> DisplaySettingsScreen(onBack = noBack, embedded = true)
                SettingsSection.NETWORK -> NetworkScreen(onBack = noBack, embedded = true)
                SettingsSection.ROBOT_INFO -> RobotInfoScreen(onBack = noBack, embedded = true)
                SettingsSection.RESOURCE_MGMT -> ResourceManagementScreen(onBack = noBack, embedded = true)
                SettingsSection.SYSTEM_INFO -> SystemInfoScreen(onBack = noBack, embedded = true)
            }
        }
    }
}

@Composable
private fun SettingsSidebar(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    onBackToHome: () -> Unit
) {
    Box(
        Modifier
            .widthIn(min = 280.dp, max = 280.dp)
            .fillMaxHeight()
            .background(SakarColors.Surface)
    ) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(SakarColors.Border).align(Alignment.CenterEnd))
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column {
                SidebarRow(label = "Back to Home", icon = Icons.Filled.Home, tint = SakarColors.Info, selected = false, onClick = onBackToHome)
                Divider(color = SakarColors.Border)
                SettingsSection.values().forEach { section ->
                    SidebarRow(
                        label = section.label,
                        icon = section.icon,
                        tint = section.tint,
                        selected = section == selected,
                        onClick = { onSelect(section) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarRow(label: String, icon: ImageVector, tint: Color, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (selected) SakarColors.Primary else Color.Transparent))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (selected) SakarColors.Background else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.height(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = SakarColors.TextPrimary)
        }
    }
}
