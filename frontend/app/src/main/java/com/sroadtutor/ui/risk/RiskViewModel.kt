package com.sroadtutor.ui.risk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.RiskAggregateResponse
import com.sroadtutor.data.remote.models.RiskScoreResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RiskViewModel(private val apiService: ApiService) : ViewModel() {

    private val _aggregate = MutableStateFlow<RiskAggregateResponse?>(null)
    val aggregate = _aggregate.asStateFlow()

    private val _studentScore = MutableStateFlow<RiskScoreResponse?>(null)
    val studentScore = _studentScore.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadAggregate() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getRiskAggregate()
                if (response.isSuccessful) {
                    _aggregate.value = response.body()?.data
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateScore(studentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.generateRiskScore(studentId)
                if (response.isSuccessful) {
                    _studentScore.value = response.body()?.data
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadByHash(hash: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getRiskScoreByHash(hash)
                if (response.isSuccessful) {
                    _studentScore.value = response.body()?.data
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
