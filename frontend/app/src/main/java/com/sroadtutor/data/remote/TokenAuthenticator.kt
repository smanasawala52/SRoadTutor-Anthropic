package com.sroadtutor.data.remote

import com.sroadtutor.data.local.SessionManager
import com.sroadtutor.data.remote.models.RefreshRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val apiService: Lazy<ApiService>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Handle both 401 and 403 as potential token expiration errors
        if (response.code != 401 && response.code != 403) return null

        // Avoid infinite refresh loops: only retry once per request
        if (response.priorResponse != null) return null

        synchronized(this) {
            val currentToken = runBlocking { sessionManager.accessToken.first() }
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            // If the token in SessionManager is already different from the one that failed,
            // it means another request already refreshed it. Just retry with the new one.
            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = runBlocking {
                sessionManager.refreshToken.first()
            } ?: return null

            val newTokenResponse = runBlocking {
                try {
                    apiService.value.refresh(RefreshRequest(refreshToken))
                } catch (e: Exception) {
                    null
                }
            }

            if (newTokenResponse?.isSuccessful == true) {
                val body = newTokenResponse.body()
                if (body != null && body.data != null) {
                    val authData = body.data
                    runBlocking {
                        sessionManager.saveSession(
                            accessToken = authData.accessToken,
                            refreshToken = authData.refreshToken ?: "",
                            userId = authData.user.id,
                            name = authData.user.fullName,
                            role = authData.user.role,
                            emailVerified = authData.user.emailVerified,
                            schoolId = authData.user.schoolId
                        )
                    }
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${authData.accessToken}")
                        .build()
                }
            }
        }

        // If refresh fails or we reach here, we are truly unauthorized
        // Only clear session on 401 (Unauthenticated). 
        // 403 (Forbidden) might just mean the user doesn't have permission for one specific resource.
        if (response.code == 401) {
            runBlocking {
                sessionManager.clearSession()
            }
        }
        return null
    }
}
