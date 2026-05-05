package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PhoneNumberResponse(
    @Json(name = "id") val id: String,
    @Json(name = "ownerType") val ownerType: String,
    @Json(name = "ownerId") val ownerId: String,
    @Json(name = "countryCode") val countryCode: String? = null,
    @Json(name = "nationalNumber") val nationalNumber: String? = null,
    @Json(name = "e164") val e164: String? = null,
    @Json(name = "label") val label: String? = null,
    @Json(name = "primary") val primary: Boolean = false,
    @Json(name = "whatsapp") val whatsapp: Boolean = false,
    @Json(name = "whatsappOptIn") val whatsappOptIn: Boolean = false,
    @Json(name = "verifiedAt") val verifiedAt: String? = null,
    @Json(name = "verified") val verified: Boolean = false,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class PhoneNumberRequest(
    @Json(name = "ownerType") val ownerType: String,
    @Json(name = "ownerId") val ownerId: String,
    @Json(name = "countryCode") val countryCode: String,
    @Json(name = "nationalNumber") val nationalNumber: String,
    @Json(name = "label") val label: String? = null,
    @Json(name = "isWhatsapp") val isWhatsapp: Boolean? = null,
    @Json(name = "whatsappOptIn") val whatsappOptIn: Boolean? = null,
    @Json(name = "makePrimary") val makePrimary: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class PhoneNumberUpdateRequest(
    @Json(name = "countryCode") val countryCode: String? = null,
    @Json(name = "nationalNumber") val nationalNumber: String? = null,
    @Json(name = "label") val label: String? = null,
    @Json(name = "isWhatsapp") val isWhatsapp: Boolean? = null,
    @Json(name = "whatsappOptIn") val whatsappOptIn: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class WhatsappOptInRequest(
    @Json(name = "optIn") val optIn: Boolean
)
