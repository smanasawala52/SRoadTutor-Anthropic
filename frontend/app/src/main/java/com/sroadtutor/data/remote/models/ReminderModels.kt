package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReminderResponse(
    @Json(name = "id") val id: String,
    @Json(name = "sessionId") val sessionId: String? = null,
    @Json(name = "recipientUserId") val recipientUserId: String? = null,
    @Json(name = "channel") val channel: String? = null,
    @Json(name = "reminderKind") val reminderKind: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "scheduledFor") val scheduledFor: String? = null,
    @Json(name = "sentAt") val sentAt: String? = null,
    @Json(name = "failedReason") val failedReason: String? = null,
    @Json(name = "waMeUrl") val waMeUrl: String? = null,
    @Json(name = "renderedBody") val renderedBody: String? = null,
    @Json(name = "recipientPhoneId") val recipientPhoneId: String? = null,
    @Json(name = "waMeLogId") val waMeLogId: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)
