package com.sroadtutor.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.models.BookSessionRequest
import com.sroadtutor.data.remote.models.InstructorResponse
import com.sroadtutor.data.remote.models.RescheduleSessionRequest
import com.sroadtutor.data.remote.models.SessionResponse
import com.sroadtutor.data.remote.models.StudentResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class SessionsViewModel(private val apiService: ApiService) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionResponse>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** Instructor roster for the booking dropdown — populated for OWNER and INSTRUCTOR. */
    private val _instructorOptions = MutableStateFlow<List<InstructorResponse>>(emptyList())
    val instructorOptions = _instructorOptions.asStateFlow()

    /** Student roster for the booking dropdown — populated for OWNER and INSTRUCTOR. */
    private val _studentOptions = MutableStateFlow<List<StudentResponse>>(emptyList())
    val studentOptions = _studentOptions.asStateFlow()

    // Last query parameters — kept so action endpoints can refresh after a
    // mutation without the screen having to re-issue the query manually.
    private data class LastQuery(
        val start: LocalDate,
        val end: LocalDate,
        val schoolId: String?,
        val instructorId: String?,
        val studentId: String?
    )

    private var lastQuery: LastQuery? = null

    fun loadSessions(
        start: LocalDate,
        end: LocalDate,
        schoolId: String? = null,
        instructorId: String? = null,
        studentId: String? = null
    ) {
        lastQuery = LastQuery(start, end, schoolId, instructorId, studentId)
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiService.getSessions(
                    from = start.toString(),
                    to = end.toString(),
                    schoolId = schoolId.takeIf { !it.isNullOrBlank() },
                    instructorId = instructorId.takeIf { !it.isNullOrBlank() },
                    studentId = studentId.takeIf { !it.isNullOrBlank() }
                )
                if (response.isSuccessful) {
                    _sessions.value = response.body()?.data ?: emptyList()
                } else {
                    _error.value = "Failed to load sessions (HTTP ${response.code()})"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Best-effort fetch of the school's instructor + student rosters so the
     * booking dialog can offer prefilled dropdowns. Failures (403 for non-OWNER,
     * for instance) are swallowed — the dialog falls back to a free-text UUID
     * field automatically when both lists are empty.
     */
    fun loadBookingOptions(schoolId: String?) {
        if (schoolId.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val ir = apiService.getSchoolInstructors(schoolId)
                if (ir.isSuccessful) _instructorOptions.value = ir.body()?.data ?: emptyList()
            } catch (_: Exception) { /* swallow — dropdown will degrade to free-text */ }
            try {
                val sr = apiService.getStudents(schoolId)
                if (sr.isSuccessful) _studentOptions.value = sr.body()?.data ?: emptyList()
            } catch (_: Exception) { /* swallow */ }
        }
    }

    private fun refreshLastQuery() {
        val q = lastQuery ?: return
        loadSessions(q.start, q.end, q.schoolId, q.instructorId, q.studentId)
    }

    fun bookSession(request: BookSessionRequest, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val response = apiService.bookSession(request)
                if (response.isSuccessful) {
                    refreshLastQuery()
                    onResult(true, null)
                } else {
                    val msg = "Booking failed (HTTP ${response.code()})"
                    _error.value = msg
                    onResult(false, msg)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Network error"
                _error.value = msg
                onResult(false, msg)
            }
        }
    }

    fun completeSession(sessionId: String) {
        viewModelScope.launch {
            try {
                if (apiService.completeSession(sessionId).isSuccessful) refreshLastQuery()
                else _error.value = "Could not complete session"
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            }
        }
    }

    fun noShowSession(sessionId: String) {
        viewModelScope.launch {
            try {
                if (apiService.noShowSession(sessionId).isSuccessful) refreshLastQuery()
                else _error.value = "Could not mark no-show"
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            }
        }
    }

    fun cancelSession(sessionId: String) {
        viewModelScope.launch {
            try {
                if (apiService.cancelSession(sessionId).isSuccessful) refreshLastQuery()
                else _error.value = "Could not cancel session"
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            }
        }
    }

    fun rescheduleSession(sessionId: String, scheduledAt: String, durationMins: Int? = null) {
        viewModelScope.launch {
            try {
                val r = apiService.rescheduleSession(
                    sessionId,
                    RescheduleSessionRequest(scheduledAt = scheduledAt, durationMins = durationMins)
                )
                if (r.isSuccessful) refreshLastQuery()
                else _error.value = "Reschedule failed (HTTP ${r.code()})"
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            }
        }
    }

    fun resetError() { _error.value = null }
}
