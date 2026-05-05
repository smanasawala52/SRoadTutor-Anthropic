package com.sroadtutor.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.PlanRow
import com.sroadtutor.data.remote.models.SubscriptionResponse
import com.sroadtutor.data.remote.models.UpgradeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubscriptionsViewModel(private val apiService: ApiService) : ViewModel() {

    private val _plans = MutableStateFlow<List<PlanRow>>(emptyList())
    val plans = _plans.asStateFlow()

    private val _mySubscription = MutableStateFlow<SubscriptionResponse?>(null)
    val mySubscription = _mySubscription.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _checkoutUrl = MutableStateFlow<String?>(null)
    val checkoutUrl = _checkoutUrl.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val plansResponse = apiService.getSubscriptionPlans()
                if (plansResponse.isSuccessful) {
                    _plans.value = plansResponse.body()?.data?.plans ?: emptyList()
                }

                val subResponse = apiService.getMySubscription()
                if (subResponse.isSuccessful) {
                    _mySubscription.value = subResponse.body()?.data
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun upgrade(planId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.upgradeSubscription(UpgradeRequest(planId))
                if (response.isSuccessful) {
                    _checkoutUrl.value = response.body()?.data?.checkoutUrl
                } else {
                    _error.value = "Failed to initiate upgrade"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetCheckoutUrl() {
        _checkoutUrl.value = null
    }

    fun resetError() {
        _error.value = null
    }
}
