package com.sroadtutor.ui.instructors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
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
import com.sroadtutor.data.remote.models.InstructorResponse
import com.sroadtutor.ui.common.WhatsAppButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InstructorsScreen(
    viewModel: InstructorsViewModel,
    apiService: ApiService,
    schoolId: String,
    onViewPhones: (String) -> Unit
) {
    val instructors by viewModel.instructors.collectAsStateWithLifecycle()
    val profileMissing by viewModel.profileMissing.collectAsStateWithLifecycle()
    val payouts by viewModel.payouts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val navigator = rememberListDetailPaneScaffoldNavigator<InstructorResponse>()
    val scope = rememberCoroutineScope()

    LaunchedEffect(schoolId) {
        viewModel.loadInstructors(schoolId)
    }

    if (profileMissing) {
        InstructorRegistrationScreen(
            onRegister = { request: com.sroadtutor.data.remote.models.InstructorCreateRequest -> 
                viewModel.registerInstructor(request)
                viewModel.loadInstructors(schoolId)
            }
        )
    } else {
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Instructors") },
                            actions = {
                                IconButton(onClick = { viewModel.loadInstructors(schoolId) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { /* Invite Instructor logic */ },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Invite Instructor")
                        }
                    },
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        if (isLoading && instructors.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (error != null && instructors.isEmpty()) {
                            Text(text = error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(instructors, key = { it.id }) { instructor ->
                                    ListItem(
                                        headlineContent = { Text(instructor.displayName, fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text(instructor.email ?: "ID: ${instructor.id.take(8)}") },
                                        leadingContent = { 
                                            Surface(
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Icon(
                                                    Icons.Default.Person, 
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(8.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        },
                                        modifier = Modifier.clickable {
                                            scope.launch {
                                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, instructor)
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
                val instructor = navigator.currentDestination?.contentKey
                if (instructor != null) {
                    LaunchedEffect(instructor.id) {
                        viewModel.loadPayouts(instructor.id)
                    }
                    InstructorDetail(
                        instructor = instructor,
                        payouts = payouts,
                        apiService = apiService,
                        onMarkPaid = { payoutId: String -> viewModel.markPayoutPaid(payoutId, instructor.id) },
                        onViewPhones = onViewPhones,
                        onBack = { scope.launch { navigator.navigateBack() } }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select an instructor to view details", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstructorDetail(
    instructor: InstructorResponse,
    payouts: List<com.sroadtutor.data.remote.models.InstructorPayoutResponse>,
    apiService: ApiService,
    onMarkPaid: (String) -> Unit,
    onViewPhones: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(instructor.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DetailItem(label = "Instructor ID", value = instructor.id) }
            item { DetailItem(label = "License No", value = instructor.licenseNo ?: "N/A") }
            item { DetailItem(label = "SGI Certificate", value = instructor.sgiCert ?: "N/A") }
            item { DetailItem(label = "Vehicle", value = "${instructor.vehicleMake ?: ""} ${instructor.vehicleModel ?: ""} (${instructor.vehicleYear ?: "N/A"})") }
            item { DetailItem(label = "Bio", value = instructor.bio ?: "No bio provided") }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                // recipientPhoneId resolved on the phone-numbers screen.
                WhatsAppButton(recipientPhoneId = null, apiService = apiService)
            }
            
            item {
                Button(
                    onClick = { onViewPhones(instructor.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Phone Numbers")
                }
            }

            item {
                Text(text = "Referral Payouts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (payouts.isEmpty()) {
                item { Text("No payouts recorded.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(payouts, key = { it.id }) { payout ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("$${payout.payoutAmount}") },
                            supportingContent = { Text(payout.createdAt ?: "N/A") },
                            trailingContent = {
                                if (payout.status == "PENDING") {
                                    Button(onClick = { onMarkPaid(payout.id) }) {
                                        Text("Mark Paid")
                                    }
                                } else {
                                    Text("PAID", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { /* Implement Detach */ },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Detach from School")
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorRegistrationScreen(onRegister: (com.sroadtutor.data.remote.models.InstructorCreateRequest) -> Unit) {
    var licenseNo by remember { mutableStateOf("") }
    var vehicleMake by remember { mutableStateOf("") }
    var vehicleModel by remember { mutableStateOf("") }
    var vehicleYear by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Complete Instructor Profile") }) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = licenseNo, onValueChange = { licenseNo = it }, label = { Text("License Number") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vehicleMake, onValueChange = { vehicleMake = it }, label = { Text("Vehicle Make") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vehicleModel, onValueChange = { vehicleModel = it }, label = { Text("Vehicle Model") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vehicleYear, onValueChange = { vehicleYear = it }, label = { Text("Vehicle Year") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            
            Button(
                onClick = { 
                    onRegister(com.sroadtutor.data.remote.models.InstructorCreateRequest(
                        licenseNo = licenseNo,
                        vehicleMake = vehicleMake,
                        vehicleModel = vehicleModel,
                        vehicleYear = vehicleYear.toIntOrNull(),
                        bio = bio
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register Profile")
            }
        }
    }
}
