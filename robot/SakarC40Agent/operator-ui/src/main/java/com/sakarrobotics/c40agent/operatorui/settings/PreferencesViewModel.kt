package com.sakarrobotics.c40agent.operatorui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.LocalPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared by every settings screen backed by [com.sakarrobotics.c40agent.domain.repository.LocalPreferencesRepository]. */
class PreferencesViewModel(private val container: AppContainer) : ViewModel() {
    val preferences: StateFlow<LocalPreferences> =
        container.localPreferencesRepository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalPreferences())

    fun update(transform: (LocalPreferences) -> LocalPreferences) {
        viewModelScope.launch { container.localPreferencesRepository.update(transform) }
    }
}
