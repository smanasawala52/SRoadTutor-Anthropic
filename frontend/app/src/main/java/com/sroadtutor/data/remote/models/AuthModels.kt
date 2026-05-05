package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SignupRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "fullName") val fullName: String,
    @Json(name = "role") val role: UserRole,
    @Json(name = "languagePref") val languagePref: String? = null
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    @Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class OAuthLoginRequest(
    @Json(name = "token") val token: String,
    @Json(name = "role") val role: UserRole? = null
)

@JsonClass(generateAdapter = true)
data class LogoutRequest(
    @Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "refreshToken") val refreshToken: String?,
    @Json(name = "accessTokenExpiresInSeconds") val accessTokenExpiresInSeconds: Long? = null,
    @Json(name = "user") val user: UserDto
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id") val id: String,
    @Json(name = "schoolId") val schoolId: String? = null,
    @Json(name = "email") val email: String,
    @Json(name = "username") val username: String? = null,
    @Json(name = "fullName") val fullName: String,
    @Json(name = "role") val role: UserRole,
    @Json(name = "authProvider") val authProvider: AuthProvider? = null,
    @Json(name = "languagePref") val languagePref: String? = null,
    @Json(name = "emailVerified") val emailVerified: Boolean = false,
    @Json(name = "phoneVerified") val phoneVerified: Boolean = false,
    @Json(name = "mustChangePassword") val mustChangePassword: Boolean = false
)

enum class UserRole {
    @Json(name = "OWNER") OWNER,
    @Json(name = "INSTRUCTOR") INSTRUCTOR,
    @Json(name = "STUDENT") STUDENT,
    @Json(name = "PARENT") PARENT
}

enum class AuthProvider {
    @Json(name = "LOCAL") LOCAL,
    @Json(name = "GOOGLE") GOOGLE
}

@JsonClass(generateAdapter = true)
data class EmailVerifyResponse(
    @Json(name = "tokenId") val tokenId: String,
    @Json(name = "rawToken") val rawToken: String? = null,
    @Json(name = "verifyUrlForDev") val verifyUrlForDev: String? = null,
    @Json(name = "expiresAt") val expiresAt: String,
    @Json(name = "reissued") val reissued: Boolean = false
)

@JsonClass(generateAdapter = true)
data class EmailVerifyConfirmResponse(
    @Json(name = "userId") val userId: String,
    @Json(name = "email") val email: String,
    @Json(name = "emailVerifiedAt") val emailVerifiedAt: String
)

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "data") val data: T?,
    @Json(name = "timestamp") val timestamp: String? = null,
    // Keep these for backward compatibility if the backend still sends them but they aren't in Swagger
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null
)
