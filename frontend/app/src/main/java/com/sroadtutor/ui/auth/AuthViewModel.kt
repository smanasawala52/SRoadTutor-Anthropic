package com.sroadtutor.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.local.SessionManager
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AuthViewModel(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.login(LoginRequest(email, password))
                handleAuthResponse(response)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "An error occurred")
            }
        }
    }

    fun signup(name: String, email: String, password: String, role: UserRole, languagePref: String? = null) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.signup(
                    SignupRequest(
                        email = email,
                        password = password,
                        fullName = name,
                        role = role,
                        languagePref = languagePref
                    )
                )
                handleAuthResponse(response)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "An error occurred")
            }
        }
    }

    fun googleLogin(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.googleLogin(OAuthLoginRequest(token = idToken))
                handleAuthResponse(response)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "An error occurred")
            }
        }
    }

    private suspend fun handleAuthResponse(response: retrofit2.Response<ApiResponse<AuthResponse>>) {
        if (response.isSuccessful) {
            val body = response.body()
            // Backend might send success=false in the envelope or just use HTTP status codes
            if (body != null && body.success) {
                val authData = body.data
                if (authData != null) {
                    sessionManager.saveSession(
                        accessToken = authData.accessToken,
                        refreshToken = authData.refreshToken ?: "",
                        userId = authData.user.id,
                        name = authData.user.fullName,
                        role = authData.user.role,
                        emailVerified = authData.user.emailVerified,
                        schoolId = authData.user.schoolId
                    )
                    // Eagerly resolve the role-specific entity id (instructor.id /
                    // student.id) so calendar / mistake / payment endpoints that
                    // filter by entity id (NOT user id) have it on hand.
                    bootstrapRoleEntityId(authData.user.role)
                    if (authData.user.emailVerified) {
                        _uiState.value = AuthUiState.Success
                    } else {
                        _uiState.value = AuthUiState.NeedsVerification
                    }
                    return
                }
            }
        }

        // Error handling
        val errorBody = response.errorBody()?.string()
        val message = response.body()?.message ?: "Error ${response.code()}: ${response.message()}"
        android.util.Log.e("AuthViewModel", "Auth failed: $message, Body: $errorBody")
        _uiState.value = AuthUiState.Error(message)
    }

    /**
     * After login, fetch the role-specific profile so SessionManager has
     * instructor.id / student.id (NOT user.id) cached. Failures are logged
     * but never surfaced — login must not fail because a profile lookup
     * 404'd (e.g. an instructor who hasn't self-registered yet).
     */
    private suspend fun bootstrapRoleEntityId(role: UserRole) {
        try {
            when (role) {
                UserRole.INSTRUCTOR -> {
                    val r = apiService.getMyInstructorProfile()
                    if (r.isSuccessful) {
                        r.body()?.data?.id?.let { sessionManager.setInstructorId(it) }
                    }
                }
                UserRole.STUDENT -> {
                    val r = apiService.getMyStudentProfile()
                    if (r.isSuccessful) {
                        r.body()?.data?.id?.let { sessionManager.setStudentId(it) }
                    }
                }
                else -> { /* OWNER / PARENT — nothing to bootstrap. */ }
            }
        } catch (e: Exception) {
            android.util.Log.w("AuthViewModel", "Profile bootstrap failed: ${e.message}")
        }
    }

    fun sendVerificationEmail() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.sendEmailVerification()
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.value = AuthUiState.VerificationSent
                } else {
                    _uiState.value = AuthUiState.Error(response.body()?.message ?: "Failed to send verification email")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "An error occurred")
            }
        }
    }

    fun confirmVerification(token: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.confirmEmailVerification(token)
                if (response.isSuccessful && response.body()?.success == true) {
                    sessionManager.setEmailVerified(true)
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.Error(response.body()?.message ?: "Invalid verification token")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "An error occurred")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                val refreshToken = sessionManager.refreshToken.first()
                if (refreshToken != null) {
                    apiService.logout(LogoutRequest(refreshToken))
                }
            } catch (e: Exception) {
                // Ignore failure on logout
            } finally {
                sessionManager.clearSession()
                _uiState.value = AuthUiState.Idle
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Success : AuthUiState
    data object NeedsVerification : AuthUiState
    data object VerificationSent : AuthUiState
    data class Error(val message: String) : AuthUiState
}
