package com.sroadtutor.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.AddStudentRequest
import com.sroadtutor.data.remote.models.StudentResponse
import com.sroadtutor.ui.common.WhatsAppButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    viewModel: StudentsViewModel,
    apiService: ApiService,
    schoolId: String,
    onViewPayments: (String) -> Unit,
    onViewPhones: (String) -> Unit,
    onViewRiskScore: (String) -> Unit
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    
    val navigator = rememberListDetailPaneScaffoldNavigator<StudentResponse>()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var showLinkParentDialog by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(schoolId) {
        viewModel.loadStudents(schoolId)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetError()
        }
    }

    if (showAddDialog) {
        AddStudentDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, email, packageType ->
                viewModel.addStudent(
                    schoolId, 
                    AddStudentRequest(
                        studentEmail = email, 
                        studentFullName = name, 
                        packageTotalLessons = packageType.toIntOrNull()
                    )
                ) {
                    showAddDialog = false
                }
            }
        )
    }

    if (showLinkParentDialog != null) {
        LinkParentDialog(
            onDismiss = { showLinkParentDialog = null },
            onConfirm = { email ->
                viewModel.linkParent(showLinkParentDialog!!, email, schoolId)
                showLinkParentDialog = null
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
                        title = { Text("Students") },
                        actions = {
                            IconButton(onClick = { viewModel.loadStudents(schoolId) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Student")
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (isLoading && students.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (error != null && students.isEmpty()) {
                        Text(text = error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(students, key = { it.id }) { student ->
                                ListItem(
                                    headlineContent = { Text(student.displayName, fontWeight = FontWeight.Bold) },
                                    supportingContent = { Text(student.email ?: "ID: ${student.id.take(8)}") },
                                    leadingContent = { 
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Icon(
                                                Icons.Default.Group, 
                                                contentDescription = null,
                                                modifier = Modifier.padding(8.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Text(
                                            text = "Score: ${student.readinessScore ?: "N/A"}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, student)
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
            val student = navigator.currentDestination?.contentKey
            if (student != null) {
                StudentDetail(
                    student = student,
                    apiService = apiService,
                    onViewPayments = onViewPayments,
                    onViewPhones = onViewPhones,
                    onViewRiskScore = onViewRiskScore,
                    onLinkParent = { showLinkParentDialog = student.id },
                    onUnlinkParent = { parentId -> viewModel.unlinkParent(student.id, parentId, schoolId) },
                    onToggleActive = {
                        // ACTIVE → DROPPED, DROPPED → ACTIVE.
                        val newStatus = if (student.status == "DROPPED") "ACTIVE" else "DROPPED"
                        viewModel.setStatus(student.id, newStatus, schoolId)
                        scope.launch { navigator.navigateBack() }
                    },
                    snackbarHostState = snackbarHostState,
                    onBack = {
                        scope.launch { navigator.navigateBack() }
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a student to view details", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDetail(
    student: StudentResponse,
    apiService: ApiService,
    onViewPayments: (String) -> Unit,
    onViewPhones: (String) -> Unit,
    onViewRiskScore: (String) -> Unit,
    onLinkParent: () -> Unit,
    onUnlinkParent: (String) -> Unit,
    onToggleActive: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(student.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DetailItem(label = "Full Name", value = student.displayName)
            DetailItem(label = "Email Address", value = student.email ?: "—")
            DetailItem(label = "Package Type", value = student.packageType ?: "Standard")
            DetailItem(label = "Readiness Score", value = student.readinessScore?.toString() ?: "Not calculated")
            
            val primaryParentId = student.parents?.firstOrNull()?.parentUserId
            DetailItem(label = "Linked Parent", value = if (primaryParentId != null) "Linked (ID: ${primaryParentId})" else "No parent linked")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // recipientPhoneId is unknown at this list level; route the user to
            // the phone-numbers screen to pick a primary phone, then re-attempt.
            WhatsAppButton(recipientPhoneId = null, apiService = apiService)
            
            if (primaryParentId == null) {
                Button(onClick = onLinkParent, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Link Parent Account")
                }
            } else {
                OutlinedButton(
                    onClick = { onUnlinkParent(primaryParentId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlink Parent Account")
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val response = apiService.getStudentReportCard(student.id)
                            if (response.isSuccessful) {
                                snackbarHostState.showSnackbar("PDF Report Card generated successfully")
                            } else {
                                snackbarHostState.showSnackbar("Failed to generate PDF")
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Error: ${e.message}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("View Report Card")
            }
            
            Button(
                onClick = { onViewRiskScore(student.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("View Risk Diagnostics")
            }

            Button(
                onClick = { onViewPayments(student.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Payment, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Financial Ledger")
            }

            Button(
                onClick = { onViewPhones(student.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Phone Numbers")
            }

            OutlinedButton(
                onClick = onToggleActive,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (student.status == "DROPPED") "Reactivate Student" else "Deactivate Student")
            }
        }
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
private fun AddStudentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Student") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                OutlinedTextField(value = pkg, onValueChange = { pkg = it }, label = { Text("Package (e.g. 10 Lessons)") })
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, email, pkg) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LinkParentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Parent Account") },
        text = {
            OutlinedTextField(
                value = email, 
                onValueChange = { email = it }, 
                label = { Text("Parent Email Address") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(email) }, enabled = email.isNotBlank()) {
                Text("Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
