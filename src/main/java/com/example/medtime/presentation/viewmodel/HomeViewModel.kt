package com.example.medtime.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.medtime.presentation.data.alarm.DoseAlarmScheduler
import com.example.medtime.presentation.data.model.DashboardData
import com.example.medtime.presentation.data.model.DoseAction
import com.example.medtime.presentation.data.remote.DeviceCredentialRejectedException
import com.example.medtime.presentation.data.repository.DoseRepository
import com.example.medtime.presentation.data.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val data: DashboardData) : HomeUiState
    data class Error(val message: String) : HomeUiState
    data object CredentialInvalid : HomeUiState
}

class HomeViewModel(
    private val repository: MedicationRepository,
    private val doseRepository: DoseRepository,
    private val alarmScheduler: DoseAlarmScheduler
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loadDashboard()
        }
    }

    fun scheduleTestAlarm() {
        alarmScheduler.scheduleTestAlarm()
    }

    fun applyNextDoseAction(action: DoseAction, postponeMinutes: Int? = null) {
        val doseId = (_state.value as? HomeUiState.Ready)?.data?.nextDose?.id ?: return
        applyDoseAction(doseId, action, postponeMinutes)
    }

    fun applyDoseAction(doseId: String, action: DoseAction, postponeMinutes: Int? = null) {
        if (_actionInProgress.value) return
        if (action == DoseAction.POSTPONE && postponeMinutes !in ALLOWED_POSTPONE_MINUTES) return
        if (action != DoseAction.POSTPONE && postponeMinutes != null) return

        viewModelScope.launch {
            _actionInProgress.value = true
            try {
                doseRepository.applyAction(doseId, action, postponeMinutes)
                alarmScheduler.cancel(doseId)
                loadDashboard()
            } catch (credentialError: DeviceCredentialRejectedException) {
                _state.value = HomeUiState.CredentialInvalid
            } catch (error: Exception) {
                _state.value = HomeUiState.Error(
                    error.message?.takeIf(String::isNotBlank)
                        ?: "No se pudo actualizar la dosis"
                )
            } finally {
                _actionInProgress.value = false
            }
        }
    }

    private suspend fun loadDashboard() {
        _state.value = HomeUiState.Loading
        try {
            val dashboard = repository.loadDashboard()
            alarmScheduler.reschedule(dashboard.pendingDoses + dashboard.upcomingDoses)
            _state.value = HomeUiState.Ready(dashboard)
        } catch (credentialError: DeviceCredentialRejectedException) {
            _state.value = HomeUiState.CredentialInvalid
        } catch (error: Exception) {
            _state.value = HomeUiState.Error(
                error.message?.takeIf(String::isNotBlank)
                    ?: "No se pudo cargar tu medicación"
            )
        }
    }

    class Factory(
        private val repository: MedicationRepository,
        private val doseRepository: DoseRepository,
        private val alarmScheduler: DoseAlarmScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            return HomeViewModel(repository, doseRepository, alarmScheduler) as T
        }
    }

    private companion object {
        val ALLOWED_POSTPONE_MINUTES = setOf(15, 30, 60, 120)
    }
}
