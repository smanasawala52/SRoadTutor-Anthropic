package com.sroadtutor.ui.telemetry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.data.remote.models.TelemetryEventResponse

@Composable
fun AttachTelemetryDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Double) -> Unit
) {
    var speed by remember { mutableStateOf("") }
    var acc by remember { mutableStateOf("") }
    var brake by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attach Telemetry Snapshot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = speed, onValueChange = { speed = it }, label = { Text("Speed (km/h)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = acc, onValueChange = { acc = it }, label = { Text("Acceleration (m/s²)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = brake, onValueChange = { brake = it }, label = { Text("Braking Force") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onConfirm(
                        speed.toDoubleOrNull() ?: 0.0,
                        acc.toDoubleOrNull() ?: 0.0,
                        brake.toDoubleOrNull() ?: 0.0
                    )
                },
                enabled = speed.isNotBlank() && acc.isNotBlank() && brake.isNotBlank()
            ) {
                Text("Attach")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryScreen(
    viewModel: TelemetryViewModel,
    mistakeId: String,
    onBack: () -> Unit
) {
    val events by viewModel.mistakeEvents.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(mistakeId) {
        viewModel.loadMistakeEvents(mistakeId)
    }

    var showAttachDialog by remember { mutableStateOf(false) }

    if (showAttachDialog) {
        AttachTelemetryDialog(
            onDismiss = { showAttachDialog = false },
            onConfirm = { speed, acc, brake ->
                viewModel.attachTelemetry(
                    mistakeId,
                    com.sroadtutor.data.remote.models.AttachTelemetryRequest(
                        telemetry = mapOf(
                            "speed" to speed,
                            "acceleration" to acc,
                            "brakingForce" to brake
                        )
                    )
                ) {
                    showAttachDialog = false
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mistake Telemetry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadMistakeEvents(mistakeId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAttachDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Attach Snapshot")
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && events.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (events.isEmpty()) {
                Text("No telemetry recorded for this mistake.", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(events, key = { it.id }) { event ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Telemetry Event", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Vehicle: ${event.vehicleMake ?: ""} ${event.vehicleModel ?: ""} ${event.vehicleYear ?: ""}", style = MaterialTheme.typography.bodySmall)
                                Text(text = "JSON: ${event.telemetryJson}", style = MaterialTheme.typography.bodySmall)
                                Text(text = "Synced: ${event.syncedAt}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
            }
        }
    }
}
