package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SessionMistakeResponse(
    @Json(name = "id") val id: String,
    @Json(name = "sessionId") val sessionId: String,
    @Json(name = "studentId") val studentId: String? = null,
    @Json(name = "mistakeCategoryId") val mistakeCategoryId: String,
    @Json(name = "categoryName") val categoryName: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "severity") val severity: String? = null,
    @Json(name = "points") val points: Int? = null,
    @Json(name = "count") val count: Int? = null,
    @Json(name = "instructorNotes") val instructorNotes: String? = null,
    @Json(name = "loggedAt") val loggedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class LogMistakeRequest(
    @Json(name = "mistakeCategoryId") val mistakeCategoryId: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "severity") val severity: String? = null,
    @Json(name = "count") val count: Int? = null,
    @Json(name = "instructorNotes") val instructorNotes: String? = null
)

@JsonClass(generateAdapter = true)
data class MistakeCategoryResponse(
    @Json(name = "id") val id: String,
    @Json(name = "jurisdiction") val jurisdiction: String? = null,
    @Json(name = "categoryName") val categoryName: String,
    @Json(name = "severity") val severity: String? = null,
    @Json(name = "displayOrder") val displayOrder: Int? = null,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "points") val points: Int? = null,
    @Json(name = "sourceCode") val sourceCode: String? = null
)

enum class MistakeSeverity {
    @Json(name = "MINOR") MINOR,
    @Json(name = "MAJOR") MAJOR,
    @Json(name = "CRITICAL") CRITICAL
}
