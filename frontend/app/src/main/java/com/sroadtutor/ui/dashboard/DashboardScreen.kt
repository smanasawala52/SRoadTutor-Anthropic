package com.sroadtutor.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.ui.risk.RiskViewModel
import com.sroadtutor.data.remote.models.DashboardResponse
import com.sroadtutor.data.remote.models.RiskAggregateResponse
import com.sroadtutor.data.remote.models.TelemetryDatasetSummary

import com.sroadtutor.data.remote.models.UserRole

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel, 
    riskViewModel: RiskViewModel,
    userRole: UserRole?
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetrySummary by viewModel.telemetrySummary.collectAsStateWithLifecycle()
    val riskAggregate by riskViewModel.aggregate.collectAsStateWithLifecycle()

    LaunchedEffect(userRole) {
        viewModel.loadDashboard(userRole)
        if (userRole == UserRole.OWNER) {
            riskViewModel.loadAggregate()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        when (val state = uiState) {
            is DashboardUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DashboardUiState.Success -> {
                Box(modifier = Modifier.padding(padding)) {
                    DashboardContent(state.data, telemetrySummary, riskAggregate)
                }
            }
            is DashboardUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is DashboardUiState.NotAuthorized -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Welcome to SRoadTutor", style = MaterialTheme.typography.headlineSmall)
                        Text(text = "Your personalized dashboard is being prepared.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    data: DashboardResponse, 
    summary: TelemetryDatasetSummary?,
    risk: RiskAggregateResponse?
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = data.schoolName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Business Overview - ${data.planTier ?: "Free"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Revenue",
                    value = "$${data.totalRevenuePaid}",
                    icon = Icons.Default.AttachMoney,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
                MetricCard(
                    title = "Outstanding",
                    value = "$${data.totalOutstanding}",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        }

        risk?.let { r ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Risk Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            r.countsByTier.forEach { (tier, count) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = tier, style = MaterialTheme.typography.labelSmall)
                                    Text(text = count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }

        summary?.let {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "AV Research Dataset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Total Events: ${it.totalEvents}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Active Students",
                    value = "${data.activeStudentCount}",
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
                MetricCard(
                    title = "Upcoming Sessions",
                    value = "${data.upcomingSessionsCount}",
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        data.instructorWorkloads?.let { workloads ->
            item {
                Text(
                    text = "Instructor Workloads",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(workloads) { workload ->
                ListItem(
                    headlineContent = { Text(workload.instructorName ?: "Unknown") },
                    supportingContent = { Text("${workload.scheduledSessionsInWindow} scheduled | ${workload.completedSessionsInWindow} completed") },
                    trailingContent = {
                        Text(text = "${workload.activeStudentsAssigned} students", style = MaterialTheme.typography.labelSmall)
                    }
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null)
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
