package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AttachTelemetryRequest(
    @Json(name = "vehicleMake") val vehicleMake: String? = null,
    @Json(name = "vehicleModel") val vehicleModel: String? = null,
    @Json(name = "vehicleYear") val vehicleYear: Int? = null,
    @Json(name = "telemetry") val telemetry: Map<String, Any>,
    @Json(name = "offsetMs") val offsetMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class TelemetryEventResponse(
    @Json(name = "id") val id: String,
    @Json(name = "sessionMistakeId") val sessionMistakeId: String,
    @Json(name = "vehicleMake") val vehicleMake: String? = null,
    @Json(name = "vehicleModel") val vehicleModel: String? = null,
    @Json(name = "vehicleYear") val vehicleYear: Int? = null,
    @Json(name = "telemetryJson") val telemetryJson: String? = null,
    @Json(name = "offsetMs") val offsetMs: Long? = null,
    @Json(name = "syncedAt") val syncedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class TelemetryDatasetSummary(
    @Json(name = "totalEvents") val totalEvents: Long,
    @Json(name = "generatedAt") val generatedAt: String? = null
)
