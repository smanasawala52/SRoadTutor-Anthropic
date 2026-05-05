package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DealershipResponse(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "city") val city: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "crmType") val crmType: String? = null,
    @Json(name = "bountyPerLead") val bountyPerLead: Double? = null,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class DealershipLeadResponse(
    @Json(name = "id") val id: String,
    @Json(name = "studentId") val studentId: String,
    @Json(name = "parentUserId") val parentUserId: String? = null,
    @Json(name = "vehiclePrefJson") val vehiclePrefJson: String? = null,
    @Json(name = "budget") val budget: Double? = null,
    @Json(name = "financingReady") val financingReady: Boolean = false,
    @Json(name = "dealershipId") val dealershipId: String? = null,
    @Json(name = "dealershipName") val dealershipName: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "bountyAmount") val bountyAmount: Double? = null,
    @Json(name = "referralFee") val referralFee: Double? = null,
    @Json(name = "convertedAt") val convertedAt: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SubmitMatchmakerRequest(
    @Json(name = "studentId") val studentId: String,
    @Json(name = "vehiclePreferences") val vehiclePreferences: Map<String, Any>? = null,
    @Json(name = "budget") val budget: Double? = null,
    @Json(name = "financingReady") val financingReady: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class InstructorPayoutResponse(
    @Json(name = "id") val id: String,
    @Json(name = "instructorId") val instructorId: String,
    @Json(name = "leadId") val leadId: String,
    @Json(name = "payoutAmount") val payoutAmount: Double,
    @Json(name = "status") val status: String,
    @Json(name = "eTransferRef") val eTransferRef: String? = null,
    @Json(name = "paidAt") val paidAt: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class MarkPayoutPaidRequest(
    @Json(name = "eTransferRef") val eTransferRef: String? = null
)
