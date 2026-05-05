package com.sroadtutor.ui.insurance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InsuranceViewModel(private val apiService: ApiService) : ViewModel() {

    private val _brokers = MutableStateFlow<List<InsuranceBrokerResponse>>(emptyList())
    val brokers = _brokers.asStateFlow()

    private val _leads = MutableStateFlow<List<InsuranceLeadResponse>>(emptyList())
    val leads = _leads.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadBrokers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getInsuranceBrokers()
                if (response.isSuccessful) {
                    _brokers.value = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSchoolLeads(schoolId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getSchoolInsuranceLeads(schoolId)
                if (response.isSuccessful) {
                    _leads.value = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markQuoted(id: String, schoolId: String) {
        viewModelScope.launch {
            try {
                if (apiService.markInsuranceQuoted(id).isSuccessful) {
                    loadSchoolLeads(schoolId)
                }
            } catch (e: Exception) {}
        }
    }

    fun convertLead(id: String, schoolId: String) {
        viewModelScope.launch {
            try {
                if (apiService.markInsuranceConverted(id).isSuccessful) {
                    loadSchoolLeads(schoolId)
                }
            } catch (e: Exception) {}
        }
    }

    fun resetError() { _error.value = null }
}
