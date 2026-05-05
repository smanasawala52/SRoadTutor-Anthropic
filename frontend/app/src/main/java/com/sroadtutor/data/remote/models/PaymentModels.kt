package com.sroadtutor.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentResponse(
    @Json(name = "id") val id: String,
    @Json(name = "schoolId") val schoolId: String? = null,
    @Json(name = "studentId") val studentId: String,
    @Json(name = "sessionId") val sessionId: String? = null,
    @Json(name = "amount") val amount: Double,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "method") val method: String? = null,
    @Json(name = "paymentMethod") val paymentMethod: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "paidAt") val paidAt: String? = null,
    @Json(name = "stripePaymentId") val stripePaymentId: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RecordPaymentRequest(
    @Json(name = "studentId") val studentId: String,
    @Json(name = "sessionId") val sessionId: String? = null,
    @Json(name = "amount") val amount: Double,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "method") val method: String? = null,
    @Json(name = "paidAt") val paidAt: String? = null,
    @Json(name = "notes") val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class MarkPaidRequest(
    @Json(name = "method") val method: String,
    @Json(name = "paidAt") val paidAt: String? = null,
    @Json(name = "notes") val notes: String? = null
)
