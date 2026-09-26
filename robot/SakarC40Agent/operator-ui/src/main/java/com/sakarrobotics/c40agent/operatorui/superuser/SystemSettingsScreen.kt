package com.sakarrobotics.c40agent.operatorui.superuser

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import com.sakarrobotics.c40agent.ui.MainActivity as LegacyDiagnosticsActivity

@Composable
fun SuperUserSystemSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(topBar = { SakarTopBar("System Settings", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("Kiosk-mode controls (auto-start on boot, hiding the system navigation bar) require the app to be enrolled as Device Owner - not yet configured on this device.", isWarning = true)
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "App Guardian Service") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Auto-restart if the app crashes")
                    Switch(checked = false, onCheckedChange = {}, enabled = false)
                }
                CapabilityBadge(com.sakarrobotics.c40agent.domain.model.Capability.UNAVAILABLE)
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Auto-start on Boot") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Launch automatically when the tablet boots")
                    Switch(checked = false, onCheckedChange = {}, enabled = false)
                }
                CapabilityBadge(com.sakarrobotics.c40agent.domain.model.Capability.UNAVAILABLE)
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Legacy Diagnostics Dashboard") {
                Text("Opens the original raw Peanut SDK diagnostic screen (connection, runtime, sensors, raw call log).", color = SakarColors.TextFaint)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    context.startActivity(Intent(context, LegacyDiagnosticsActivity::class.java))
                }) { Text("Open Legacy Diagnostics") }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { (context as? Activity)?.finish() },
                colors = ButtonDefaults.buttonColors(containerColor = SakarColors.Danger),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exit App", color = Color.White) }
        }
    }
}
