package com.sakarrobotics.c40agent.operatorui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.sakarrobotics.c40agent.domain.model.ScreenFace
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

@Composable
fun DisplaySettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: PreferencesViewModel = viewModel(factory = viewModelFactory { initializer { PreferencesViewModel(container) } })
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(topBar = { SakarTopBar("Display", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            SectionCard(title = "Screen Face") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenFace.values().forEach { face ->
                        FilterChip(
                            selected = prefs.screenFace == face,
                            onClick = { viewModel.update { it.copy(screenFace = face) } },
                            label = { Text(face.name.replace('_', ' ')) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Promotional Content")
                    Switch(checked = prefs.promotionalContentEnabled, onCheckedChange = { viewModel.update { p -> p.copy(promotionalContentEnabled = it) } })
                }
            }
        }
    }
}

@Composable
fun SoundSettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: PreferencesViewModel = viewModel(factory = viewModelFactory { initializer { PreferencesViewModel(container) } })
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(topBar = { SakarTopBar("Sound", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            SectionCard(title = "Music Volume") {
                Slider(value = prefs.musicVolume.toFloat(), onValueChange = { v -> viewModel.update { it.copy(musicVolume = v.toInt()) } }, valueRange = 0f..100f)
                Text("${prefs.musicVolume}", color = SakarColors.TextFaint)
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Voice Prompt Volume") {
                Slider(value = prefs.voiceVolume.toFloat(), onValueChange = { v -> viewModel.update { it.copy(voiceVolume = v.toInt()) } }, valueRange = 0f..100f)
                Text("${prefs.voiceVolume}", color = SakarColors.TextFaint)
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Do Not Disturb") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Music DND")
                    Switch(checked = prefs.musicDndEnabled, onCheckedChange = { viewModel.update { p -> p.copy(musicDndEnabled = it) } })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Task DND")
                    Switch(checked = prefs.taskDndEnabled, onCheckedChange = { viewModel.update { p -> p.copy(taskDndEnabled = it) } })
                }
            }
        }
    }
}

@Composable
fun ScreenLockSettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: PreferencesViewModel = viewModel(factory = viewModelFactory { initializer { PreferencesViewModel(container) } })
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(topBar = { SakarTopBar("Screen Lock Settings", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            SectionCard(title = "Screen Lock Duration") {
                Slider(
                    value = prefs.screenLockDurationSeconds.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(screenLockDurationSeconds = v.toInt()) } },
                    valueRange = 5f..120f
                )
                Text("${prefs.screenLockDurationSeconds} seconds", color = SakarColors.TextFaint)
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Lock Screen Password") {
                Text(
                    if (prefs.screenLockPinConfigured) "A screen lock password is configured." else "No screen lock password configured.",
                    color = SakarColors.TextMuted
                )
            }
        }
    }
}
