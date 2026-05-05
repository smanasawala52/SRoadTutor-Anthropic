package com.sroadtutor.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.WaMeLinkRequest
import kotlinx.coroutines.launch

/**
 * Tap-to-WhatsApp button. The backend's WaMeLinkRequest.recipientPhoneId is the
 * phone-number entity id (NOT a user id). Caller must resolve the primary phone
 * id for the recipient (e.g., via PhoneNumbersViewModel) and pass it here.
 *
 * If [recipientPhoneId] is null/blank, the button surfaces a snackbar instead
 * of generating a malformed request.
 */
@Composable
fun WhatsAppButton(recipientPhoneId: String?, apiService: ApiService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Button(
        onClick = {
            if (recipientPhoneId.isNullOrBlank()) {
                errorMessage = "No primary phone on file"
                return@Button
            }
            scope.launch {
                isLoading = true
                try {
                    val response = apiService.generateWhatsAppLink(WaMeLinkRequest(recipientPhoneId = recipientPhoneId))
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            // Confirm click beacon (don't block UI on failure)
                            scope.launch { try { apiService.confirmWhatsAppClick(data.logId) } catch (_: Exception) {} }

                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(data.waMeUrl))
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            try {
                                context.startActivity(intent)
                            } catch (_: android.content.ActivityNotFoundException) {
                                errorMessage = "WhatsApp not installed"
                            }
                        } else {
                            errorMessage = "Could not generate link"
                        }
                    } else {
                        errorMessage = "Error ${response.code()}"
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Network error"
                } finally {
                    isLoading = false
                }
            }
        },
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "WhatsApp")
    }

    errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            errorMessage = null
        }
    }
}
