package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StudentResponse(
    @Json(name = "id") val id: String,
    @Json(name = "userId") val userId: String,
    @Json(name = "schoolId") val schoolId: String,
    @Json(name = "instructorId") val instructorId: String? = null,
    @Json(name = "fullName") val fullName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "packageType") val packageType: String? = null,
    @Json(name = "packageTotalLessons") val packageTotalLessons: Int? = null,
    @Json(name = "lessonsRemaining") val lessonsRemaining: Int? = null,
    @Json(name = "readinessScore") val readinessScore: Double? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "roadTestDate") val roadTestDate: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null,
    @Json(name = "parents") val parents: List<ParentLink>? = null
) {
    /** Convenience: prefer fullName, fall back to email's local-part, then "Student". */
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Student"

    /** Backwards-compat alias used by older screens. */
    val name: String get() = displayName

    /** Backwards-compat alias used by older screens. */
    val isActive: Boolean get() = active
}

@JsonClass(generateAdapter = true)
data class ParentLink(
    @Json(name = "parentUserId") val parentUserId: String,
    @Json(name = "parentEmail") val parentEmail: String? = null,
    @Json(name = "parentFullName") val parentFullName: String? = null,
    @Json(name = "relationship") val relationship: String? = null
)

@JsonClass(generateAdapter = true)
data class StudentUpdateRequest(
    @Json(name = "instructorId") val instructorId: String? = null,
    @Json(name = "packageTotalLessons") val packageTotalLessons: Int? = null,
    @Json(name = "lessonsRemaining") val lessonsRemaining: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "roadTestDate") val roadTestDate: String? = null
)

@JsonClass(generateAdapter = true)
data class AddStudentRequest(
    @Json(name = "studentEmail") val studentEmail: String,
    @Json(name = "studentFullName") val studentFullName: String,
    @Json(name = "languagePref") val languagePref: String? = null,
    @Json(name = "instructorId") val instructorId: String? = null,
    @Json(name = "packageTotalLessons") val packageTotalLessons: Int? = null,
    @Json(name = "lessonsRemaining") val lessonsRemaining: Int? = null,
    @Json(name = "roadTestDate") val roadTestDate: String? = null,
    @Json(name = "parentEmail") val parentEmail: String? = null,
    @Json(name = "parentFullName") val parentFullName: String? = null,
    @Json(name = "parentRelationship") val parentRelationship: String? = null
)

@JsonClass(generateAdapter = true)
data class LinkParentRequest(
    @Json(name = "parentEmail") val parentEmail: String,
    @Json(name = "parentFullName") val parentFullName: String? = null,
    @Json(name = "relationship") val relationship: String? = null
)

@JsonClass(generateAdapter = true)
data class ReadinessScoreResponse(
    @Json(name = "studentId") val studentId: String,
    @Json(name = "sessionsConsidered") val sessionsConsidered: Int,
    @Json(name = "averageScore") val averageScore: Double,
    @Json(name = "anyFailMistakeRecently") val anyFailMistakeRecently: Boolean,
    @Json(name = "perSession") val perSession: List<PerSessionScore>? = null
)

@JsonClass(generateAdapter = true)
data class PerSessionScore(
    @Json(name = "sessionId") val sessionId: String,
    @Json(name = "score") val score: Int,
    @Json(name = "totalDemerits") val totalDemerits: Int,
    @Json(name = "hadFail") val hadFail: Boolean
)

@JsonClass(generateAdapter = true)
data class StudentLedgerResponse(
    @Json(name = "studentId") val studentId: String,
    @Json(name = "totalPaid") val totalPaid: Double,
    @Json(name = "totalOutstanding") val totalOutstanding: Double,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "payments") val payments: List<PaymentResponse>? = null
)
