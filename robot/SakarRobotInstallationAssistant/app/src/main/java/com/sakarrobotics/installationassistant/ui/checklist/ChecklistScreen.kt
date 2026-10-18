package com.sakarrobotics.installationassistant.ui.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.model.ChecklistEntry
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.Installation
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installationassistant.ui.LocalServices
import com.sakarrobotics.installationassistant.ui.components.AssistantButton
import com.sakarrobotics.installationassistant.ui.components.AssistantScaffold
import com.sakarrobotics.installationassistant.ui.components.SectionCard
import com.sakarrobotics.installationassistant.ui.components.StatusChip
import com.sakarrobotics.installationassistant.ui.components.Tone
import com.sakarrobotics.installationassistant.ui.theme.SakarColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChecklistViewModel(private val services: AssistantServices) : ViewModel() {
    init {
        // The checklist belongs to an installation; open one if none exists yet.
        services.installations.ensureCurrent()
    }

    val installation: StateFlow<Installation?> = services.installations.current

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    fun save(id: String, state: ChecklistState, note: String) {
        _errors.value = when (val r = services.installations.updateChecklist(id, state, note)) {
            is Outcome.Success -> _errors.value - id
            is Outcome.Failure -> _errors.value + (id to r.message)
        }
    }
}

fun checklistTone(state: ChecklistState): Tone = when (state) {
    ChecklistState.DONE -> Tone.SUCCESS
    ChecklistState.PENDING -> Tone.NEUTRAL
    ChecklistState.NOT_APPLICABLE -> Tone.INFO
    ChecklistState.BLOCKED -> Tone.DANGER
}

@Composable
fun ChecklistScreen(onBack: () -> Unit) {
    val services = LocalServices.current
    val vm: ChecklistViewModel = viewModel(factory = viewModelFactory { initializer { ChecklistViewModel(services) } })
    val installation by vm.installation.collectAsState()
    val errors by vm.errors.collectAsState()
    val entries = installation?.checklist.orEmpty()
    val resolved = entries.count { it.state == ChecklistState.DONE || it.state == ChecklistState.NOT_APPLICABLE }

    AssistantScaffold(title = "Installation Checklist", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("checklist_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = installation?.let { "Installation ${it.id}" } ?: "Installation") {
                    LinearProgressIndicator(
                        progress = { if (entries.isEmpty()) 0f else resolved / entries.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$resolved of ${entries.size} checklist items resolved" },
                        color = SakarColors.Orange
                    )
                    Text(
                        "$resolved of ${entries.size} items resolved. Required items that are blocked stop validation; " +
                            "N/A and Blocked need a note. This checklist is a simulation-only record.",
                        style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted,
                        modifier = Modifier.testTag("checklist_summary")
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                ChecklistEntryCard(entry, errors[entry.id], onSave = vm::save)
            }
        }
    }
}

@Composable
private fun ChecklistEntryCard(entry: ChecklistEntry, error: String?, onSave: (String, ChecklistState, String) -> Unit) {
    var state by remember(entry.id, entry.state) { mutableStateOf(entry.state) }
    var note by remember(entry.id, entry.note) { mutableStateOf(entry.note) }
    val changed = state != entry.state || note.trim() != entry.note

    SectionCard(modifier = Modifier.testTag("checklist_${entry.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(entry.description, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
            }
            StatusChip(if (entry.required) "Required" else "Optional", if (entry.required) Tone.BRAND else Tone.NEUTRAL)
            StatusChip("Saved: ${entry.state.label}", checklistTone(entry.state), Modifier.testTag("saved_state_${entry.id}"))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChecklistState.values().forEach { s ->
                FilterChip(
                    selected = state == s,
                    onClick = { state = s },
                    label = { Text(s.label) },
                    modifier = Modifier.testTag("chip_${entry.id}_${s.name}")
                )
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(if (state == ChecklistState.BLOCKED || state == ChecklistState.NOT_APPLICABLE) "Note (required)" else "Note (optional)") },
            modifier = Modifier.fillMaxWidth().testTag("note_${entry.id}")
        )
        if (error != null) {
            Text(error, color = SakarColors.Danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("error_${entry.id}"))
        }
        AssistantButton(
            "Save item", onClick = { onSave(entry.id, state, note) },
            icon = Icons.Filled.Save, enabled = changed,
            modifier = Modifier.testTag("save_${entry.id}")
        )
        if (!changed) {
            Text("No changes to save.", style = MaterialTheme.typography.bodySmall, color = SakarColors.Faint)
        }
    }
}
