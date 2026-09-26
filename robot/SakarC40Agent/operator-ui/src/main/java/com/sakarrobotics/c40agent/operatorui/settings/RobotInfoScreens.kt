package com.sakarrobotics.c40agent.operatorui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sakarrobotics.c40agent.domain.model.RobotIdentity
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

@Composable
fun RobotInfoScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    var identity by remember { mutableStateOf<RobotIdentity?>(null) }
    LaunchedEffect(Unit) { identity = container.identityRepository.identity() }

    Scaffold(topBar = { SakarTopBar("Robot Information", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            val info = identity
            if (info == null) {
                Text("Loading…", color = SakarColors.TextFaint)
            } else {
                SectionCard {
                    InfoRow("Robot Name", info.robotName)
                    InfoRow("Product Line", info.productLine)
                    InfoRow("Model", info.model)
                    InfoRow("Serial Number", info.serialNumber ?: "Not available from current SDK")
                    InfoRow("MAC Address", info.macAddress ?: "Not available from current SDK")
                    InfoRow("Firmware Version", info.firmwareVersion ?: "Not available from current SDK")
                    InfoRow("Robot IP", info.robotIp ?: "Unknown")
                }
            }
        }
    }
}

@Composable
fun SystemInfoScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    var identity by remember { mutableStateOf<RobotIdentity?>(null) }
    LaunchedEffect(Unit) { identity = container.identityRepository.identity() }

    Scaffold(topBar = { SakarTopBar("System Information", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            val info = identity
            if (info == null) {
                Text("Loading…", color = SakarColors.TextFaint)
            } else {
                SectionCard {
                    InfoRow("Application Version", info.appVersionName)
                    InfoRow("Peanut SDK Version", info.sdkVersion)
                    InfoRow("Android Version", info.androidVersion)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = SakarColors.TextFaint)
        Text(value, color = SakarColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(8.dp))
}
