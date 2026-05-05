package com.sroadtutor.ui.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MarketplaceViewModel(private val apiService: ApiService) : ViewModel() {

    private val _dealerships = MutableStateFlow<List<DealershipResponse>>(emptyList())
    val dealerships = _dealerships.asStateFlow()

    private val _leads = MutableStateFlow<List<DealershipLeadResponse>>(emptyList())
    val leads = _leads.asStateFlow()

    private val _myLeads = MutableStateFlow<List<DealershipLeadResponse>>(emptyList())
    val myLeads = _myLeads.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadDealerships() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getDealerships()
                if (response.isSuccessful) {
                    _dealerships.value = response.body()?.data ?: emptyList()
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
                val response = apiService.getSchoolMarketplaceLeads(schoolId)
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

    fun submitMatchmaker(studentId: String, budget: Double, preferences: Map<String, Any>?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.submitMatchmaker(SubmitMatchmakerRequest(studentId, preferences, budget))
                if (response.isSuccessful) {
                    onSuccess()
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun convertLead(id: String, schoolId: String) {
        viewModelScope.launch {
            try {
                if (apiService.convertMarketplaceLead(id).isSuccessful) {
                    loadSchoolLeads(schoolId)
                }
            } catch (e: Exception) {}
        }
    }

    fun resetError() { _error.value = null }
}
