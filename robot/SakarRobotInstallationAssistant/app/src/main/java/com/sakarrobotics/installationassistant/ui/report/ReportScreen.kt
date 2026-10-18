package com.sakarrobotics.installationassistant.ui.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.logic.ReportBuilder
import com.sakarrobotics.installation.core.model.InstallationReport
import com.sakarrobotics.installationassistant.AssistantApp
import com.sakarrobotics.installationassistant.export.ReportExporter
import com.sakarrobotics.installationassistant.export.ReportSharer
import com.sakarrobotics.installationassistant.ui.components.LoadingBlock
import com.sakarrobotics.installationassistant.ui.Feedback
import com.sakarrobotics.installationassistant.ui.LocalServices
import com.sakarrobotics.installationassistant.ui.components.AssistantButton
import com.sakarrobotics.installationassistant.ui.components.AssistantScaffold
import com.sakarrobotics.installationassistant.ui.components.ButtonKind
import com.sakarrobotics.installationassistant.ui.components.ConfirmDialog
import com.sakarrobotics.installationassistant.ui.components.EmptyState
import com.sakarrobotics.installationassistant.ui.components.MessageBanner
import com.sakarrobotics.installationassistant.ui.components.SectionCard
import com.sakarrobotics.installationassistant.ui.components.StatusChip
import com.sakarrobotics.installationassistant.ui.components.Tone
import com.sakarrobotics.installationassistant.ui.theme.SakarColors
import com.sakarrobotics.installationassistant.ui.toFeedback
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReportViewModel(
    private val services: AssistantServices,
    private val exporter: ReportExporter
) : ViewModel() {
    val reports: StateFlow<List<InstallationReport>> = services.reports.reports

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    fun select(id: String) {
        _selectedId.value = id
    }

    fun generate() {
        val report = services.generateReport()
        _selectedId.value = report.id
        _feedback.value = Feedback("Generated report ${report.id} (SIMULATION ONLY).", Tone.SUCCESS)
    }

    fun delete(id: String) {
        _feedback.value = services.reports.delete(id).toFeedback("Deleted the report.")
        if (_selectedId.value == id) _selectedId.value = null
    }

    fun render(report: InstallationReport): String = services.reports.render(report)

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    /**
     * Creates the PDF off the main thread, then hands it to [share] (the system share sheet, or a test double).
     * Every failure becomes a visible message; nothing is thrown at the user.
     */
    fun exportPdf(report: InstallationReport, share: (File) -> Result<Unit>) {
        if (_exporting.value) return
        _exporting.value = true
        _feedback.value = null
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) { exporter.export(report) }
            _feedback.value = created.fold(
                onSuccess = { pdf ->
                    share(pdf.file).fold(
                        onSuccess = { Feedback("PDF created (${pdf.render.pageCount} page(s)). Choose where to send it.", Tone.SUCCESS) },
                        onFailure = { Feedback("The PDF was created but could not be shared: ${it.message}", Tone.DANGER) }
                    )
                },
                onFailure = { Feedback(it.message ?: "The PDF could not be created.", Tone.DANGER) }
            )
            _exporting.value = false
        }
    }

    fun dismissFeedback() {
        _feedback.value = null
    }
}

@Composable
fun ReportScreen(onBack: () -> Unit) {
    val services = LocalServices.current
    val context = LocalContext.current
    val vm: ReportViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ReportViewModel(services, ReportExporter(File(context.applicationContext.cacheDir, "reports"))) }
        }
    )
    val exporting by vm.exporting.collectAsState()
    val reports by vm.reports.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val feedback by vm.feedback.collectAsState()
    var confirmDelete by remember { mutableStateOf<InstallationReport?>(null) }

    val selected = reports.firstOrNull { it.id == selectedId } ?: reports.firstOrNull()

    AssistantScaffold(title = "Installation Report", onBack = onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MessageBanner(
                "SIMULATION ONLY - reports are generated from demo data and are not evidence of a real installation.",
                Tone.WARNING, Modifier.testTag("report_sim_notice")
            )
            feedback?.let { MessageBanner(it.text, it.tone, Modifier.testTag("report_feedback"), onDismiss = vm::dismissFeedback) }

            AssistantButton(
                if (reports.isEmpty()) "Generate report" else "Generate new report",
                onClick = vm::generate, icon = Icons.Filled.NoteAdd,
                modifier = Modifier.testTag("btn_generate_report")
            )

            if (selected == null) {
                EmptyState(
                    Icons.Filled.Description, "No reports yet",
                    "Generate a report to summarise the current simulated installation: floors, simulated mapping status, checklist results and outstanding warnings."
                )
            } else {
                if (reports.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reports.forEach { r ->
                            FilterChip(
                                selected = r.id == selected.id,
                                onClick = { vm.select(r.id) },
                                label = { Text("${r.id} - ${ReportBuilder.formatTimestamp(r.generatedAtMillis)}") }
                            )
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantButton(
                        if (exporting) "Creating PDF..." else "Export PDF and share",
                        onClick = {
                            vm.exportPdf(selected) { file ->
                                (AssistantApp.shareOverride ?: ReportSharer::share).invoke(context, file)
                            }
                        },
                        icon = Icons.Filled.PictureAsPdf, enabled = !exporting,
                        modifier = Modifier.testTag("btn_export_pdf")
                    )
                    AssistantButton("Copy report text", onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Installation report (simulation only)", vm.render(selected)))
                        Toast.makeText(context, "Report text copied", Toast.LENGTH_SHORT).show()
                    }, kind = ButtonKind.SECONDARY, icon = Icons.Filled.ContentCopy, modifier = Modifier.testTag("btn_copy_report"))
                    AssistantButton("Delete report", onClick = { confirmDelete = selected }, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Delete, modifier = Modifier.testTag("btn_delete_report"))
                }
                if (exporting) LoadingBlock("Creating the PDF...", Modifier.testTag("report_pdf_loading"))
                ReportView(selected)
            }
        }
    }

    confirmDelete?.let { report ->
        ConfirmDialog(
            title = "Delete report ${report.id}?",
            text = "This removes the saved report from this tablet. It cannot be undone.",
            confirmLabel = "Delete report", danger = true,
            onConfirm = { vm.delete(report.id); confirmDelete = null },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
fun ReportView(report: InstallationReport) {
    Column(Modifier.testTag("report_view"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = "Installation ${report.installationId}", trailing = { StatusChip("SIMULATION ONLY", Tone.BRAND) }) {
            Text("Report ID: ${report.id}", style = MaterialTheme.typography.bodyMedium)
            Text("Completion: ${report.completionStatus ?: ReportBuilder.NOT_RECORDED}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("report_completion"))
            Text("Generated: ${ReportBuilder.formatTimestamp(report.generatedAtMillis)}", style = MaterialTheme.typography.bodyMedium)
            Text(report.mappingStatus, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("report_mapping_status"))
            Text(report.diagnosticsSummary, style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(title = "Selected floors") {
            if (report.floors.isEmpty()) Text("No floors selected.", color = SakarColors.Muted)
            report.floors.forEach { floor ->
                Text(floor.name, style = MaterialTheme.typography.titleSmall)
                if (floor.maps.isEmpty()) Text("  No saved simulated map", color = SakarColors.Muted)
                floor.maps.forEach {
                    Text("  Map \"${it.name}\": ${it.status}, ${it.coveragePercent}% coverage", style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
                }
            }
        }
        SectionCard(title = "Checklist results") {
            report.checklist.forEach {
                val note = if (it.note.isBlank()) "" else " - ${it.note}"
                Text("[${it.state}] ${it.title} (${if (it.required) "required" else "optional"})$note", style = MaterialTheme.typography.bodyMedium)
            }
        }
        SectionCard(title = "Outstanding warnings") {
            if (report.warnings.isEmpty()) Text("None.", color = SakarColors.Muted)
            report.warnings.forEach { Text("- $it", style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted) }
        }
        MessageBanner(report.simulationNotice, Tone.WARNING, Modifier.testTag("report_notice"))
    }
}
