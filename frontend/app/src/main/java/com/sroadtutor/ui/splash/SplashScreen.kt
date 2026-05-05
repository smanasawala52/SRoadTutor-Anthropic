package com.sroadtutor.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.sroadtutor.data.local.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(
    sessionManager: SessionManager,
    onNavigateToMain: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToVerification: () -> Unit
) {
    LaunchedEffect(Unit) {
        val token = sessionManager.accessToken.first()
        val isVerified = sessionManager.emailVerified.first()
        delay(1500) // Aesthetic delay
        if (token != null) {
            if (isVerified) {
                onNavigateToMain()
            } else {
                onNavigateToVerification()
            }
        } else {
            onNavigateToAuth()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "SRoadTutor",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
