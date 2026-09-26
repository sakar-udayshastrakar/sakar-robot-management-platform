package com.sakarrobotics.c40agent.operatorui.superuser

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.sakarrobotics.c40agent.domain.model.LogExportRequest
import com.sakarrobotics.c40agent.domain.model.LogRecord
import com.sakarrobotics.c40agent.operatorui.common.EmptyState
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlinx.coroutines.launch

class LogsViewModel(private val container: AppContainer) : ViewModel() {
    var entries by androidx.compose.runtime.mutableStateOf<List<LogRecord>>(emptyList())
        private set
    var exportMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun refresh() {
        entries = container.logsRepository.tail(200)
    }

    init {
        refresh()
        viewModelScope.launch {
            container.logsRepository.liveLog.collect { refresh() }
        }
    }

    fun exportAll() {
        viewModelScope.launch {
            val result = container.logsRepository.export(LogExportRequest(null, null))
            exportMessage = if (result.success) "Exported ${result.entryCount} entries to ${result.destinationDescription}" else "Export failed: ${result.destinationDescription}"
        }
    }

    fun clear() {
        container.logsRepository.clear()
        refresh()
    }
}

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: LogsViewModel = viewModel(factory = viewModelFactory { initializer { LogsViewModel(container) } })

    Scaffold(topBar = { SakarTopBar("Logs", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::exportAll) { Text("Export All") }
                OutlinedButton(onClick = viewModel::clear) { Text("Clear") }
            }
            viewModel.exportMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = SakarColors.TextFaint, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            if (viewModel.entries.isEmpty()) {
                EmptyState("No log entries yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = true).background(SakarColors.ConsoleBackground).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(viewModel.entries.reversed()) { entry ->
                        Text(entry.message, color = SakarColors.ConsoleText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
