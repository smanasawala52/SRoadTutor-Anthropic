package com.sroadtutor.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.PhoneNumberOwnerType
import com.sroadtutor.data.remote.models.PhoneNumberRequest
import com.sroadtutor.data.remote.models.PhoneNumberResponse
import com.sroadtutor.data.remote.models.PhoneNumberUpdateRequest
import com.sroadtutor.data.remote.models.WhatsappOptInRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhoneNumbersViewModel(private val apiService: ApiService) : ViewModel() {

    private val _phoneNumbers = MutableStateFlow<List<PhoneNumberResponse>>(emptyList())
    val phoneNumbers = _phoneNumbers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadPhoneNumbers(ownerId: String?, ownerType: PhoneNumberOwnerType?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiService.getPhoneNumbers(ownerId, ownerType?.name)
                if (response.isSuccessful) {
                    _phoneNumbers.value = response.body()?.data ?: emptyList()
                } else {
                    _error.value = "Failed to load phone numbers"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "An unexpected error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addPhoneNumber(request: PhoneNumberRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.createPhoneNumber(request)
                if (response.isSuccessful) {
                    loadPhoneNumbers(request.ownerId, PhoneNumberOwnerType.valueOf(request.ownerType))
                    onSuccess()
                } else {
                    _error.value = "Failed to add phone number"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updatePhoneNumber(id: String, request: PhoneNumberUpdateRequest, ownerId: String, ownerType: PhoneNumberOwnerType) {
        viewModelScope.launch {
            try {
                val response = apiService.updatePhoneNumber(id, request)
                if (response.isSuccessful) {
                    loadPhoneNumbers(ownerId, ownerType)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deletePhoneNumber(id: String, ownerId: String, ownerType: PhoneNumberOwnerType) {
        viewModelScope.launch {
            try {
                val response = apiService.deletePhoneNumber(id)
                if (response.isSuccessful) {
                    loadPhoneNumbers(ownerId, ownerType)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun toggleWhatsApp(id: String, ownerId: String, ownerType: PhoneNumberOwnerType) {
        viewModelScope.launch {
            try {
                val current = _phoneNumbers.value.find { it.id == id }
                val newStatus = !(current?.whatsappOptIn ?: false)
                val response = apiService.toggleWhatsAppOptIn(id, WhatsappOptInRequest(newStatus))
                if (response.isSuccessful) {
                    loadPhoneNumbers(ownerId, ownerType)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun setPrimary(id: String, ownerId: String, ownerType: PhoneNumberOwnerType) {
        viewModelScope.launch {
            try {
                val response = apiService.setPrimaryPhoneNumber(id)
                if (response.isSuccessful) {
                    loadPhoneNumbers(ownerId, ownerType)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun resetError() {
        _error.value = null
    }
}
