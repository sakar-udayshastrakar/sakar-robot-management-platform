package com.sakarrobotics.c40agent.operatorui.superuser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
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
import com.sakarrobotics.c40agent.operatorui.nav.Routes
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

/**
 * Reference-matched: a persistent left sidebar (icon + label rows, colored per-section like the
 * vendor's own Super User panel) with the selected section's content in the right pane. Shows
 * ONLY the 5 reference-confirmed sections - "Network", "Resource Management", "General Settings",
 * "Robot Debugging" and "System Settings" (rendered inline, see SuperUserPanes.kt) - matching the
 * vendor reference sidebar exactly.
 *
 * This screen previously also listed Manual Drive Simulation, Logs, Robot Information, Robot
 * Installation and Maps below a divider; none of those appear in the vendor reference sidebar, so
 * their visible entries were removed here (see docs/ui/CURRENT_UI_DUPLICATION_AUDIT.md and
 * docs/ui/UI_CHANGE_LEDGER.md). Nothing underlying was deleted: Manual Drive is still reachable
 * from Home, Robot Information is still reachable from Settings, and Logs/Robot
 * Installation/Maps keep their routes/screens/ViewModels intact in source - they are simply not
 * navigable from anywhere in the UI right now (see docs/ui/ROUTE_REGISTRY.md, status ORPHANED).
 */
private enum class SuperUserSection(val label: String, val icon: ImageVector, val tint: Color) {
    NETWORK("Network", Icons.Filled.Public, SakarColors.Success),
    RESOURCE_MANAGEMENT("Resource Management", Icons.Filled.Folder, SakarColors.Info),
    GENERAL_SETTINGS("General Settings", Icons.Filled.Extension, SakarColors.Warning),
    ROBOT_DEBUGGING("Robot Debugging", Icons.Filled.Tune, SakarColors.Info),
    SYSTEM_SETTINGS("System Settings", Icons.Filled.Shield, SakarColors.Danger)
}

@Composable
fun SuperUserHomeScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onExitSuperUser: () -> Unit) {
    var selected by remember { mutableStateOf(SuperUserSection.NETWORK) }

    Row(Modifier.fillMaxSize().background(SakarColors.Background)) {
        SuperUserSidebar(
            selected = selected,
            onSelect = { selected = it },
            // The reference UI shows only one way out ("Back to Home") - unlike the old top-bar
            // "Exit Super User" button, this both resets the in-memory role AND navigates back,
            // so a stale SUPER_USER role can never linger after leaving this screen.
            onBackToHome = onExitSuperUser
        )
        Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(28.dp)) {
            when (selected) {
                SuperUserSection.NETWORK -> SuperUserNetworkPane()
                SuperUserSection.RESOURCE_MANAGEMENT -> SuperUserResourceManagementPane()
                SuperUserSection.GENERAL_SETTINGS -> SuperUserGeneralSettingsPane()
                SuperUserSection.ROBOT_DEBUGGING -> SuperUserRobotDebuggingPane(onOpenFullDebugging = { onOpen(Routes.SUPER_USER_DEBUG_GROUPS) })
                SuperUserSection.SYSTEM_SETTINGS -> SuperUserSystemSettingsPane()
            }
        }
    }
}

@Composable
private fun SuperUserSidebar(
    selected: SuperUserSection,
    onSelect: (SuperUserSection) -> Unit,
    onBackToHome: () -> Unit
) {
    Box(
        Modifier
            .widthIn(min = 260.dp, max = 260.dp)
            .fillMaxHeight()
            .background(SakarColors.Surface)
    ) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(SakarColors.Border).align(Alignment.CenterEnd))
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            androidx.compose.foundation.layout.Column {
                SidebarRow(label = "Back to Home", icon = Icons.Filled.Home, tint = SakarColors.Info, selected = false, onClick = onBackToHome)
                Divider(color = SakarColors.Border)

                SuperUserSection.values().forEach { section ->
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
    Row(Modifier.fillMaxWidth().height(64.dp)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (selected) SakarColors.Info else Color.Transparent))
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
