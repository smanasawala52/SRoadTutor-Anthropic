package com.sroadtutor.ui.mistakes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.LogMistakeRequest
import com.sroadtutor.data.remote.models.MistakeSeverity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakeLoggingScreen(
    sessionId: String, 
    apiService: ApiService, 
    onLogged: (String) -> Unit
) {
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(MistakeSeverity.MINOR) }
    var expanded by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Log Driving Mistake", style = MaterialTheme.typography.headlineSmall)
            
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category (e.g., Speeding, Signal)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = severity.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Severity") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    MistakeSeverity.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.name) },
                            onClick = {
                                severity = s
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        try {
                            val response = apiService.logMistake(sessionId, LogMistakeRequest(
                                mistakeCategoryId = category, 
                                description = description, 
                                severity = severity.name
                            ))
                            if (response.isSuccessful) {
                                val mistakeId = response.body()?.data?.id
                                if (mistakeId != null) {
                                    onLogged(mistakeId)
                                }
                            }
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = category.isNotBlank() && !isSubmitting,
                shape = MaterialTheme.shapes.medium
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Log Mistake", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
