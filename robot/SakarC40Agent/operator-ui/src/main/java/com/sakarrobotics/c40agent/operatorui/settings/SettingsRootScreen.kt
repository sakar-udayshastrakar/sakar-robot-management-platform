package com.sakarrobotics.c40agent.operatorui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

data class SettingsCategory(val title: String, val subtitle: String, val route: String)

fun settingsCategories(): List<SettingsCategory> = listOf(
    SettingsCategory("Consumables", "Brush, filter and mop wear", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_CONSUMABLES),
    SettingsCategory("Cleaning Data", "Cleaning records and defaults", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_CLEANING_DATA),
    SettingsCategory("Workstation", "Water refill, drainage, docking base", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_WORKSTATION),
    SettingsCategory("Charging", "Auto-recharge and battery protection", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_CHARGING),
    SettingsCategory("Wi-Fi / Network", "Robot network connection", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_NETWORK),
    SettingsCategory("Display", "Screen face and promotional content", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_DISPLAY),
    SettingsCategory("Sound", "Music and voice prompt volume", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_SOUND),
    SettingsCategory("Screen Lock", "Lock screen password and timeout", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_SCREEN_LOCK),
    SettingsCategory("General", "Language, positioning, breakpoints", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_GENERAL),
    SettingsCategory("Robot Information", "Model, serial, versions, network", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_ROBOT_INFO),
    SettingsCategory("Resource Management", "Business profile selection", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_RESOURCE_MGMT),
    SettingsCategory("System Information", "App, SDK and Android versions", com.sakarrobotics.c40agent.operatorui.nav.Routes.SETTINGS_SYSTEM_INFO)
)

@Composable
fun SettingsRootScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Scaffold(topBar = { SakarTopBar("Settings", onBack) }, containerColor = SakarColors.Background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(settingsCategories()) { category ->
                SectionCard(modifier = Modifier.clickable { onOpen(category.route) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Column {
                            Text(category.title, style = MaterialTheme.typography.titleMedium, color = SakarColors.TextPrimary)
                            Text(category.subtitle, style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SakarColors.TextSubtle)
                    }
                }
            }
        }
    }
}
