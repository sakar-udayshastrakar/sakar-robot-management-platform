package com.sakarrobotics.c40agent.operatorui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CleaningRunState
import com.sakarrobotics.c40agent.domain.model.CleaningSession
import com.sakarrobotics.c40agent.domain.model.CloudSyncState
import com.sakarrobotics.c40agent.domain.model.ConnectionDisplayState
import com.sakarrobotics.c40agent.domain.model.deriveConnectionDisplay
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
    val battery: BatteryState = BatteryState(null, null, null, null, Capability.UNKNOWN),
    val docking: DockingState = DockingState(false, false, Capability.GATED),
    val session: CleaningSession? = null,
    val isConnecting: Boolean = false,
    val lastActionMessage: String? = null,
    /** Wall-clock millis of the last successful SDK exchange; null = none yet (see deriveConnectionDisplay). */
    val lastCommunicationMillis: Long? = null,
    val appVersion: String? = null
) {
    val connection: ConnectionDisplayState
        get() = deriveConnectionDisplay(linkState, lastCommunicationMillis)
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _appVersion = MutableStateFlow<String?>(null)

    private val robotLink = combine(
        container.connectionRepository.linkState,
        container.connectionRepository.lastCommunicationMillis
    ) { link, lastComm -> link to lastComm }

    val uiState: StateFlow<HomeUiState> = combine(
        robotLink,
        container.connectionRepository.cloudSyncState,
        container.batteryRepository.battery,
        container.chargingRepository.dockingState,
        container.cleaningRepository.session
    ) { (link, lastComm), cloud, battery, docking, session ->
        HomeUiState(
            linkState = link,
            cloudSyncState = cloud,
            battery = battery,
            docking = docking,
            session = session,
            lastCommunicationMillis = lastComm
        )
    }.combine(_appVersion) { state, version -> state.copy(appVersion = version) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    init {
        refreshBattery()
        viewModelScope.launch {
            // Local identity lookup only (build version string); no robot request is made.
            _appVersion.value = runCatching { container.identityRepository.identity().appVersionName }.getOrNull()
        }
    }

    fun connect() {
        viewModelScope.launch {
            container.connectionRepository.connect().onFailure {
                _messages.value = "Connection failed: ${it.message}"
                container.eventLogger.warn("Home", "Connect attempt failed: ${it.message}")
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
                container.eventLogger.warn("Home", "Return to dock request did not succeed: ${it.message}")
            }
        }
    }

    fun consumeMessage() {
        _messages.value = null
    }

    val isCleaningActive: Boolean
        get() = uiState.value.session?.state == CleaningRunState.CLEANING
}
