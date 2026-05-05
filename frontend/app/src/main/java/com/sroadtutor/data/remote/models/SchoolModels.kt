package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SchoolResponse(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "ownerId") val ownerId: String,
    @Json(name = "planTier") val planTier: String? = null,
    @Json(name = "stripeCustomerId") val stripeCustomerId: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "jurisdiction") val jurisdiction: String? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "gstNumber") val gstNumber: String? = null,
    @Json(name = "pstNumber") val pstNumber: String? = null,
    @Json(name = "hstNumber") val hstNumber: String? = null,
    @Json(name = "businessRegistrationNumber") val businessRegistrationNumber: String? = null,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "synthetic") val synthetic: Boolean = false,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SchoolCreateRequest(
    @Json(name = "name") val name: String,
    @Json(name = "jurisdiction") val jurisdiction: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "gstNumber") val gstNumber: String? = null,
    @Json(name = "pstNumber") val pstNumber: String? = null,
    @Json(name = "hstNumber") val hstNumber: String? = null,
    @Json(name = "businessRegistrationNumber") val businessRegistrationNumber: String? = null
)

@JsonClass(generateAdapter = true)
data class SchoolUpdateRequest(
    @Json(name = "name") val name: String? = null,
    @Json(name = "jurisdiction") val jurisdiction: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "gstNumber") val gstNumber: String? = null,
    @Json(name = "pstNumber") val pstNumber: String? = null,
    @Json(name = "hstNumber") val hstNumber: String? = null,
    @Json(name = "businessRegistrationNumber") val businessRegistrationNumber: String? = null
)

@JsonClass(generateAdapter = true)
data class SchoolMeResponse(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "jurisdiction") val jurisdiction: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "planTier") val planTier: String? = null,
    @Json(name = "active") val active: Boolean = true
)
