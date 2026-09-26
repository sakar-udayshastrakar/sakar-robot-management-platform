package com.sakarrobotics.c40agent.operatorui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.sakarrobotics.c40agent.domain.model.ScheduleRepeat
import com.sakarrobotics.c40agent.domain.model.ScheduleTask
import com.sakarrobotics.c40agent.domain.model.SyncState
import com.sakarrobotics.c40agent.domain.model.Weekday
import com.sakarrobotics.c40agent.domain.usecase.ScheduleUseCases
import com.sakarrobotics.c40agent.operatorui.common.EmptyState
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(container: AppContainer) : ViewModel() {
    private val useCases = ScheduleUseCases(container.scheduleRepository)

    val schedules: StateFlow<List<ScheduleTask>> =
        useCases.observeSchedules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch { useCases.setEnabled(id, enabled) }
    fun delete(id: String) = viewModelScope.launch { useCases.delete(id) }
    fun save(task: ScheduleTask) = viewModelScope.launch { useCases.create(task) }
}

@Composable
fun ScheduleListScreen(onBack: () -> Unit, onNew: () -> Unit, onEdit: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ScheduleViewModel = viewModel(factory = viewModelFactory { initializer { ScheduleViewModel(container) } })
    val schedules by viewModel.schedules.collectAsState()

    Scaffold(
        topBar = {
            SakarTopBar("Scheduled Cleaning", onBack) {
                TextButton(onClick = onNew) { Text("+ New Task", color = SakarColors.Primary) }
            }
        },
        containerColor = SakarColors.Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            if (schedules.isEmpty()) {
                EmptyState("No scheduled tasks yet")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(schedules, key = { it.id }) { task ->
                        SectionCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(task.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${task.startTime} - ${task.endTime} · ${task.repeat.name} · ${task.cleaningCycles}x · ${task.mode.name.replace('_', ' ')}",
                                        color = SakarColors.TextFaint,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Switch(checked = task.enabled, onCheckedChange = { viewModel.setEnabled(task.id, it) })
                                TextButton(onClick = { onEdit(task.id) }) { Text("Edit") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleEditScreen(existing: ScheduleTask?, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ScheduleViewModel = viewModel(factory = viewModelFactory { initializer { ScheduleViewModel(container) } })

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var startTime by remember { mutableStateOf(existing?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(existing?.endTime ?: "10:00") }
    var repeat by remember { mutableStateOf(existing?.repeat ?: ScheduleRepeat.ONCE) }
    var cycles by remember { mutableStateOf(existing?.cleaningCycles ?: 1) }
    var mode by remember { mutableStateOf(existing?.mode ?: CleaningMode.SWEEP) }
    var intensity by remember { mutableStateOf(existing?.intensity ?: CleaningIntensity.STANDARD) }
    var selectedDays by remember { mutableStateOf(existing?.days ?: emptySet()) }

    Scaffold(
        topBar = {
            SakarTopBar(if (existing == null) "New Task" else "Edit Task", onBack) {
                TextButton(onClick = {
                    viewModel.save(
                        ScheduleTask(
                            id = existing?.id ?: "",
                            name = name.ifBlank { "Task" },
                            enabled = existing?.enabled ?: true,
                            startTime = startTime,
                            endTime = endTime,
                            repeat = repeat,
                            days = selectedDays,
                            cleaningCycles = cycles,
                            zoneIds = existing?.zoneIds ?: emptyList(),
                            mode = mode,
                            intensity = intensity,
                            syncState = SyncState.NOT_SYNCED
                        )
                    )
                    onBack()
                }) { Text("Save", color = SakarColors.Primary) }
            }
        },
        containerColor = SakarColors.Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Task Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start (HH:mm)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End (HH:mm)") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Repeat") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScheduleRepeat.values().forEach {
                        FilterChip(selected = repeat == it, onClick = { repeat = it }, label = { Text(it.name.replace('_', ' ')) })
                    }
                }
                if (repeat == ScheduleRepeat.WEEKLY_CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Weekday.values().forEach { day ->
                            FilterChip(
                                selected = day in selectedDays,
                                onClick = { selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day },
                                label = { Text(day.name.take(3)) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Cleaning Mode") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleaningMode.values().forEach {
                        FilterChip(selected = mode == it, onClick = { mode = it }, label = { Text(it.name.replace('_', ' ')) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Intensity") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleaningIntensity.values().forEach {
                        FilterChip(selected = intensity == it, onClick = { intensity = it }, label = { Text(it.name) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Cleaning Cycles") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { if (cycles > 1) cycles-- }) { Text("-") }
                    Text("$cycles", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { if (cycles < 10) cycles++ }) { Text("+") }
                }
            }
        }
    }
}
