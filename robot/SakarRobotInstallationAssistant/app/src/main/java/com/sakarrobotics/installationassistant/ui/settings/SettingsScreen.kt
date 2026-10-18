package com.sakarrobotics.installationassistant.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.backup.BackupPreview
import com.sakarrobotics.installation.core.backup.RestoreMode
import com.sakarrobotics.installation.core.logic.ReportBuilder
import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installationassistant.AssistantApp
import com.sakarrobotics.installationassistant.export.DocumentIo
import com.sakarrobotics.installationassistant.ui.Feedback
import com.sakarrobotics.installationassistant.ui.LocalServices
import com.sakarrobotics.installationassistant.ui.components.AssistantButton
import com.sakarrobotics.installationassistant.ui.components.AssistantScaffold
import com.sakarrobotics.installationassistant.ui.components.ButtonKind
import com.sakarrobotics.installationassistant.ui.components.ConfirmDialog
import com.sakarrobotics.installationassistant.ui.components.LoadingBlock
import com.sakarrobotics.installationassistant.ui.components.MessageBanner
import com.sakarrobotics.installationassistant.ui.components.SectionCard
import com.sakarrobotics.installationassistant.ui.components.SimulatedTag
import com.sakarrobotics.installationassistant.ui.components.Tone
import com.sakarrobotics.installationassistant.ui.theme.SakarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A backup file that passed validation and is waiting for the user to choose how to restore it. */
class PendingRestore(val json: String, val preview: BackupPreview)

class SettingsViewModel(
    private val services: AssistantServices,
    private val io: DocumentIo,
    private val appVersion: String
) : ViewModel() {

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    private val _pending = MutableStateFlow<PendingRestore?>(null)
    val pending: StateFlow<PendingRestore?> = _pending.asStateFlow()

    fun suggestedBackupName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "sakar-installation-backup-$stamp.json"
    }

    fun dismissFeedback() {
        _feedback.value = null
    }

    fun exportTo(uri: Uri?) {
        if (uri == null) return // the user left the file picker
        if (_busy.value != null) return
        _busy.value = "Saving backup..."
        viewModelScope.launch(Dispatchers.IO) {
            val text = services.backup.export(appVersion)
            val written = io.writeText(uri, text)
            _feedback.value = written.fold(
                onSuccess = {
                    Feedback(
                        "Backup saved. It holds ${services.floors.floors.value.size} floor(s), " +
                            "${services.mapping.maps.value.size} simulated map(s) and ${services.reports.reports.value.size} report(s) " +
                            "- demo data only.",
                        Tone.SUCCESS
                    )
                },
                onFailure = { Feedback("The backup could not be saved: ${it.message}", Tone.DANGER) }
            )
            _busy.value = null
        }
    }

    fun readBackup(uri: Uri?) {
        if (uri == null) return
        if (_busy.value != null) return
        _busy.value = "Checking backup file..."
        viewModelScope.launch(Dispatchers.IO) {
            val read = io.readText(uri)
            val text = read.getOrNull()
            if (text == null) {
                _feedback.value = Feedback("Nothing was changed. ${read.exceptionOrNull()?.message}", Tone.DANGER)
            } else {
                when (val inspected = services.backup.inspect(text)) {
                    is Outcome.Success -> {
                        _feedback.value = null
                        _pending.value = PendingRestore(text, inspected.value)
                    }
                    is Outcome.Failure -> _feedback.value = Feedback(
                        "Nothing was changed. ${inspected.message}", Tone.DANGER
                    )
                }
            }
            _busy.value = null
        }
    }

    fun cancelRestore() {
        _pending.value = null
    }

    fun restore(mode: RestoreMode) {
        val pending = _pending.value ?: return
        _pending.value = null
        _feedback.value = when (val result = services.backup.restore(pending.json, mode)) {
            is Outcome.Success -> Feedback(result.value.describe(), Tone.SUCCESS)
            is Outcome.Failure -> Feedback("Nothing was changed. ${result.message}", Tone.DANGER)
        }
    }

    fun resetDemoData() {
        services.backup.resetDemoData()
        _feedback.value = Feedback("All demo data was deleted. Floors are back to the defaults.", Tone.SUCCESS)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val services = LocalServices.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(services, DocumentIo(appContext.contentResolver), AssistantApp.versionName(appContext))
            }
        }
    )
    val busy by vm.busy.collectAsState()
    val feedback by vm.feedback.collectAsState()
    val pending by vm.pending.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.exportTo(it) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.readBackup(it) }

    AssistantScaffold(title = "Settings & About", onBack = onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            feedback?.let { MessageBanner(it.text, it.tone, Modifier.testTag("settings_feedback"), onDismiss = vm::dismissFeedback) }
            busy?.let { LoadingBlock(it, Modifier.testTag("settings_busy")) }

            AboutCard(AssistantApp.versionName(appContext))

            SectionCard(title = "Backup and restore") {
                Text(
                    "Save the installation data on this tablet to a file you choose, or restore it from one. " +
                        "Backups contain demo data only. The simulated maps are Sakar demo maps and are not compatible " +
                        "with Keenon or any real robot map format.",
                    style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistantButton(
                        "Export backup", { createBackup.launch(vm.suggestedBackupName()) },
                        icon = Icons.Filled.FileUpload, enabled = busy == null, modifier = Modifier.testTag("btn_export_backup")
                    )
                    AssistantButton(
                        "Restore from file", { openBackup.launch(arrayOf("*/*")) },
                        kind = ButtonKind.SECONDARY, icon = Icons.Filled.FileDownload, enabled = busy == null,
                        modifier = Modifier.testTag("btn_restore_backup")
                    )
                }
                Text(
                    "Restoring never changes anything until the file has been checked. You choose between merging " +
                        "(adds what is missing, keeps everything else) and replacing all current data.",
                    style = MaterialTheme.typography.bodySmall, color = SakarColors.Faint
                )
            }

            SectionCard(title = "Reset demo data") {
                Text(
                    "Deletes everything this app has saved on this tablet: floors (back to Ground floor and First " +
                        "floor), simulated maps, the current installation with its checklist and wizard progress, saved " +
                        "reports and the diagnostics scenario. Backup files and PDFs you already exported are not affected.",
                    style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted
                )
                AssistantButton(
                    "Reset demo data", { confirmReset = true },
                    kind = ButtonKind.DANGER, icon = Icons.Filled.DeleteForever, modifier = Modifier.testTag("btn_reset_data")
                )
            }
        }
    }

    pending?.let { p ->
        RestoreDialog(
            preview = p.preview,
            onMerge = { vm.restore(RestoreMode.MERGE) },
            onReplace = { confirmReplace = true },
            onCancel = vm::cancelRestore
        )
    }
    if (confirmReplace) {
        ConfirmDialog(
            title = "Replace all current data?",
            text = "Everything currently saved on this tablet (floors, simulated maps, the current installation and " +
                "its checklist, and saved reports) will be deleted and replaced by the contents of the backup. " +
                "This cannot be undone.",
            confirmLabel = "Replace all data", danger = true,
            onConfirm = { confirmReplace = false; vm.restore(RestoreMode.REPLACE) },
            onDismiss = { confirmReplace = false }
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Delete all demo data?",
            text = "This deletes all floors you added, every simulated map, the current installation (checklist and " +
                "wizard progress) and all saved reports from this tablet. The two default floors come back. " +
                "Exported backups and PDFs are not affected. This cannot be undone.",
            confirmLabel = "Delete everything", danger = true,
            onConfirm = { confirmReset = false; vm.resetDemoData() },
            onDismiss = { confirmReset = false }
        )
    }
}

@Composable
private fun AboutCard(version: String) {
    SectionCard(title = "About", modifier = Modifier.testTag("about_card"), trailing = { SimulatedTag() }) {
        InfoRow("App", "Sakar Robotics Robot Installation Assistant", "about_name")
        InfoRow("Version", version, "about_version")
        InfoRow("Mode", "SIMULATED backend", "about_mode")
        MessageBanner(
            "Physical robot integration is not yet enabled. This version cannot connect to a robot, a ROS computer or " +
                "any network, and it asks for no network permission. All mapping, sensor and diagnostic results are " +
                "generated by a local simulation and are not evidence of a real installation.",
            Tone.WARNING, Modifier.testTag("about_notice")
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, tag: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Faint, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun RestoreDialog(preview: BackupPreview, onMerge: () -> Unit, onReplace: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("restore_dialog"),
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The file is a valid backup" +
                        (if (preview.exportedAtMillis > 0) " made ${ReportBuilder.formatTimestamp(preview.exportedAtMillis)}" else "") +
                        (preview.appVersion?.let { " by app version $it" } ?: "") + ".",
                    modifier = Modifier.testTag("restore_summary")
                )
                Text(
                    "${preview.floorCount} floor(s), ${preview.mapCount} simulated map(s), ${preview.reportCount} report(s)" +
                        (preview.installationId?.let { ", installation $it" } ?: ", no installation record") + ".",
                    modifier = Modifier.testTag("restore_counts")
                )
                Text(
                    "Merge adds what is missing and keeps everything you already have. Replace deletes your current data first.",
                    style = MaterialTheme.typography.bodySmall, color = SakarColors.Muted
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistantButton("Merge into current data", onMerge, modifier = Modifier.testTag("btn_restore_merge"))
                AssistantButton("Replace all current data", onReplace, kind = ButtonKind.DANGER, modifier = Modifier.testTag("btn_restore_replace"))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("btn_restore_cancel")) { Text("Cancel") }
        }
    )
}
