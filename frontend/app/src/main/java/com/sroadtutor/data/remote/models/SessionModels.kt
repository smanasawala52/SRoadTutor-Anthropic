package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SessionResponse(
    @Json(name = "id") val id: String,
    @Json(name = "schoolId") val schoolId: String,
    @Json(name = "instructorId") val instructorId: String,
    @Json(name = "instructorName") val instructorName: String? = null,
    @Json(name = "studentId") val studentId: String,
    @Json(name = "studentName") val studentName: String? = null,
    @Json(name = "scheduledAt") val scheduledAt: String,
    @Json(name = "startTime") val startTime: String? = null,
    @Json(name = "endAt") val endAt: String? = null,
    @Json(name = "durationMins") val durationMins: Int? = null,
    @Json(name = "status") val status: String,
    @Json(name = "location") val location: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "createdByUserId") val createdByUserId: String? = null,
    @Json(name = "cancelledAt") val cancelledAt: String? = null,
    @Json(name = "cancelledByUserId") val cancelledByUserId: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null
)

enum class SessionStatus {
    @Json(name = "SCHEDULED") SCHEDULED,
    @Json(name = "COMPLETED") COMPLETED,
    @Json(name = "CANCELLED") CANCELLED,
    @Json(name = "NO_SHOW") NO_SHOW
}

@JsonClass(generateAdapter = true)
data class BookSessionRequest(
    @Json(name = "instructorId") val instructorId: String,
    @Json(name = "studentId") val studentId: String,
    @Json(name = "scheduledAt") val scheduledAt: String,
    @Json(name = "durationMins") val durationMins: Int? = null,
    @Json(name = "location") val location: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "forceOutsideHours") val forceOutsideHours: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RescheduleSessionRequest(
    @Json(name = "instructorId") val instructorId: String? = null,
    @Json(name = "scheduledAt") val scheduledAt: String,
    @Json(name = "durationMins") val durationMins: Int? = null,
    @Json(name = "location") val location: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "forceOutsideHours") val forceOutsideHours: Boolean? = null
)
