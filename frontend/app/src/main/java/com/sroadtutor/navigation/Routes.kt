package com.sroadtutor.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Splash : Route
    @Serializable
    data object Login : Route
    @Serializable
    data object Signup : Route
    @Serializable
    data object EmailVerification : Route
    @Serializable
    data class Invitation(val token: String) : Route
    @Serializable
    data object Main : Route

    @Serializable
    data object Dashboard : Route
    @Serializable
    data object Schools : Route
    @Serializable
    data object Instructors : Route
    @Serializable
    data object Students : Route
    
    @Serializable
    data object Sessions : Route

    @Serializable
    data class MistakeLogging(val sessionId: String) : Route
    @Serializable
    data class MistakesList(val sessionId: String) : Route
    @Serializable
    data class PaymentLedger(val studentId: String) : Route
    @Serializable
    data class PhoneNumbers(val ownerId: String, val ownerType: com.sroadtutor.data.remote.models.PhoneNumberOwnerType) : Route
    @Serializable
    data object Invitations : Route
    @Serializable
    data object Subscription : Route
    @Serializable
    data object Reminders : Route
    @Serializable
    data object Marketplace : Route
    @Serializable
    data object Matchmaker : Route
    @Serializable
    data object Insurance : Route

    @Serializable
    data class RiskScore(val studentId: String?, val hash: String?) : Route
    @Serializable
    data class Telemetry(val mistakeId: String) : Route

    @Serializable
    data object Language : Route
}
