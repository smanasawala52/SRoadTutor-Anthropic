package com.sroadtutor.ui.invitations

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.data.remote.models.InvitationResponse
import com.sroadtutor.data.remote.models.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationsScreen(
    viewModel: InvitationsViewModel,
    schoolId: String,
    onBack: () -> Unit
) {
    val invitations by viewModel.invitations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    
    var showInviteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(schoolId) {
        viewModel.loadInvitations(schoolId)
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
                title = { Text("Invitations Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadInvitations(schoolId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showInviteDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Invitation")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && invitations.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (invitations.isEmpty()) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MailOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No pending invitations", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(invitations, key = { it.id }) { invitation ->
                        InvitationItem(
                            invitation = invitation,
                            onRevoke = { viewModel.revokeInvitation(schoolId, invitation.id) },
                            onReissue = { viewModel.reissueInvitation(schoolId, invitation.id) }
                        )
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        InviteDialog(
            onDismiss = { showInviteDialog = false },
            onConfirm = { name, email, role, studentId ->
                when (role) {
                    UserRole.STUDENT -> viewModel.inviteStudent(schoolId, name, email)
                    UserRole.INSTRUCTOR -> viewModel.inviteInstructor(schoolId, name, email)
                    UserRole.PARENT -> studentId?.let { viewModel.inviteParent(schoolId, name, email, it) }
                    else -> {}
                }
                showInviteDialog = false
            }
        )
    }
}

@Composable
fun InvitationItem(
    invitation: InvitationResponse,
    onRevoke: () -> Unit,
    onReissue: () -> Unit
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
                    Text(text = invitation.email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Role: ${invitation.role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                StatusChip(status = invitation.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Expires: ${invitation.expiresAt}", style = MaterialTheme.typography.labelSmall)
            
            if (invitation.status == "PENDING") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRevoke) {
                        Text("Revoke", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onReissue) {
                        Text("Reissue")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val color = when (status) {
        "PENDING" -> MaterialTheme.colorScheme.secondaryContainer
        "ACCEPTED" -> MaterialTheme.colorScheme.primaryContainer
        "EXPIRED" -> MaterialTheme.colorScheme.errorContainer
        "REVOKED" -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, UserRole, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite New Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedRole.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(UserRole.STUDENT, UserRole.INSTRUCTOR, UserRole.PARENT).forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.name) },
                                onClick = {
                                    selectedRole = role
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedRole == UserRole.PARENT) {
                    OutlinedTextField(
                        value = studentId, 
                        onValueChange = { studentId = it }, 
                        label = { Text("Student ID") }, 
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Link this parent to a student") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, email, selectedRole, if (selectedRole == UserRole.PARENT) studentId else null) }, 
                enabled = name.isNotBlank() && email.isNotBlank() && (selectedRole != UserRole.PARENT || studentId.isNotBlank())
            ) {
                Text("Send Invitation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
