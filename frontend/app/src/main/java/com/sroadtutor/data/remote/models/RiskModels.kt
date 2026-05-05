package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RiskScoreResponse(
    @Json(name = "id") val id: String,
    @Json(name = "studentAnonymizedHash") val studentAnonymizedHash: String? = null,
    @Json(name = "mistakeProfileJson") val mistakeProfileJson: String? = null,
    @Json(name = "riskTier") val riskTier: String? = null,
    @Json(name = "generatedAt") val generatedAt: String? = null,
    @Json(name = "licensedToInsurer") val licensedToInsurer: String? = null
)

@JsonClass(generateAdapter = true)
data class RiskAggregateResponse(
    @Json(name = "totalDrivers") val totalDrivers: Long,
    @Json(name = "countsByTier") val countsByTier: Map<String, Long>,
    @Json(name = "generatedAt") val generatedAt: String? = null
)
