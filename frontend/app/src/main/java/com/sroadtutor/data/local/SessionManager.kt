package com.sroadtutor.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sroadtutor.data.remote.models.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session_prefs")

class SessionManager(private val context: Context) {

    companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ROLE = stringPreferencesKey("user_role")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val EMAIL_VERIFIED = booleanPreferencesKey("email_verified")
        val SCHOOL_ID = stringPreferencesKey("school_id")
        val INSTRUCTOR_ID = stringPreferencesKey("instructor_id")
        val STUDENT_ID = stringPreferencesKey("student_id")
    }

    suspend fun saveSession(
        accessToken: String, 
        refreshToken: String?, 
        userId: String, 
        name: String, 
        role: UserRole,
        emailVerified: Boolean,
        schoolId: String? = null,
        instructorId: String? = null,
        studentId: String? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = accessToken
            refreshToken?.let { preferences[REFRESH_TOKEN] = it }
            preferences[USER_ID] = userId
            preferences[USER_NAME] = name
            preferences[USER_ROLE] = role.name
            preferences[EMAIL_VERIFIED] = emailVerified
            schoolId?.let { preferences[SCHOOL_ID] = it }
            instructorId?.let { preferences[INSTRUCTOR_ID] = it }
            studentId?.let { preferences[STUDENT_ID] = it }
        }
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN]
    }

    val refreshToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_TOKEN]
    }

    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID]
    }

    val userRole: Flow<UserRole?> = context.dataStore.data.map { preferences ->
        preferences[USER_ROLE]?.let { UserRole.valueOf(it) }
    }

    val emailVerified: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[EMAIL_VERIFIED] ?: false
    }

    val schoolId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SCHOOL_ID]
    }

    val instructorId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[INSTRUCTOR_ID]
    }

    val studentId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[STUDENT_ID]
    }

    suspend fun setInstructorId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[INSTRUCTOR_ID] = id
        }
    }

    suspend fun setStudentId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[STUDENT_ID] = id
        }
    }

    suspend fun setEmailVerified(verified: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[EMAIL_VERIFIED] = verified
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
