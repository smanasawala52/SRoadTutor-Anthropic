package com.sroadtutor.ui.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.data.remote.models.PhoneNumberOwnerType
import com.sroadtutor.data.remote.models.PhoneNumberRequest
import com.sroadtutor.data.remote.models.PhoneNumberResponse
import com.sroadtutor.data.remote.models.PhoneNumberUpdateRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNumbersScreen(
    viewModel: PhoneNumbersViewModel,
    ownerId: String,
    ownerType: PhoneNumberOwnerType,
    onBack: () -> Unit
) {
    val phoneNumbers by viewModel.phoneNumbers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var phoneToEdit by remember { mutableStateOf<PhoneNumberResponse?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(ownerId, ownerType) {
        viewModel.loadPhoneNumbers(ownerId, ownerType)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Numbers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadPhoneNumbers(ownerId, ownerType) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Phone Number")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && phoneNumbers.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (phoneNumbers.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No phone numbers added yet", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(phoneNumbers, key = { it.id }) { phone ->
                        PhoneNumberItem(
                            phone = phone,
                            onEdit = { phoneToEdit = it },
                            onDelete = { viewModel.deletePhoneNumber(phone.id, ownerId, ownerType) },
                            onToggleWhatsApp = { viewModel.toggleWhatsApp(phone.id, ownerId, ownerType) },
                            onSetPrimary = { viewModel.setPrimary(phone.id, ownerId, ownerType) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PhoneNumberDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { label, country, national, isPrimary, isWhatsApp ->
                viewModel.addPhoneNumber(
                    PhoneNumberRequest(
                        ownerId = ownerId,
                        ownerType = ownerType.name,
                        label = label,
                        countryCode = country,
                        nationalNumber = national,
                        makePrimary = isPrimary,
                        whatsappOptIn = isWhatsApp
                    )
                ) {
                    showAddDialog = false
                }
            }
        )
    }

    if (phoneToEdit != null) {
        PhoneNumberDialog(
            initialLabel = phoneToEdit!!.label ?: "",
            initialCountryCode = phoneToEdit!!.countryCode ?: "1",
            initialNationalNumber = phoneToEdit!!.nationalNumber ?: "",
            isEdit = true,
            onDismiss = { phoneToEdit = null },
            onConfirm = { label, country, national, _, _ ->
                viewModel.updatePhoneNumber(
                    phoneToEdit!!.id,
                    PhoneNumberUpdateRequest(
                        label = label, 
                        countryCode = country, 
                        nationalNumber = national
                    ),
                    ownerId,
                    ownerType
                )
                phoneToEdit = null
            }
        )
    }
}

@Composable
fun PhoneNumberItem(
    phone: PhoneNumberResponse,
    onEdit: (PhoneNumberResponse) -> Unit,
    onDelete: () -> Unit,
    onToggleWhatsApp: () -> Unit,
    onSetPrimary: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = phone.label ?: "Phone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(text = phone.e164 ?: "+${phone.countryCode ?: ""} ${phone.nationalNumber ?: ""}", style = MaterialTheme.typography.titleLarge)
                }
                Row {
                    if (phone.whatsappOptIn) {
                        Icon(Icons.Default.Chat, contentDescription = "WhatsApp Opted-in", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (phone.primary) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Primary") },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSetPrimary, enabled = !phone.primary) {
                    Icon(Icons.Default.Star, contentDescription = "Set Primary")
                }
                IconButton(onClick = onToggleWhatsApp) {
                    Icon(
                        imageVector = if (phone.whatsappOptIn) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                        contentDescription = "Toggle WhatsApp Opt-in"
                    )
                }
                IconButton(onClick = { onEdit(phone) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun PhoneNumberDialog(
    initialLabel: String = "",
    initialCountryCode: String = "1",
    initialNationalNumber: String = "",
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean, Boolean) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var countryCode by remember { mutableStateOf(initialCountryCode) }
    var nationalNumber by remember { mutableStateOf(initialNationalNumber) }
    var isPrimary by remember { mutableStateOf(false) }
    var isWhatsApp by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Phone Number" else "Add Phone Number") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. Mobile, Office)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it },
                        label = { Text("Code") },
                        modifier = Modifier.width(80.dp)
                    )
                    OutlinedTextField(
                        value = nationalNumber,
                        onValueChange = { nationalNumber = it },
                        label = { Text("Number") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!isEdit) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = isPrimary, onCheckedChange = { isPrimary = it })
                            Text(text = "Set as Primary", style = MaterialTheme.typography.bodyLarge)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = isWhatsApp, onCheckedChange = { isWhatsApp = it })
                            Text(text = "WhatsApp Opt-in", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(label, countryCode, nationalNumber, isPrimary, isWhatsApp) },
                enabled = label.isNotBlank() && nationalNumber.isNotBlank()
            ) {
                Text(if (isEdit) "Update" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
