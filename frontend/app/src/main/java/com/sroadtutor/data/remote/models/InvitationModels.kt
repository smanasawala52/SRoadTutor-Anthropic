package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateStudentInvitationRequest(
    @Json(name = "email") val email: String,
    @Json(name = "fullName") val fullName: String,
    @Json(name = "deliveryMode") val deliveryMode: String? = null,
    @Json(name = "instructorId") val instructorId: String? = null,
    @Json(name = "packageTotalLessons") val packageTotalLessons: Int? = null,
    @Json(name = "roadTestDate") val roadTestDate: String? = null,
    @Json(name = "parentEmail") val parentEmail: String? = null,
    @Json(name = "parentFullName") val parentFullName: String? = null,
    @Json(name = "parentRelationship") val parentRelationship: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateInstructorInvitationRequest(
    @Json(name = "email") val email: String,
    @Json(name = "fullName") val fullName: String,
    @Json(name = "deliveryMode") val deliveryMode: String? = null,
    @Json(name = "roleAtSchool") val roleAtSchool: String? = null,
    @Json(name = "licenseNo") val licenseNo: String? = null,
    @Json(name = "sgiCert") val sgiCert: String? = null,
    @Json(name = "vehicleMake") val vehicleMake: String? = null,
    @Json(name = "vehicleModel") val vehicleModel: String? = null,
    @Json(name = "vehicleYear") val vehicleYear: Int? = null,
    @Json(name = "vehiclePlate") val vehiclePlate: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "hourlyRate") val hourlyRate: Double? = null
)

@JsonClass(generateAdapter = true)
data class CreateParentInvitationRequest(
    @Json(name = "email") val email: String,
    @Json(name = "fullName") val fullName: String,
    @Json(name = "deliveryMode") val deliveryMode: String? = null,
    @Json(name = "relationship") val relationship: String? = null,
    @Json(name = "studentIds") val studentIds: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class AcceptInvitationRequest(
    @Json(name = "password") val password: String,
    @Json(name = "languagePref") val languagePref: String? = null
)

@JsonClass(generateAdapter = true)
data class InvitationResponse(
    @Json(name = "id") val id: String,
    @Json(name = "schoolId") val schoolId: String,
    @Json(name = "invitedByUserId") val invitedByUserId: String? = null,
    @Json(name = "email") val email: String,
    @Json(name = "username") val username: String? = null,
    @Json(name = "role") val role: String,
    @Json(name = "deliveryMode") val deliveryMode: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "expiresAt") val expiresAt: String? = null,
    @Json(name = "acceptedAt") val acceptedAt: String? = null,
    @Json(name = "acceptedUserId") val acceptedUserId: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateInvitationResponse(
    @Json(name = "invitationId") val invitationId: String,
    @Json(name = "email") val email: String? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "deliveryMode") val deliveryMode: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "rawToken") val rawToken: String? = null,
    @Json(name = "acceptUrlForDev") val acceptUrlForDev: String? = null,
    @Json(name = "acceptedUserId") val acceptedUserId: String? = null,
    @Json(name = "expiresAt") val expiresAt: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class InvitationLookupResponse(
    @Json(name = "email") val email: String,
    @Json(name = "fullName") val fullName: String? = null,
    @Json(name = "role") val role: String,
    @Json(name = "deliveryMode") val deliveryMode: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "schoolName") val schoolName: String? = null
)
