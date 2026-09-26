package com.sakarrobotics.c40agent.operatorui.settings

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.ConsumableItem
import com.sakarrobotics.c40agent.domain.usecase.ConsumableUseCases
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConsumablesViewModel(container: AppContainer) : ViewModel() {
    private val useCases = ConsumableUseCases(container.consumableRepository)
    val items: StateFlow<List<ConsumableItem>> =
        useCases.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markReplaced(id: String) = viewModelScope.launch { useCases.markReplaced(id) }
}

@Composable
fun ConsumablesScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ConsumablesViewModel = viewModel(factory = viewModelFactory { initializer { ConsumablesViewModel(container) } })
    val items by viewModel.items.collectAsState()

    Scaffold(topBar = { SakarTopBar("Consumables", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            InlineBanner("Remaining life is operator-tracked from cleaning runtime, not a wear sensor - the vendored SDK exposes none.", isWarning = false)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { item ->
                    SectionCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { viewModel.markReplaced(item.id) }) { Text("Mark Replaced") }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { item.remainingFraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (item.isReplacementDue) SakarColors.Danger else SakarColors.Primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Lifespan: ${item.lifespanHours}h · Estimated remaining: ${item.remainingHours}h",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SakarColors.TextFaint
                        )
                        if (item.isReplacementDue) {
                            Text("Replacement recommended", color = SakarColors.Danger, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
