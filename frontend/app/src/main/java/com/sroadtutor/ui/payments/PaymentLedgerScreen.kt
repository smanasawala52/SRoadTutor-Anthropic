package com.sroadtutor.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.PaymentResponse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentLedgerScreen(studentId: String, apiService: ApiService) {
    var payments by remember { mutableStateOf<List<PaymentResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(studentId) {
        scope.launch {
            try {
                val response = apiService.getStudentLedger(studentId)
                if (response.isSuccessful) {
                    payments = response.body()?.data?.payments ?: emptyList()
                }
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Ledger") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Record Payment */ },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Record Payment")
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(payments, key = { it.id }) { payment ->
                    ListItem(
                        headlineContent = { Text("$${payment.amount}", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("${payment.paymentMethod ?: payment.method ?: "CASH"} • ${payment.createdAt ?: "N/A"}") },
                        trailingContent = { 
                            StatusChip(isPaid = payment.status == "PAID") 
                        },
                        leadingContent = { 
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Icon(
                                    Icons.Default.Payment, 
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StatusChip(isPaid: Boolean) {
    val color = if (isPaid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val label = if (isPaid) "PAID" else "UNPAID"
    Surface(
        color = color,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
