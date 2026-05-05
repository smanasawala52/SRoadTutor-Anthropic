package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class InstructorResponse(
    @Json(name = "id") val id: String,
    @Json(name = "userId") val userId: String,
    @Json(name = "schoolId") val schoolId: String? = null,
    @Json(name = "fullName") val fullName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "licenseNo") val licenseNo: String? = null,
    @Json(name = "sgiCert") val sgiCert: String? = null,
    @Json(name = "vehicleMake") val vehicleMake: String? = null,
    @Json(name = "vehicleModel") val vehicleModel: String? = null,
    @Json(name = "vehicleYear") val vehicleYear: Int? = null,
    @Json(name = "vehiclePlate") val vehiclePlate: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "hourlyRate") val hourlyRate: Double? = null,
    @Json(name = "workingHours") val workingHours: WorkingHours? = null,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null
) {
    /** Convenience: prefer fullName, fall back to email's local-part, then "Instructor". */
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Instructor"
}

@JsonClass(generateAdapter = true)
data class WorkingHours(
    @Json(name = "schedule") val schedule: Map<String, List<TimeRange>>? = null
)

@JsonClass(generateAdapter = true)
data class TimeRange(
    @Json(name = "start") val start: LocalTimeDto? = null,
    @Json(name = "end") val end: LocalTimeDto? = null
)

@JsonClass(generateAdapter = true)
data class LocalTimeDto(
    @Json(name = "hour") val hour: Int,
    @Json(name = "minute") val minute: Int,
    @Json(name = "second") val second: Int = 0,
    @Json(name = "nano") val nano: Int = 0
)

@JsonClass(generateAdapter = true)
data class InstructorCreateRequest(
    @Json(name = "licenseNo") val licenseNo: String? = null,
    @Json(name = "sgiCert") val sgiCert: String? = null,
    @Json(name = "vehicleMake") val vehicleMake: String? = null,
    @Json(name = "vehicleModel") val vehicleModel: String? = null,
    @Json(name = "vehicleYear") val vehicleYear: Int? = null,
    @Json(name = "vehiclePlate") val vehiclePlate: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "hourlyRate") val hourlyRate: Double? = null,
    @Json(name = "workingHours") val workingHours: WorkingHours? = null
)

@JsonClass(generateAdapter = true)
data class InstructorUpdateRequest(
    @Json(name = "licenseNo") val licenseNo: String? = null,
    @Json(name = "sgiCert") val sgiCert: String? = null,
    @Json(name = "vehicleMake") val vehicleMake: String? = null,
    @Json(name = "vehicleModel") val vehicleModel: String? = null,
    @Json(name = "vehicleYear") val vehicleYear: Int? = null,
    @Json(name = "vehiclePlate") val vehiclePlate: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "hourlyRate") val hourlyRate: Double? = null,
    @Json(name = "workingHours") val workingHours: WorkingHours? = null
)

@JsonClass(generateAdapter = true)
data class AttachInstructorRequest(
    @Json(name = "roleAtSchool") val roleAtSchool: String? = null
)
