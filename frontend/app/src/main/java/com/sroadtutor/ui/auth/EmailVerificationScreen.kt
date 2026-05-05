package com.sroadtutor.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun EmailVerificationScreen(
    viewModel: AuthViewModel,
    onVerificationSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var token by remember { mutableStateOf("") }
    var resendCountdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onVerificationSuccess()
        } else if (uiState is AuthUiState.Error) {
            snackbarHostState.showSnackbar((uiState as AuthUiState.Error).message)
            viewModel.resetState()
        }
    }

    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1000)
            resendCountdown--
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
            Icon(
                imageVector = if (uiState is AuthUiState.VerificationSent) Icons.Default.MarkEmailRead else Icons.Default.MarkEmailUnread,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Verify Your Email",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (uiState is AuthUiState.VerificationSent) 
                    "We've sent a code to your email. Enter it below to verify your account." 
                    else "To protect your account, we need to verify your email address before you can access the dashboard.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState is AuthUiState.VerificationSent || uiState is AuthUiState.Idle || uiState is AuthUiState.Loading) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { if (it.length <= 6) token = it },
                    label = { Text("6-Digit Verification Code") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    placeholder = { Text("######") },
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.confirmVerification(token) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = token.length == 6 && uiState !is AuthUiState.Loading,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Verify & Continue", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (uiState is AuthUiState.NeedsVerification) {
                Button(
                    onClick = { 
                        viewModel.sendVerificationEmail()
                        resendCountdown = 60
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = uiState !is AuthUiState.Loading,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text("Send Verification Email", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState is AuthUiState.VerificationSent || uiState is AuthUiState.Idle) {
                TextButton(
                    onClick = { 
                        viewModel.sendVerificationEmail()
                        resendCountdown = 60
                    },
                    enabled = resendCountdown == 0 && uiState !is AuthUiState.Loading
                ) {
                    Text(if (resendCountdown > 0) "Resend code in ${resendCountdown}s" else "Didn't receive a code? Resend")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onBackToLogin) {
                Text("Switch Account / Back to Login", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
