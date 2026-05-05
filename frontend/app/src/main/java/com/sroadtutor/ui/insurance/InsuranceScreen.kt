package com.sroadtutor.ui.insurance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsuranceScreen(
    viewModel: InsuranceViewModel,
    schoolId: String,
    onBack: () -> Unit
) {
    val brokers by viewModel.brokers.collectAsStateWithLifecycle()
    val leads by viewModel.leads.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(schoolId) {
        viewModel.loadBrokers()
        viewModel.loadSchoolLeads(schoolId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insurance Leads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSchoolLeads(schoolId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(text = "Active Leads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (leads.isEmpty()) {
                item { Text("No insurance leads found.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(leads, key = { it.id }) { lead ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Student: ${lead.studentId.take(8)}") },
                            supportingContent = { Text("Status: ${lead.status} • Created: ${lead.createdAt ?: "N/A"}") },
                            trailingContent = {
                                Row {
                                    if (lead.status == "PENDING") {
                                        Button(onClick = { viewModel.markQuoted(lead.id, schoolId) }) {
                                            Text("Quote")
                                        }
                                    } else if (lead.status == "QUOTED") {
                                        Button(onClick = { viewModel.convertLead(lead.id, schoolId) }) {
                                            Text("Convert")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Text(text = "Partner Brokers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            items(brokers, key = { it.id }) { broker ->
                ListItem(
                    headlineContent = { Text(broker.name) },
                    supportingContent = { Text("${broker.contactEmail ?: "No email"} • ${broker.province ?: "N/A"}") }
                )
            }
        }
    }
}
