package com.sroadtutor.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.ReminderResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemindersViewModel(private val apiService: ApiService) : ViewModel() {

    private val _reminders = MutableStateFlow<List<ReminderResponse>>(emptyList())
    val reminders = _reminders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadPendingReminders() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiService.getMyPendingReminders()
                if (response.isSuccessful && response.body()?.success == true) {
                    _reminders.value = response.body()?.data ?: emptyList()
                } else {
                    _error.value = response.body()?.message ?: "Failed to load reminders"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "An error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fireReminder(id: String) {
        viewModelScope.launch {
            try {
                val response = apiService.fireReminder(id)
                if (response.isSuccessful) {
                    loadPendingReminders()
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
