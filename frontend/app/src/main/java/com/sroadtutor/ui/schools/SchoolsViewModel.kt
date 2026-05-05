package com.sroadtutor.ui.schools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SchoolsViewModel(private val apiService: ApiService) : ViewModel() {

    private val _schools = MutableStateFlow<List<SchoolResponse>>(emptyList())
    val schools = _schools.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadSchools() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Fetch owner's school lean projection
                val meResponse = apiService.getMySchool()
                if (meResponse.isSuccessful) {
                    val meSchool = meResponse.body()?.data
                    if (meSchool != null) {
                        // Fetch full detail for the school list
                        val detailResponse = apiService.getSchool(meSchool.id)
                        if (detailResponse.isSuccessful && detailResponse.body()?.data != null) {
                            _schools.value = listOf(detailResponse.body()!!.data!!)
                        } else {
                            // Fallback to minimal data if full detail fails
                            _schools.value = listOf(SchoolResponse(
                                id = meSchool.id,
                                name = meSchool.name,
                                ownerId = "", // Unknown
                                planTier = meSchool.planTier,
                                jurisdiction = meSchool.jurisdiction,
                                province = meSchool.province,
                                timezone = meSchool.timezone,
                                active = meSchool.active
                            ))
                        }
                    } else {
                        _schools.value = emptyList()
                    }
                } else {
                    _error.value = "Failed to load school: ${meResponse.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createSchool(request: SchoolCreateRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.createSchool(request)
                if (response.isSuccessful) {
                    loadSchools()
                    onSuccess()
                } else {
                    _error.value = "Failed to create school: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSchool(id: String, request: SchoolUpdateRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.updateSchool(id, request)
                if (response.isSuccessful) {
                    loadSchools()
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleSchoolActive(id: String, currentActive: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = if (currentActive) {
                    apiService.deactivateSchool(id)
                } else {
                    apiService.reactivateSchool(id)
                }
                if (response.isSuccessful) {
                    loadSchools()
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetError() {
        _error.value = null
    }
}
