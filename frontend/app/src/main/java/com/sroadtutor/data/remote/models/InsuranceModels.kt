package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class InsuranceBrokerResponse(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "contactEmail") val contactEmail: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "bountyPerQuote") val bountyPerQuote: Double? = null,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class InsuranceLeadResponse(
    @Json(name = "id") val id: String,
    @Json(name = "studentId") val studentId: String,
    @Json(name = "brokerId") val brokerId: String,
    @Json(name = "status") val status: String,
    @Json(name = "bountyAmount") val bountyAmount: Double? = null,
    @Json(name = "quotedAt") val quotedAt: String? = null,
    @Json(name = "convertedAt") val convertedAt: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)
