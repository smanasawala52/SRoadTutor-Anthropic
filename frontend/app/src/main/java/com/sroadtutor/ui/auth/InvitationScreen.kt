package com.sroadtutor.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.data.local.SessionManager
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.AcceptInvitationRequest
import com.sroadtutor.data.remote.models.InvitationLookupResponse
import com.sroadtutor.data.remote.models.InvitationResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InvitationViewModel(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _invitationInfo = MutableStateFlow< InvitationLookupResponse?>(null)
    val invitationInfo = _invitationInfo.asStateFlow()

    fun lookup(token: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.lookupInvitation(token)
                if (response.isSuccessful && response.body()?.success == true) {
                    _invitationInfo.value = response.body()?.data
                    _uiState.value = AuthUiState.Idle
                } else {
                    _uiState.value = AuthUiState.Error("Invalid or expired invitation")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "An error occurred")
            }
        }
    }

    fun acceptInvitation(token: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.acceptInvitation(token, AcceptInvitationRequest(password))
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.Error(response.body()?.message ?: "Failed to accept invitation")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "An error occurred")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

@Composable
fun InvitationScreen(
    token: String,
    viewModel: InvitationViewModel,
    onSuccess: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val inviteInfo by viewModel.invitationInfo.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(token) {
        viewModel.lookup(token)
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onSuccess()
        } else if (uiState is AuthUiState.Error) {
            snackbarHostState.showSnackbar((uiState as AuthUiState.Error).message)
            viewModel.resetState()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = inviteInfo?.let { "Join ${it.role} at School" } ?: "Accept Invitation",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = inviteInfo?.let { "Invitation for ${it.email}. Set your account password to join." } 
                    ?: "Welcome to the team! Set your account password to join.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Set Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                shape = MaterialTheme.shapes.medium,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.acceptInvitation(token, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState !is AuthUiState.Loading && password.length >= 6,
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Join School", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
