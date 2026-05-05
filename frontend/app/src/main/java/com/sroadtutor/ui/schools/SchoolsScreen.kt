package com.sroadtutor.ui.schools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.data.remote.models.SchoolCreateRequest
import com.sroadtutor.data.remote.models.SchoolResponse
import com.sroadtutor.data.remote.models.SchoolUpdateRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SchoolsScreen(
    viewModel: SchoolsViewModel
) {
    val schools by viewModel.schools.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val navigator = rememberListDetailPaneScaffoldNavigator<SchoolResponse>()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadSchools()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetError()
        }
    }

    if (showAddDialog) {
        AddSchoolDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { request ->
                viewModel.createSchool(request) {
                    showAddDialog = false
                }
            }
        )
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Schools Management") },
                        actions = {
                            IconButton(onClick = { viewModel.loadSchools() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (schools.isEmpty()) {
                        FloatingActionButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Create School")
                        }
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (isLoading && schools.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (schools.isEmpty()) {
                        Text(
                            text = "No schools found. Create one to get started.",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(schools, key = { it.id }) { school ->
                                ListItem(
                                    headlineContent = { Text(school.name, fontWeight = FontWeight.Bold) },
                                    supportingContent = { Text(school.jurisdiction ?: "No Jurisdiction") },
                                    leadingContent = { 
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Icon(
                                                Icons.Default.School, 
                                                contentDescription = null,
                                                modifier = Modifier.padding(8.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        StatusChip(isActive = school.active)
                                    },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, school)
                                        }
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            val school = navigator.currentDestination?.contentKey
            if (school != null) {
                SchoolDetail(
                    school = school,
                    onUpdate = { updated -> viewModel.updateSchool(school.id, updated) },
                    onToggleActive = { viewModel.toggleSchoolActive(school.id, school.active) },
                    onBack = { scope.launch { navigator.navigateBack() } }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a school to view details")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchoolDetail(
    school: SchoolResponse,
    onUpdate: (SchoolUpdateRequest) -> Unit,
    onToggleActive: () -> Unit,
    onBack: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditSchoolDialog(
            school = school,
            onDismiss = { showEditDialog = false },
            onConfirm = { request ->
                onUpdate(request)
                showEditDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(school.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = { showEditDialog = true }) {
                        Text("Edit")
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
            DetailItem(label = "Province", value = school.province ?: "N/A")
            DetailItem(label = "Jurisdiction", value = school.jurisdiction ?: "N/A")
            DetailItem(label = "Business Reg #", value = school.businessRegistrationNumber ?: "N/A")
            DetailItem(label = "Timezone", value = school.timezone ?: "N/A")
            DetailItem(label = "GST #", value = school.gstNumber ?: "N/A")
            DetailItem(label = "Plan Tier", value = school.planTier ?: "N/A")

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onToggleActive,
                modifier = Modifier.fillMaxWidth(),
                colors = if (school.active) 
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (school.active) "Deactivate School" else "Reactivate School")
            }
        }
    }
}

@Composable
private fun StatusChip(isActive: Boolean) {
    val color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    Surface(color = color, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text = if (isActive) "ACTIVE" else "INACTIVE",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AddSchoolDialog(
    onDismiss: () -> Unit,
    onConfirm: (SchoolCreateRequest) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var jurisdiction by remember { mutableStateOf("") }
    var businessReg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Your School") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("School Name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = province, onValueChange = { province = it }, label = { Text("Province (e.g. SK)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = jurisdiction, onValueChange = { jurisdiction = it }, label = { Text("Jurisdiction (e.g. SGI)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = businessReg, onValueChange = { businessReg = it }, label = { Text("Business Registration #") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(SchoolCreateRequest(name = name, province = province, jurisdiction = jurisdiction, businessRegistrationNumber = businessReg)) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditSchoolDialog(
    school: SchoolResponse,
    onDismiss: () -> Unit,
    onConfirm: (SchoolUpdateRequest) -> Unit
) {
    var name by remember { mutableStateOf(school.name) }
    var province by remember { mutableStateOf(school.province ?: "") }
    var jurisdiction by remember { mutableStateOf(school.jurisdiction ?: "") }
    var businessReg by remember { mutableStateOf(school.businessRegistrationNumber ?: "") }
    var timezone by remember { mutableStateOf(school.timezone ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit School") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("School Name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = province, onValueChange = { province = it }, label = { Text("Province") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = jurisdiction, onValueChange = { jurisdiction = it }, label = { Text("Jurisdiction") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = businessReg, onValueChange = { businessReg = it }, label = { Text("Business Registration #") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = timezone, onValueChange = { timezone = it }, label = { Text("Timezone") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(SchoolUpdateRequest(name = name, province = province, jurisdiction = jurisdiction, businessRegistrationNumber = businessReg, timezone = timezone)) }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
