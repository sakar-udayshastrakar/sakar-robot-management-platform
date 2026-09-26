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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.ChargingSettings
import com.sakarrobotics.c40agent.domain.model.WorkstationState
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ChargingSettingsViewModel(private val container: AppContainer) : ViewModel() {
    var settings by androidx.compose.runtime.mutableStateOf(container.chargingRepository.settings)
        private set

    fun update(transform: (ChargingSettings) -> ChargingSettings) {
        settings = transform(settings)
        container.chargingRepository.settings = settings
    }
}

@Composable
fun ChargingSettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ChargingSettingsViewModel = viewModel(factory = viewModelFactory { initializer { ChargingSettingsViewModel(container) } })
    val settings = viewModel.settings

    Scaffold(topBar = { SakarTopBar("Charging Settings", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            SectionCard(title = "Idle Battery Protection") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Auto-recharge when idle")
                    Switch(checked = settings.autoRechargeEnabled, onCheckedChange = { viewModel.update { s -> s.copy(autoRechargeEnabled = it) } })
                }
                Text("Auto-recharge level: ${settings.autoRechargeLevelPercent}%", color = SakarColors.TextFaint)
                Slider(
                    value = settings.autoRechargeLevelPercent.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(autoRechargeLevelPercent = v.toInt()) } },
                    valueRange = 10f..90f
                )
                Text("Retry interval: ${settings.idleChargingIntervalMinutes} min", color = SakarColors.TextFaint)
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Task Battery Protection") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Protect battery during tasks")
                    Switch(checked = settings.taskBatteryProtectionEnabled, onCheckedChange = { viewModel.update { s -> s.copy(taskBatteryProtectionEnabled = it) } })
                }
                Text("Minimum operating level: ${settings.minimumOperatingBatteryPercent}%", color = SakarColors.TextFaint)
                Slider(
                    value = settings.minimumOperatingBatteryPercent.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(minimumOperatingBatteryPercent = v.toInt()) } },
                    valueRange = 5f..40f
                )
            }
            Spacer(Modifier.height(12.dp))
            InlineBanner("These thresholds are Sakar-local operator preferences. Automatic enforcement requires an on-robot scheduler service - see Roadmap.", isWarning = true)
        }
    }
}

class WorkstationViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<WorkstationState> =
        container.workstationRepository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkstationState(false, false, false, false, 60, null, com.sakarrobotics.c40agent.domain.model.Capability.UNAVAILABLE))
}

@Composable
fun WorkstationScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: WorkstationViewModel = viewModel(factory = viewModelFactory { initializer { WorkstationViewModel(container) } })
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { SakarTopBar("Workstation", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("No smart workstation (auto water refill / drainage / self-docking base) is connected to this robot yet. This screen is a ready interface for when that hardware integration lands.", isWarning = true)
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Workstation Status") {
                CapabilityBadge(state.capability)
                Spacer(Modifier.height(8.dp))
                Text("Connected: ${state.connected}")
                Text("Water Refill: ${state.waterRefillEnabled}")
                Text("Drainage: ${state.drainageEnabled}")
                Text("Cleaning Agent: ${state.detergentEnabled} (ratio 1:${state.waterToDetergentRatio})")
                Text("Wastewater level: ${state.wasteWaterLevelPercent?.let { "$it%" } ?: "Unknown"}")
            }
        }
    }
}
