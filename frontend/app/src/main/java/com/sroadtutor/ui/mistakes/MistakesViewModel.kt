package com.sroadtutor.ui.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.SessionMistakeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MistakesViewModel(private val apiService: ApiService) : ViewModel() {

    private val _mistakes = MutableStateFlow<List<SessionMistakeResponse>>(emptyList())
    val mistakes = _mistakes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun loadMistakes(sessionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getSessionMistakes(sessionId)
                if (response.isSuccessful) {
                    _mistakes.value = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }
}
