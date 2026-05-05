package com.sroadtutor.ui.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.data.remote.models.BookSessionRequest
import com.sroadtutor.data.remote.models.InstructorResponse
import com.sroadtutor.data.remote.models.SessionResponse
import com.sroadtutor.data.remote.models.SessionStatus
import com.sroadtutor.data.remote.models.StudentResponse
import com.sroadtutor.data.remote.models.UserRole
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCalendarScreen(
    viewModel: SessionsViewModel,
    schoolId: String? = null,
    instructorId: String? = null,
    studentId: String? = null,
    userRole: UserRole? = null,
    onLogMistake: (String) -> Unit,
    onViewMistakes: (String) -> Unit
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val instructorOptions by viewModel.instructorOptions.collectAsStateWithLifecycle()
    val studentOptions by viewModel.studentOptions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showBookingDialog by remember { mutableStateOf(false) }
    var sessionToReschedule by remember { mutableStateOf<SessionResponse?>(null) }

    LaunchedEffect(schoolId, instructorId, studentId) {
        // PARENT must supply a studentId to read the calendar; if we don't have
        // one yet we surface a friendly empty state below instead of issuing a
        // call that we know will 400.
        if (userRole == UserRole.PARENT && studentId.isNullOrBlank()) return@LaunchedEffect
        viewModel.loadSessions(
            start = LocalDate.now(),
            end = LocalDate.now().plusDays(7),
            schoolId = schoolId,
            instructorId = instructorId,
            studentId = studentId
        )
        // Pre-warm the booking dropdowns. OWNER + INSTRUCTOR get a populated
        // list; STUDENT/PARENT calls 403 silently and the dialog falls back
        // to a free-text UUID field.
        viewModel.loadBookingOptions(schoolId)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetError()
        }
    }

    if (showBookingDialog) {
        BookSessionDialog(
            instructorOptions = instructorOptions,
            studentOptions = studentOptions,
            defaultStudentId = studentId.orEmpty(),
            defaultInstructorId = instructorId.orEmpty(),
            onDismiss = { showBookingDialog = false },
            onConfirm = { req ->
                viewModel.bookSession(req) { ok, msg ->
                    if (ok) {
                        showBookingDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Session booked") }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(msg ?: "Booking failed") }
                    }
                }
            }
        )
    }

    sessionToReschedule?.let { s ->
        RescheduleSessionDialog(
            session = s,
            onDismiss = { sessionToReschedule = null },
            onConfirm = { isoInstant, durationMins ->
                viewModel.rescheduleSession(s.id, isoInstant, durationMins)
                sessionToReschedule = null
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Only OWNER / INSTRUCTOR / STUDENT may book. PARENT is read-only.
            if (userRole != UserRole.PARENT) {
                FloatingActionButton(onClick = { showBookingDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Book Session")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                userRole == UserRole.PARENT && studentId.isNullOrBlank() -> {
                    Text(
                        "Open a child's profile to view their calendar.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                sessions.isEmpty() -> {
                    Text(
                        "No sessions in the next 7 days.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Session Calendar",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        items(sessions, key = { it.id }) { session ->
                            SessionItem(
                                session = session,
                                userRole = userRole,
                                onComplete = { viewModel.completeSession(session.id) },
                                onNoShow = { viewModel.noShowSession(session.id) },
                                onCancel = { viewModel.cancelSession(session.id) },
                                onReschedule = { sessionToReschedule = session },
                                onLogMistake = { onLogMistake(session.id) },
                                onViewMistakes = { onViewMistakes(session.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: SessionResponse,
    userRole: UserRole?,
    onComplete: () -> Unit,
    onNoShow: () -> Unit,
    onCancel: () -> Unit,
    onReschedule: () -> Unit,
    onLogMistake: () -> Unit,
    onViewMistakes: () -> Unit
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
                Text(
                    text = session.studentName ?: "Student",
                    style = MaterialTheme.typography.titleLarge
                )
                StatusChip(session.status)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Instructor: ${session.instructorName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Time: ${session.scheduledAt}", style = MaterialTheme.typography.bodySmall)

            val canActOnSession = userRole == UserRole.OWNER || userRole == UserRole.INSTRUCTOR
            if (session.status == SessionStatus.SCHEDULED.name) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canActOnSession) {
                        TextButton(onClick = onLogMistake) { Text("Log Mistakes") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onReschedule) { Text("Reschedule") }
                        Spacer(Modifier.width(4.dp))
                    }
                    TextButton(onClick = onViewMistakes) { Text("View Mistakes") }
                    if (canActOnSession) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onNoShow) {
                            Text("No-Show", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onComplete) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Complete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status) {
        SessionStatus.SCHEDULED.name -> MaterialTheme.colorScheme.primaryContainer
        SessionStatus.COMPLETED.name -> MaterialTheme.colorScheme.secondaryContainer
        SessionStatus.CANCELLED.name -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Booking dialog. Behaviour:
 *  - When [instructorOptions] / [studentOptions] are non-empty, render
 *    Material3 ExposedDropdownMenuBox dropdowns with each entry's
 *    displayName + email — first item selected by default.
 *  - When the lists are empty (e.g. STUDENT role got 403 listing the
 *    school's roster), fall back to a plain UUID text field so the user
 *    can still book by pasting an id.
 *  - [defaultInstructorId] / [defaultStudentId] (from caller's role
 *    bootstrap) override the "first item" default when present.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSessionDialog(
    instructorOptions: List<InstructorResponse>,
    studentOptions: List<StudentResponse>,
    defaultStudentId: String,
    defaultInstructorId: String,
    onDismiss: () -> Unit,
    onConfirm: (BookSessionRequest) -> Unit
) {
    // Resolve the initial selection: pick the matching entry from the list if
    // the caller pre-supplied an id, otherwise default to the first row.
    val initialInstructor = remember(instructorOptions, defaultInstructorId) {
        instructorOptions.firstOrNull { it.id == defaultInstructorId }
            ?: instructorOptions.firstOrNull()
    }
    val initialStudent = remember(studentOptions, defaultStudentId) {
        studentOptions.firstOrNull { it.id == defaultStudentId }
            ?: studentOptions.firstOrNull()
    }

    var selectedInstructor by remember { mutableStateOf(initialInstructor) }
    var selectedStudent by remember { mutableStateOf(initialStudent) }

    // Fallback free-text fields when the rosters are empty (STUDENT/PARENT role).
    var instructorIdField by remember { mutableStateOf(defaultInstructorId) }
    var studentIdField by remember { mutableStateOf(defaultStudentId) }

    // Default to "tomorrow at 10:00 UTC" so the dialog opens with a valid future time.
    val tomorrow10 = remember {
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
            .withHour(10).withMinute(0).withSecond(0).withNano(0)
            .toString()
    }
    var scheduledAtField by remember { mutableStateOf(tomorrow10) }
    var durationField by remember { mutableStateOf("60") }
    var locationField by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (instructorOptions.isNotEmpty()) {
                    DropdownPicker(
                        label = "Instructor",
                        options = instructorOptions,
                        selected = selectedInstructor,
                        onSelected = { selectedInstructor = it },
                        labelOf = { i -> "${i.displayName}${i.email?.let { " · $it" } ?: ""}" }
                    )
                } else {
                    OutlinedTextField(
                        value = instructorIdField,
                        onValueChange = { instructorIdField = it },
                        label = { Text("Instructor ID (UUID)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (studentOptions.isNotEmpty()) {
                    DropdownPicker(
                        label = "Student",
                        options = studentOptions,
                        selected = selectedStudent,
                        onSelected = { selectedStudent = it },
                        labelOf = { s -> "${s.displayName}${s.email?.let { " · $it" } ?: ""}" }
                    )
                } else {
                    OutlinedTextField(
                        value = studentIdField,
                        onValueChange = { studentIdField = it },
                        label = { Text("Student ID (UUID)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = scheduledAtField,
                    onValueChange = { scheduledAtField = it },
                    label = { Text("Scheduled At (ISO-8601, e.g. 2026-05-10T10:00:00Z)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationField,
                    onValueChange = { durationField = it.filter(Char::isDigit) },
                    label = { Text("Duration (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = locationField,
                    onValueChange = { locationField = it },
                    label = { Text("Location (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val resolvedInstructorId =
                selectedInstructor?.id ?: instructorIdField.trim()
            val resolvedStudentId =
                selectedStudent?.id ?: studentIdField.trim()
            Button(
                onClick = {
                    onConfirm(
                        BookSessionRequest(
                            instructorId = resolvedInstructorId,
                            studentId = resolvedStudentId,
                            scheduledAt = scheduledAtField.trim(),
                            durationMins = durationField.toIntOrNull() ?: 60,
                            location = locationField.trim().ifBlank { null }
                        )
                    )
                },
                enabled = resolvedInstructorId.isNotBlank() &&
                        resolvedStudentId.isNotBlank() &&
                        scheduledAtField.isNotBlank()
            ) { Text("Book") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Generic Material3 ExposedDropdownMenuBox helper. Picks one entry from
 * [options], renders it via [labelOf]. The hosting composable owns
 * [selected] / [onSelected].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownPicker(
    label: String,
    options: List<T>,
    selected: T?,
    onSelected: (T) -> Unit,
    labelOf: (T) -> String,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            readOnly = true,
            value = selected?.let(labelOf) ?: "",
            onValueChange = { },
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RescheduleSessionDialog(
    session: SessionResponse,
    onDismiss: () -> Unit,
    onConfirm: (String, Int?) -> Unit
) {
    var scheduledAtField by remember { mutableStateOf(session.scheduledAt) }
    var durationField by remember { mutableStateOf(session.durationMins?.toString() ?: "60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = scheduledAtField,
                    onValueChange = { scheduledAtField = it },
                    label = { Text("New Time (ISO-8601)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationField,
                    onValueChange = { durationField = it.filter(Char::isDigit) },
                    label = { Text("Duration (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(scheduledAtField.trim(), durationField.toIntOrNull())
                },
                enabled = scheduledAtField.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
