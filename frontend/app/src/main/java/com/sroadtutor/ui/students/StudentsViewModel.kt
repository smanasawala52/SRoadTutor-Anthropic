package com.sroadtutor.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.local.SessionManager
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.AddStudentRequest
import com.sroadtutor.data.remote.models.LinkParentRequest
import com.sroadtutor.data.remote.models.StudentResponse
import com.sroadtutor.data.remote.models.StudentUpdateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudentsViewModel(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _students = MutableStateFlow<List<StudentResponse>>(emptyList())
    val students = _students.asStateFlow()

    private val _myProfile = MutableStateFlow<StudentResponse?>(null)
    val myProfile = _myProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadStudents(schoolId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                if (schoolId.isNotEmpty()) {
                    val response = apiService.getStudents(schoolId)
                    if (response.isSuccessful) {
                        _students.value = response.body()?.data ?: emptyList()
                    } else {
                        _error.value = "Failed to load students (HTTP ${response.code()})"
                    }
                }

                // Best-effort caller-profile fetch — only relevant for STUDENT role.
                // OWNER/INSTRUCTOR/PARENT will get a 404 here which we silently swallow.
                try {
                    val profileResponse = apiService.getMyStudentProfile()
                    if (profileResponse.isSuccessful) {
                        profileResponse.body()?.data?.let {
                            _myProfile.value = it
                            sessionManager.setStudentId(it.id)
                        }
                    }
                } catch (_: Exception) { /* ignore - caller may not be a student */ }
            } catch (e: Exception) {
                _error.value = e.message ?: "An unexpected error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addStudent(schoolId: String, request: AddStudentRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.addStudent(schoolId, request)
                if (response.isSuccessful) {
                    loadStudents(schoolId)
                    onSuccess()
                } else {
                    _error.value = "Failed to add student (HTTP ${response.code()})"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun linkParent(studentId: String, email: String, schoolId: String) {
        viewModelScope.launch {
            try {
                if (apiService.linkParent(studentId, LinkParentRequest(email)).isSuccessful) {
                    loadStudents(schoolId)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun unlinkParent(studentId: String, parentUserId: String, schoolId: String) {
        viewModelScope.launch {
            try {
                if (apiService.unlinkParent(studentId, parentUserId).isSuccessful) {
                    loadStudents(schoolId)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /**
     * Soft-delete a student via PUT /api/students/{id} with status=DROPPED.
     * The backend rejects hard delete; this is the canonical "deactivate"
     * path. Re-activate by calling {@link #setStatus} with status=ACTIVE.
     */
    fun setStatus(studentId: String, status: String, schoolId: String) {
        viewModelScope.launch {
            try {
                val r = apiService.updateStudent(
                    studentId,
                    StudentUpdateRequest(status = status)
                )
                if (r.isSuccessful) {
                    loadStudents(schoolId)
                } else {
                    _error.value = "Could not update student status (HTTP ${r.code()})"
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
