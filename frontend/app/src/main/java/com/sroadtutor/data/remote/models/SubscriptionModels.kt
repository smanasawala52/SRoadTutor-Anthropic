package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubscriptionMeResponse(
    @Json(name = "tier") val tier: String,
    @Json(name = "monthlyPriceCad") val monthlyPriceCad: String? = null,
    @Json(name = "limits") val limits: Limits? = null,
    @Json(name = "usage") val usage: Usage? = null,
    @Json(name = "stripeManaged") val stripeManaged: Boolean = false
)

typealias SubscriptionResponse = SubscriptionMeResponse

@JsonClass(generateAdapter = true)
data class Limits(
    @Json(name = "instructorLimit") val instructorLimit: Int,
    @Json(name = "studentLimit") val studentLimit: Int,
    @Json(name = "phonesPerOwnerLimit") val phonesPerOwnerLimit: Int,
    @Json(name = "waMeMonthlyLimit") val waMeMonthlyLimit: Int
)

@JsonClass(generateAdapter = true)
data class Usage(
    @Json(name = "waMeThisMonth") val waMeThisMonth: Int
)

@JsonClass(generateAdapter = true)
data class PlanCatalogResponse(
    @Json(name = "plans") val plans: List<PlanRow>
)

@JsonClass(generateAdapter = true)
data class PlanRow(
    @Json(name = "tier") val tier: String,
    @Json(name = "monthlyPriceCad") val monthlyPriceCad: String,
    @Json(name = "instructorLimit") val instructorLimit: Int,
    @Json(name = "studentLimit") val studentLimit: Int,
    @Json(name = "phonesPerOwnerLimit") val phonesPerOwnerLimit: Int,
    @Json(name = "waMeMonthlyLimit") val waMeMonthlyLimit: Int
)

@JsonClass(generateAdapter = true)
data class UpgradeRequest(
    @Json(name = "targetPlan") val targetPlan: String
)

@JsonClass(generateAdapter = true)
data class UpgradeResponse(
    @Json(name = "mode") val mode: String? = null,
    @Json(name = "currentPlan") val currentPlan: String? = null,
    @Json(name = "checkoutUrl") val checkoutUrl: String? = null
)
