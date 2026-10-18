package com.sakarrobotics.installationassistant.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.logic.ValidationLogic
import com.sakarrobotics.installation.core.logic.WizardLogic
import com.sakarrobotics.installation.core.model.Floor
import com.sakarrobotics.installation.core.model.Installation
import com.sakarrobotics.installation.core.model.SimMap
import com.sakarrobotics.installation.core.model.SimulationScenario
import com.sakarrobotics.installation.core.model.StepStatus
import com.sakarrobotics.installation.core.model.ValidationResult
import com.sakarrobotics.installation.core.model.WizardStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WizardViewModel(private val services: AssistantServices) : ViewModel() {

    val installation: StateFlow<Installation?> = services.installations.current
    val floors: StateFlow<List<Floor>> = services.floors.floors
    val maps: StateFlow<List<SimMap>> = services.mapping.maps
    val scenario: StateFlow<SimulationScenario> = services.diagnostics.scenario

    val statuses: StateFlow<List<StepStatus>> = combine(installation, floors, maps) { inst, f, m ->
        WizardLogic.statuses(inst, f, m)
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        WizardLogic.statuses(installation.value, floors.value, maps.value)
    )

    val validation: StateFlow<ValidationResult> = combine(installation, floors, maps) { inst, f, m ->
        ValidationLogic.validate(inst, f, m)
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        ValidationLogic.validate(installation.value, floors.value, maps.value)
    )

    private val _step = MutableStateFlow(initialStep())
    val step: StateFlow<WizardStep> = _step.asStateFlow()

    private val _readinessRunning = MutableStateFlow(false)
    val readinessRunning: StateFlow<Boolean> = _readinessRunning.asStateFlow()

    private val _sensorsRunning = MutableStateFlow(false)
    val sensorsRunning: StateFlow<Boolean> = _sensorsRunning.asStateFlow()

    private fun initialStep(): WizardStep {
        if (installation.value == null) return WizardStep.OVERVIEW
        val s = WizardLogic.statuses(installation.value, floors.value, maps.value)
        return WizardStep.values()[WizardLogic.firstIncomplete(s)]
    }

    // -------------------------------------------------------------- navigation

    fun startNew() {
        services.installations.startNew()
        _step.value = WizardStep.OVERVIEW
    }

    fun go(target: WizardStep) {
        if (WizardLogic.canEnter(target, statuses.value)) _step.value = target
    }

    fun next() {
        val current = _step.value
        if (statuses.value[current.index].isComplete) WizardLogic.nextStep(current)?.let { go(it) }
    }

    fun back() {
        WizardLogic.previousStep(_step.value)?.let { _step.value = it }
    }

    // ----------------------------------------------------------------- actions

    fun acknowledgeOverview() = services.installations.acknowledgeOverview()

    fun setScenario(s: SimulationScenario) = services.diagnostics.setScenario(s)

    fun runReadiness() {
        if (_readinessRunning.value) return
        _readinessRunning.value = true
        viewModelScope.launch {
            try {
                services.installations.recordReadiness(services.diagnostics.runReadiness())
            } finally {
                _readinessRunning.value = false
            }
        }
    }

    fun runSensors() {
        if (_sensorsRunning.value) return
        _sensorsRunning.value = true
        viewModelScope.launch {
            try {
                services.installations.recordSensors(services.diagnostics.runSensors())
            } finally {
                _sensorsRunning.value = false
            }
        }
    }

    fun toggleFloor(floorId: String) {
        val selected = installation.value?.selectedFloorIds.orEmpty()
        services.installations.setSelectedFloors(
            if (floorId in selected) selected - floorId else selected + floorId
        )
    }

    fun runValidation() = services.installations.markValidationRun()

    fun generateReport() {
        services.generateReport()
    }
}
