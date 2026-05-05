package com.sroadtutor.ui.risk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.data.remote.models.RiskScoreResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskScoreScreen(
    viewModel: RiskViewModel,
    studentId: String?,
    hash: String?,
    onBack: () -> Unit
) {
    val score by viewModel.studentScore.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(studentId, hash) {
        if (hash != null) {
            viewModel.loadByHash(hash)
        } else if (studentId != null) {
            viewModel.generateScore(studentId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risk Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (score == null) {
                Text("No risk data available.", modifier = Modifier.align(Alignment.Center))
            } else {
                RiskScoreContent(score!!)
            }
        }
    }
}

@Composable
private fun RiskScoreContent(score: RiskScoreResponse) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when(score.riskTier) {
                        "LOW" -> MaterialTheme.colorScheme.primaryContainer
                        "MEDIUM" -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Safety Tier: ${score.riskTier ?: "UNKNOWN"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Risk Profile Generated", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                    Text(text = "Anonymized Hash: ${(score.studentAnonymizedHash ?: "").take(8)}...", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            Text(text = "Mistake Profile Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Text(text = score.mistakeProfileJson ?: "No detailed factors provided", style = MaterialTheme.typography.bodyMedium)
        }

        item {
            Text(
                text = "Generated on ${score.generatedAt ?: "N/A"}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
