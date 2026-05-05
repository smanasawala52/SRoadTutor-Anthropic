package com.sroadtutor.ui.marketplace

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchmakerScreen(
    viewModel: MarketplaceViewModel,
    studentId: String = "",
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var budget by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("First Car Matchmaker") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Let us help you find the perfect first car for your student.", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = budget,
                onValueChange = { budget = it },
                label = { Text("Budget (CAD)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vehicleType,
                onValueChange = { vehicleType = it },
                label = { Text("Vehicle Type (e.g. Sedan, SUV)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Preferred Brand (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Additional Preferences / Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    isSubmitting = true
                    val preferences = mutableMapOf<String, Any>()
                    if (vehicleType.isNotBlank()) preferences["vehicleType"] = vehicleType
                    if (brand.isNotBlank()) preferences["brand"] = brand
                    if (notes.isNotBlank()) preferences["notes"] = notes

                    viewModel.submitMatchmaker(
                        studentId = studentId,
                        budget = budget.toDoubleOrNull() ?: 0.0,
                        preferences = preferences
                    ) {
                        isSubmitting = false
                        onSuccess()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = budget.isNotBlank() && !isSubmitting,
                shape = MaterialTheme.shapes.medium
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Submit Matchmaker Request", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
