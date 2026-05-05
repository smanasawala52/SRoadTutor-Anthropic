package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DashboardResponse(
    @Json(name = "schoolId") val schoolId: String,
    @Json(name = "schoolName") val schoolName: String,
    @Json(name = "planTier") val planTier: String? = null,
    @Json(name = "window") val window: Window? = null,
    @Json(name = "totalRevenuePaid") val totalRevenuePaid: Double,
    @Json(name = "totalOutstanding") val totalOutstanding: Double,
    @Json(name = "activeStudentCount") val activeStudentCount: Long,
    @Json(name = "upcomingSessionsCount") val upcomingSessionsCount: Long,
    @Json(name = "completedSessionsInWindow") val completedSessionsInWindow: Long,
    @Json(name = "monthlyRecurringRevenue") val monthlyRecurringRevenue: Double? = null,
    @Json(name = "instructorWorkloads") val instructorWorkloads: List<InstructorWorkload>? = null
)

@JsonClass(generateAdapter = true)
data class InstructorWorkload(
    @Json(name = "instructorId") val instructorId: String,
    @Json(name = "instructorName") val instructorName: String? = null,
    @Json(name = "scheduledSessionsInWindow") val scheduledSessionsInWindow: Long,
    @Json(name = "completedSessionsInWindow") val completedSessionsInWindow: Long,
    @Json(name = "activeStudentsAssigned") val activeStudentsAssigned: Long
)

@JsonClass(generateAdapter = true)
data class Window(
    @Json(name = "from") val from: String? = null,
    @Json(name = "to") val to: String? = null,
    @Json(name = "days") val days: Int? = null
)
