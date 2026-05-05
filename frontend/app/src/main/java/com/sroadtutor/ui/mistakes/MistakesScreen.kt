package com.sroadtutor.ui.mistakes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
fun MistakesScreen(
    viewModel: MistakesViewModel,
    sessionId: String,
    onViewTelemetry: (String) -> Unit,
    onBack: () -> Unit
) {
    val mistakes by viewModel.mistakes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) {
        viewModel.loadMistakes(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Mistakes") },
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
            if (isLoading && mistakes.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (mistakes.isEmpty()) {
                Text("No mistakes logged for this session.", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mistakes, key = { it.id }) { mistake ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onViewTelemetry(mistake.id) }
                        ) {
                            ListItem(
                                headlineContent = { Text(mistake.category ?: mistake.categoryName ?: "General", fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(mistake.description ?: mistake.instructorNotes ?: "No details") },
                                trailingContent = { 
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = mistake.severity ?: "MINOR",
                                            modifier = Modifier.padding(4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }
    }
}
