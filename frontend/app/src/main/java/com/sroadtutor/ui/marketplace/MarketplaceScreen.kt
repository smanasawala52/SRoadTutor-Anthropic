package com.sroadtutor.ui.marketplace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
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
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel,
    schoolId: String,
    onBack: () -> Unit
) {
    val dealerships by viewModel.dealerships.collectAsStateWithLifecycle()
    val leads by viewModel.leads.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(schoolId) {
        viewModel.loadDealerships()
        viewModel.loadSchoolLeads(schoolId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marketplace") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Text(text = "Lead Conversions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            
            if (leads.isEmpty()) {
                item { Text("No active leads found.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(leads, key = { it.id }) { lead ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(lead.dealershipName ?: "Unknown Dealership") },
                            supportingContent = { Text("Referral Fee: $${lead.referralFee ?: 0.0} • ${lead.status}") },
                            trailingContent = {
                                if (lead.status == "PENDING") {
                                    Button(onClick = { viewModel.convertLead(lead.id, schoolId) }) {
                                        Text("Convert")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Text(text = "Partner Dealerships", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (isLoading && dealerships.isEmpty()) {
                item { CircularProgressIndicator() }
            } else {
                items(dealerships, key = { it.id }) { dealer ->
                    ListItem(
                        headlineContent = { Text(dealer.name) },
                        supportingContent = { Text(dealer.city ?: "Local") },
                        leadingContent = { Icon(Icons.Default.Store, contentDescription = null) }
                    )
                }
            }
        }
    }
}
