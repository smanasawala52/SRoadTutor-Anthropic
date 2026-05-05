package com.sroadtutor.ui.instructors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.local.SessionManager
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.InstructorCreateRequest
import com.sroadtutor.data.remote.models.InstructorResponse
import com.sroadtutor.data.remote.models.InstructorUpdateRequest
import com.sroadtutor.data.remote.models.InstructorPayoutResponse
import com.sroadtutor.data.remote.models.MarkPayoutPaidRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstructorsViewModel(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _instructors = MutableStateFlow<List<InstructorResponse>>(emptyList())
    val instructors = _instructors.asStateFlow()

    private val _profileMissing = MutableStateFlow(false)
    val profileMissing = _profileMissing.asStateFlow()

    private val _payouts = MutableStateFlow<List<InstructorPayoutResponse>>(emptyList())
    val payouts = _payouts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /**
     * Owner uses {@code GET /api/schools/{schoolId}/instructors} to see the full
     * roster. We also try {@code GET /api/instructors/me} so an instructor user
     * who lands on this screen sees their own profile (and we cache instructor.id
     * in SessionManager). Either failure is non-fatal — we fall through.
     */
    fun loadInstructors(schoolId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _profileMissing.value = false
            try {
                if (schoolId.isNotBlank()) {
                    val list = apiService.getSchoolInstructors(schoolId)
                    if (list.isSuccessful) {
                        _instructors.value = list.body()?.data ?: emptyList()
                    } else if (list.code() != 403) {
                        // 403 is fine — the caller may be an instructor whose
                        // own school is set but who isn't an OWNER. Fall back
                        // to /me below.
                        _error.value = "Failed to load instructors (HTTP ${list.code()})"
                    }
                }

                // Best-effort caller profile (only meaningful for INSTRUCTOR).
                try {
                    val me = apiService.getMyInstructorProfile()
                    if (me.isSuccessful) {
                        me.body()?.data?.let { profile ->
                            sessionManager.setInstructorId(profile.id)
                            // If the school list came back empty (e.g. instructor
                            // not yet attached), at least show their own row.
                            if (_instructors.value.isEmpty()) _instructors.value = listOf(profile)
                        }
                    } else if (me.code() == 404) {
                        // Only flag profile-missing if the caller is an instructor
                        // who hasn't self-registered yet — owners see this 404
                        // routinely and should NOT see a "complete your profile" CTA.
                        if (_instructors.value.isEmpty()) _profileMissing.value = true
                    }
                } catch (_: Exception) { /* swallow — owner / non-instructor caller */ }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerInstructor(request: InstructorCreateRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.registerInstructor(request)
                if (response.isSuccessful) {
                    _profileMissing.value = false
                    val profile = response.body()?.data
                    if (profile != null) {
                        _instructors.value = listOf(profile)
                        sessionManager.setInstructorId(profile.id)
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun detachInstructor(schoolId: String, instructorId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.detachInstructor(schoolId, instructorId)
                if (response.isSuccessful) {
                    loadInstructors(schoolId)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun loadPayouts(instructorId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getInstructorPayouts(instructorId)
                if (response.isSuccessful) {
                    _payouts.value = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markPayoutPaid(payoutId: String, instructorId: String, eTransferRef: String? = null) {
        viewModelScope.launch {
            if (apiService.markPayoutPaid(payoutId, MarkPayoutPaidRequest(eTransferRef)).isSuccessful) {
                loadPayouts(instructorId)
            }
        }
    }
}
