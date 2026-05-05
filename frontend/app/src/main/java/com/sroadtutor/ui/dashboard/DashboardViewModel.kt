package com.sroadtutor.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.DashboardResponse
import com.sroadtutor.data.remote.models.TelemetryDatasetSummary
import com.sroadtutor.data.remote.models.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val apiService: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _telemetrySummary = MutableStateFlow<TelemetryDatasetSummary?>(null)
    val telemetrySummary = _telemetrySummary.asStateFlow()

    fun loadDashboard(role: UserRole?) {
        if (role != UserRole.OWNER) {
            // For now, if not an owner, we don't have a specialized dashboard.
            // We can show a simple welcome or success state with null data if we update the UI.
            // But to avoid 403s, we just stop here or set a specific state.
            _uiState.value = DashboardUiState.NotAuthorized
            return
        }

        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            try {
                val response = apiService.getOwnerDashboard()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.data != null) {
                        _uiState.value = DashboardUiState.Success(body.data)
                    } else {
                        _uiState.value = DashboardUiState.Error(body?.message ?: "Failed to load dashboard")
                    }
                } else if (response.code() == 403) {
                    _uiState.value = DashboardUiState.NotAuthorized
                } else {
                    _uiState.value = DashboardUiState.Error("Error ${response.code()}: ${response.message()}")
                }
                
                if (role == UserRole.OWNER) {
                    val telResponse = apiService.getTelemetrySummary()
                    if (telResponse.isSuccessful) {
                        _telemetrySummary.value = telResponse.body()?.data
                    }
                }
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "An error occurred")
            }
        }
    }
}

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val data: DashboardResponse) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
    data object NotAuthorized : DashboardUiState
}
