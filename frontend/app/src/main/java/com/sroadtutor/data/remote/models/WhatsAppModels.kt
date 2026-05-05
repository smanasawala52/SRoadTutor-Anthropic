package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WaMeLinkRequest(
    @Json(name = "recipientPhoneId") val recipientPhoneId: String,
    @Json(name = "templateId") val templateId: String? = null,
    @Json(name = "placeholders") val placeholders: Map<String, String>? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "correlationId") val correlationId: String? = null
)

@JsonClass(generateAdapter = true)
data class WaMeLinkResponse(
    @Json(name = "logId") val logId: String,
    @Json(name = "waMeUrl") val waMeUrl: String,
    @Json(name = "renderedBody") val renderedBody: String? = null,
    @Json(name = "recipientPhoneId") val recipientPhoneId: String? = null,
    @Json(name = "linkGeneratedAt") val linkGeneratedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ClickConfirmResponse(
    @Json(name = "logId") val logId: String,
    @Json(name = "recipientPhoneId") val recipientPhoneId: String? = null,
    @Json(name = "clickedAt") val clickedAt: String? = null,
    @Json(name = "phoneVerifiedNow") val phoneVerifiedNow: Boolean = false,
    @Json(name = "phoneVerifiedAt") val phoneVerifiedAt: String? = null
)
