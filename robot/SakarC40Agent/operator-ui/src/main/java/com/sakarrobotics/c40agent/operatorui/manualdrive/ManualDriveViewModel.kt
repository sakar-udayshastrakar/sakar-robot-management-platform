package com.sakarrobotics.c40agent.operatorui.manualdrive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.ManualDriveBackendMode
import com.sakarrobotics.c40agent.domain.model.ManualDriveDirection
import com.sakarrobotics.c40agent.domain.model.SimulatedRobotState
import com.sakarrobotics.c40agent.domain.usecase.ManualDriveUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Each press sends exactly one command (to whichever backend - REAL or SIMULATED - the container
 * selected, see backendMode) and each release sends exactly one STOP. For the REAL backend there
 * is no verified evidence that forward()/backward()/turnLeft()/turnRight() are "move until told
 * otherwise" versus "move for an implicit vendor-side duration", so this deliberately does not
 * repeat commands while held (see safety rule "do not continuously send movement commands"). The
 * SIMULATED backend is explicitly specified to move continuously between press and release - see
 * SimulatedMotorController.
 */
class ManualDriveViewModel(private val container: AppContainer) : ViewModel() {

    private val useCases = ManualDriveUseCases(container.navigationRepository)

    /** Which backend is actually being driven right now - see RobotMotionController (:data). */
    val backendMode: ManualDriveBackendMode = container.manualDriveBackendMode

    /** Live virtual-robot telemetry. Only changes while backendMode == SIMULATED. */
    val simulatedState: StateFlow<SimulatedRobotState> = container.simulatedRobotState

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    fun onPress(direction: ManualDriveDirection) {
        _isSending.value = true
        viewModelScope.launch {
            useCases.jog(direction, speedLevel = 2)
                .onSuccess { _statusMessage.value = null }
                .onFailure { _statusMessage.value = it.message ?: "Command failed" }
            _isSending.value = false
        }
    }

    fun onRelease() {
        viewModelScope.launch {
            useCases.emergencyStop().onFailure { _statusMessage.value = it.message ?: "Stop failed" }
        }
    }

    /** Safety rule: issue STOP when leaving Manual Drive, where the verified API supports it. */
    fun onLeaveManualDrive() {
        viewModelScope.launch { useCases.emergencyStop() }
    }

    fun consumeMessage() {
        _statusMessage.value = null
    }
}
