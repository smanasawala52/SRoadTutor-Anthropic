package com.sroadtutor.ui.invitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.CreateInstructorInvitationRequest
import com.sroadtutor.data.remote.models.CreateParentInvitationRequest
import com.sroadtutor.data.remote.models.CreateStudentInvitationRequest
import com.sroadtutor.data.remote.models.InvitationResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InvitationsViewModel(private val apiService: ApiService) : ViewModel() {

    private val _invitations = MutableStateFlow<List<InvitationResponse>>(emptyList())
    val invitations = _invitations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadInvitations(schoolId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiService.getSchoolInvitations(schoolId)
                if (response.isSuccessful && response.body()?.success == true) {
                    _invitations.value = response.body()?.data ?: emptyList()
                } else {
                    _error.value = response.body()?.message ?: "Failed to load invitations"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun inviteStudent(schoolId: String, name: String, email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val r = apiService.inviteStudent(
                    schoolId,
                    CreateStudentInvitationRequest(email = email, fullName = name)
                )
                if (r.isSuccessful) loadInvitations(schoolId)
                else _error.value = "Invite failed (HTTP ${r.code()})"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun inviteInstructor(schoolId: String, name: String, email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val r = apiService.inviteInstructor(
                    schoolId,
                    CreateInstructorInvitationRequest(email = email, fullName = name)
                )
                if (r.isSuccessful) loadInvitations(schoolId)
                else _error.value = "Invite failed (HTTP ${r.code()})"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun inviteParent(schoolId: String, name: String, email: String, studentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                apiService.inviteParent(
                    schoolId,
                    CreateParentInvitationRequest(
                        email = email,
                        fullName = name,
                        studentIds = listOf(studentId)
                    )
                )
                loadInvitations(schoolId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun revokeInvitation(schoolId: String, invitationId: String) {
        viewModelScope.launch {
            try {
                apiService.revokeInvitation(invitationId)
                loadInvitations(schoolId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun reissueInvitation(schoolId: String, invitationId: String) {
        viewModelScope.launch {
            try {
                apiService.reissueInvitation(invitationId)
                loadInvitations(schoolId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun resetError() {
        _error.value = null
    }
}
