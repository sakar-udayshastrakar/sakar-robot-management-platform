package com.sakarrobotics.c40agent.operatorui.cleaning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.CleaningIntensity
import com.sakarrobotics.c40agent.domain.model.CleaningMode
import com.sakarrobotics.c40agent.domain.model.CleaningRunState
import com.sakarrobotics.c40agent.domain.model.CleaningSession
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StartCleaningViewModel(private val container: AppContainer) : ViewModel() {
    val session: StateFlow<CleaningSession?> =
        container.cleaningRepository.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun start(mode: CleaningMode, intensity: CleaningIntensity, cycles: Int) {
        viewModelScope.launch { container.cleaningRepository.startCleaning(zoneIds = emptyList(), mode = mode, intensity = intensity, cycles = cycles) }
    }

    fun pause() = viewModelScope.launch { container.cleaningRepository.pause() }
    fun resume() = viewModelScope.launch { container.cleaningRepository.resume() }
    fun stop() = viewModelScope.launch { container.cleaningRepository.stop() }
}

@Composable
fun StartCleaningScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: StartCleaningViewModel = viewModel(factory = viewModelFactory { initializer { StartCleaningViewModel(container) } })
    val session by viewModel.session.collectAsState()

    var selectedMode by remember { mutableStateOf(CleaningMode.WATER_SUCTION) }
    var selectedIntensity by remember { mutableStateOf(CleaningIntensity.STANDARD) }
    var cycles by remember { mutableStateOf(1) }

    Scaffold(topBar = { SakarTopBar("Start Cleaning", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("Whole-floor map selection is not yet available on this build - cleaning starts across the currently active area.", isWarning = true)
            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Cleaning Mode") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CleaningMode.values().toList()) { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.name.replace('_', ' ')) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Intensity") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleaningIntensity.values().forEach { intensity ->
                        FilterChip(
                            selected = selectedIntensity == intensity,
                            onClick = { selectedIntensity = intensity },
                            label = { Text(intensity.name) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Cleaning Cycles") {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { if (cycles > 1) cycles-- }) { Text("-") }
                    Text("$cycles", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { if (cycles < 10) cycles++ }) { Text("+") }
                }
            }
            Spacer(Modifier.height(16.dp))

            val currentSession = session
            if (currentSession != null && currentSession.state != CleaningRunState.IDLE) {
                SectionCard(title = "Progress") {
                    Text("${currentSession.progressPercent}% — cycle ${currentSession.cyclesCompleted + 1} of ${currentSession.cyclesRequested}")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { currentSession.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    CapabilityBadge(currentSession.capability)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (currentSession.state) {
                            CleaningRunState.CLEANING -> Button(onClick = viewModel::pause) { Text("Pause") }
                            CleaningRunState.PAUSED -> Button(onClick = viewModel::resume) { Text("Resume") }
                            else -> {}
                        }
                        OutlinedButton(onClick = viewModel::stop) { Text("Stop") }
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.start(selectedMode, selectedIntensity, cycles) },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Start Clean", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
