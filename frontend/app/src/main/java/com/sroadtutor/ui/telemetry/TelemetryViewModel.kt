package com.sroadtutor.ui.telemetry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.AttachTelemetryRequest
import com.sroadtutor.data.remote.models.TelemetryDatasetSummary
import com.sroadtutor.data.remote.models.TelemetryEventResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TelemetryViewModel(private val apiService: ApiService) : ViewModel() {

    private val _mistakeEvents = MutableStateFlow<List<TelemetryEventResponse>>(emptyList())
    val mistakeEvents = _mistakeEvents.asStateFlow()

    private val _summary = MutableStateFlow<TelemetryDatasetSummary?>(null)
    val summary = _summary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadMistakeEvents(mistakeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getMistakeTelemetry(mistakeId)
                if (response.isSuccessful) {
                    _mistakeEvents.value = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSummary() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getTelemetrySummary()
                if (response.isSuccessful) {
                    _summary.value = response.body()?.data
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun attachTelemetry(mistakeId: String, request: AttachTelemetryRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.attachTelemetry(mistakeId, request)
                if (response.isSuccessful) {
                    onSuccess()
                    loadMistakeEvents(mistakeId)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetError() { _error.value = null }
}
