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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.model.BusinessProfile
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: PreferencesViewModel = viewModel(factory = viewModelFactory { initializer { PreferencesViewModel(container) } })
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(topBar = { SakarTopBar("General Settings", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            SectionCard(title = "Language") {
                Text(prefs.language, color = SakarColors.TextMuted)
            }
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Resume cleaning from breakpoint")
                    Switch(checked = prefs.resumeFromBreakpoint, onCheckedChange = { viewModel.update { p -> p.copy(resumeFromBreakpoint = it) } })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sweeping brush auto-height")
                    Switch(checked = prefs.sweepingBrushAutoHeight, onCheckedChange = { viewModel.update { p -> p.copy(sweepingBrushAutoHeight = it) } })
                }
            }
        }
    }
}

@Composable
fun ResourceManagementScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: PreferencesViewModel = viewModel(factory = viewModelFactory { initializer { PreferencesViewModel(container) } })
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(topBar = { SakarTopBar("Resource Management", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("This selects a Sakar-local operating profile only (cleaning defaults) - not a Keenon-style downloadable resource package.", isWarning = false)
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Business Profile") {
                BusinessProfile.values().forEach { profile ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected = prefs.businessProfile == profile, onClick = { viewModel.update { it.copy(businessProfile = profile) } })
                        Text(profile.name.replace('_', ' '), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun CleaningDataScreen(onBack: () -> Unit) {
    Scaffold(topBar = { SakarTopBar("Cleaning Data", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("Persisted cleaning-history records are not implemented yet - only the current session's live progress (Start Cleaning screen) is tracked.", isWarning = true)
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Cleaning Record") {
                Text("No historical records available yet.", color = SakarColors.TextFaint)
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Cleaning Settings") {
                Text("Default mode/intensity are chosen per session on the Start Cleaning screen.", color = SakarColors.TextFaint)
            }
        }
    }
}
