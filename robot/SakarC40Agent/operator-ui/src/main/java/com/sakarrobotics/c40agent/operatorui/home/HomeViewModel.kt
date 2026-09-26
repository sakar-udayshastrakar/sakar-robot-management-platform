package com.sakarrobotics.c40agent.operatorui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CleaningRunState
import com.sakarrobotics.c40agent.domain.model.CleaningSession
import com.sakarrobotics.c40agent.domain.model.CloudSyncState
import com.sakarrobotics.c40agent.domain.model.DockingState
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val linkState: RobotLinkState = RobotLinkState.DISCONNECTED,
    val cloudSyncState: CloudSyncState = CloudSyncState.OFFLINE,
    val battery: BatteryState = BatteryState(null, false, null, null, Capability.REAL),
    val docking: DockingState = DockingState(false, false, Capability.GATED),
    val session: CleaningSession? = null,
    val isConnecting: Boolean = false,
    val lastActionMessage: String? = null
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        container.connectionRepository.linkState,
        container.connectionRepository.cloudSyncState,
        container.batteryRepository.battery,
        container.chargingRepository.dockingState,
        container.cleaningRepository.session
    ) { link, cloud, battery, docking, session ->
        HomeUiState(linkState = link, cloudSyncState = cloud, battery = battery, docking = docking, session = session)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    init {
        refreshBattery()
    }

    fun connect() {
        viewModelScope.launch {
            container.connectionRepository.connect().onFailure {
                _messages.value = "Connection failed: ${it.message}"
            }
            refreshBattery()
        }
    }

    fun refreshBattery() {
        viewModelScope.launch { container.batteryRepository.refresh() }
    }

    fun pauseCleaning() = viewModelScope.launch { container.cleaningRepository.pause() }
    fun resumeCleaning() = viewModelScope.launch { container.cleaningRepository.resume() }
    fun stopCleaning() = viewModelScope.launch { container.cleaningRepository.stop() }

    fun returnToDock() {
        viewModelScope.launch {
            container.chargingRepository.returnToDock().onFailure {
                _messages.value = "Return to dock unavailable: ${it.message}"
            }
        }
    }

    fun consumeMessage() {
        _messages.value = null
    }

    val isCleaningActive: Boolean
        get() = uiState.value.session?.state == CleaningRunState.CLEANING
}
