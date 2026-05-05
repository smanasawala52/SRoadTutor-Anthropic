package com.sroadtutor.data.remote

import com.sroadtutor.data.local.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        
        // Don't add auth header for public endpoints
        if (path.contains("auth/login") || 
            path.contains("auth/signup") || 
            path.contains("auth/refresh") ||
            path.contains("auth/google") ||
            path.contains("auth/email-verify") ||
            path.contains("api/invitations/lookup") ||
            path.contains("api/invitations/") && path.endsWith("/accept")
        ) {
            return chain.proceed(request)
        }

        val token = runBlocking {
            sessionManager.accessToken.first()
        }
        
        val newRequest = request.newBuilder()
        if (token != null) {
            newRequest.addHeader("Authorization", "Bearer $token")
        }
        return chain.proceed(newRequest.build())
    }
}
