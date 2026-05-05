package com.sroadtutor.data.remote

import com.sroadtutor.data.remote.models.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/google")
    suspend fun googleLogin(@Body request: OAuthLoginRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/email-verify/send")
    suspend fun sendEmailVerification(): Response<ApiResponse<EmailVerifyResponse>>

    @POST("auth/email-verify/{token}/confirm")
    suspend fun confirmEmailVerification(@Path("token") token: String): Response<ApiResponse<EmailVerifyConfirmResponse>>

    // Dashboard
    @GET("api/dashboard/owner")
    suspend fun getOwnerDashboard(): Response<ApiResponse<DashboardResponse>>

    // Schools
    @POST("api/schools")
    suspend fun createSchool(@Body request: SchoolCreateRequest): Response<ApiResponse<SchoolResponse>>

    @GET("api/schools/me")
    suspend fun getMySchool(): Response<ApiResponse<SchoolMeResponse>>

    @GET("api/schools/{id}")
    suspend fun getSchool(@Path("id") id: String): Response<ApiResponse<SchoolResponse>>

    @PUT("api/schools/{id}")
    suspend fun updateSchool(@Path("id") id: String, @Body request: SchoolUpdateRequest): Response<ApiResponse<SchoolResponse>>

    @POST("api/schools/{id}/reactivate")
    suspend fun reactivateSchool(@Path("id") id: String): Response<ApiResponse<SchoolResponse>>

    @POST("api/schools/{id}/deactivate")
    suspend fun deactivateSchool(@Path("id") id: String): Response<ApiResponse<SchoolResponse>>

    // Instructors
    @GET("api/instructors/me")
    suspend fun getMyInstructorProfile(): Response<ApiResponse<InstructorResponse>>

    @POST("api/instructors/me")
    suspend fun registerInstructor(@Body request: InstructorCreateRequest): Response<ApiResponse<InstructorResponse>>

    @GET("api/schools/{schoolId}/instructors")
    suspend fun getSchoolInstructors(@Path("schoolId") schoolId: String): Response<ApiResponse<List<InstructorResponse>>>

    @POST("api/schools/{schoolId}/instructors/{instructorId}/attach")
    suspend fun attachInstructor(
        @Path("schoolId") schoolId: String,
        @Path("instructorId") instructorId: String,
        @Body request: AttachInstructorRequest? = null
    ): Response<Unit>

    @POST("api/schools/{schoolId}/instructors/{instructorId}/detach")
    suspend fun detachInstructor(
        @Path("schoolId") schoolId: String,
        @Path("instructorId") instructorId: String
    ): Response<Unit>

    @GET("api/instructors/{id}")
    suspend fun getInstructor(@Path("id") id: String): Response<ApiResponse<InstructorResponse>>

    @PUT("api/instructors/{id}")
    suspend fun updateInstructor(@Path("id") id: String, @Body request: InstructorUpdateRequest): Response<ApiResponse<InstructorResponse>>

    @POST("api/instructors/{id}/deactivate")
    suspend fun deactivateInstructor(@Path("id") id: String): Response<ApiResponse<InstructorResponse>>

    // Students
    @GET("api/schools/{schoolId}/students")
    suspend fun getStudents(@Path("schoolId") schoolId: String): Response<ApiResponse<List<StudentResponse>>>

    @POST("api/schools/{schoolId}/students")
    suspend fun addStudent(@Path("schoolId") schoolId: String, @Body request: AddStudentRequest): Response<ApiResponse<StudentResponse>>

    @GET("api/students/me")
    suspend fun getMyStudentProfile(): Response<ApiResponse<StudentResponse>>

    @GET("api/students/{id}")
    suspend fun getStudent(@Path("id") id: String): Response<ApiResponse<StudentResponse>>

    @PUT("api/students/{id}")
    suspend fun updateStudent(@Path("id") id: String, @Body request: StudentUpdateRequest): Response<ApiResponse<StudentResponse>>

    @GET("api/students/{id}/readiness-score")
    suspend fun getStudentReadiness(@Path("id") id: String): Response<ApiResponse<ReadinessScoreResponse>>

    @GET("api/students/{id}/payments")
    suspend fun getStudentLedger(@Path("id") id: String): Response<ApiResponse<StudentLedgerResponse>>

    @GET("api/students/{id}/mistakes")
    suspend fun getStudentMistakes(@Path("id") id: String): Response<ApiResponse<List<SessionMistakeResponse>>>

    @POST("api/students/{id}/parents")
    suspend fun linkParent(@Path("id") id: String, @Body request: LinkParentRequest): Response<ApiResponse<StudentResponse>>

    @DELETE("api/students/{id}/parents/{parentUserId}")
    suspend fun unlinkParent(@Path("id") id: String, @Path("parentUserId") parentUserId: String): Response<Unit>

    // Sessions
    @GET("api/sessions")
    suspend fun getSessions(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("schoolId") schoolId: String? = null,
        @Query("instructorId") instructorId: String? = null,
        @Query("studentId") studentId: String? = null
    ): Response<ApiResponse<List<SessionResponse>>>

    @POST("api/sessions")
    suspend fun bookSession(@Body request: BookSessionRequest): Response<ApiResponse<SessionResponse>>

    @GET("api/sessions/{id}")
    suspend fun getSession(@Path("id") id: String): Response<ApiResponse<SessionResponse>>

    @PUT("api/sessions/{id}")
    suspend fun rescheduleSession(@Path("id") id: String, @Body request: RescheduleSessionRequest): Response<ApiResponse<SessionResponse>>

    @POST("api/sessions/{id}/complete")
    suspend fun completeSession(@Path("id") id: String): Response<ApiResponse<SessionResponse>>

    @POST("api/sessions/{id}/cancel")
    suspend fun cancelSession(@Path("id") id: String): Response<ApiResponse<SessionResponse>>

    @POST("api/sessions/{id}/no-show")
    suspend fun noShowSession(@Path("id") id: String): Response<ApiResponse<SessionResponse>>

    // Mistakes
    @GET("api/sessions/{sessionId}/mistakes")
    suspend fun getSessionMistakes(@Path("sessionId") sessionId: String): Response<ApiResponse<List<SessionMistakeResponse>>>

    @POST("api/sessions/{sessionId}/mistakes")
    suspend fun logMistake(@Path("sessionId") sessionId: String, @Body request: LogMistakeRequest): Response<ApiResponse<SessionMistakeResponse>>

    @GET("api/mistakes/categories/{jurisdiction}")
    suspend fun getMistakeCategories(@Path("jurisdiction") jurisdiction: String): Response<ApiResponse<List<MistakeCategoryResponse>>>

    // Telemetry (Phase 3)
    @GET("api/telemetry/mistakes/{sessionMistakeId}/events")
    suspend fun getMistakeTelemetry(@Path("sessionMistakeId") sessionMistakeId: String): Response<ApiResponse<List<TelemetryEventResponse>>>

    @POST("api/telemetry/mistakes/{sessionMistakeId}/events")
    suspend fun attachTelemetry(@Path("sessionMistakeId") sessionMistakeId: String, @Body request: AttachTelemetryRequest): Response<ApiResponse<TelemetryEventResponse>>

    @GET("api/telemetry/dataset/summary")
    suspend fun getTelemetrySummary(): Response<ApiResponse<TelemetryDatasetSummary>>

    // Payments
    @POST("api/payments")
    suspend fun recordPayment(@Body request: RecordPaymentRequest): Response<ApiResponse<PaymentResponse>>

    @GET("api/payments/{id}")
    suspend fun getPayment(@Path("id") id: String): Response<ApiResponse<PaymentResponse>>

    @PUT("api/payments/{id}/mark-paid")
    suspend fun markPaymentPaid(@Path("id") id: String, @Body request: MarkPaidRequest): Response<ApiResponse<PaymentResponse>>

    @GET("api/schools/{id}/payments/outstanding")
    suspend fun getOutstandingPayments(@Path("id") id: String): Response<ApiResponse<List<PaymentResponse>>>

    // Invitations
    @GET("api/schools/{schoolId}/invitations")
    suspend fun getSchoolInvitations(@Path("schoolId") schoolId: String, @Query("status") status: String? = null): Response<ApiResponse<List<InvitationResponse>>>

    @POST("api/schools/{schoolId}/invitations/student")
    suspend fun inviteStudent(@Path("schoolId") schoolId: String, @Body request: CreateStudentInvitationRequest): Response<ApiResponse<CreateInvitationResponse>>

    @POST("api/schools/{schoolId}/invitations/instructor")
    suspend fun inviteInstructor(@Path("schoolId") schoolId: String, @Body request: CreateInstructorInvitationRequest): Response<ApiResponse<CreateInvitationResponse>>

    @POST("api/schools/{schoolId}/invitations/parent")
    suspend fun inviteParent(@Path("schoolId") schoolId: String, @Body request: CreateParentInvitationRequest): Response<ApiResponse<CreateInvitationResponse>>

    @POST("api/invitations/{id}/revoke")
    suspend fun revokeInvitation(@Path("id") id: String): Response<ApiResponse<InvitationResponse>>

    @POST("api/invitations/{id}/reissue")
    suspend fun reissueInvitation(@Path("id") id: String): Response<ApiResponse<CreateInvitationResponse>>

    @GET("api/invitations/lookup/{token}")
    suspend fun lookupInvitation(@Path("token") token: String): Response<ApiResponse<InvitationLookupResponse>>

    @POST("api/invitations/{token}/accept")
    suspend fun acceptInvitation(@Path("token") token: String, @Body request: AcceptInvitationRequest): Response<ApiResponse<CreateInvitationResponse>>

    // WhatsApp
    @POST("api/whatsapp/links")
    suspend fun generateWhatsAppLink(@Body request: WaMeLinkRequest): Response<ApiResponse<WaMeLinkResponse>>

    @POST("api/whatsapp/links/{logId}/click-confirm")
    suspend fun confirmWhatsAppClick(@Path("logId") logId: String): Response<ApiResponse<ClickConfirmResponse>>

    // Phone Numbers
    @GET("api/phone-numbers")
    suspend fun getPhoneNumbers(@Query("ownerType") ownerType: String?, @Query("ownerId") ownerId: String?): Response<ApiResponse<List<PhoneNumberResponse>>>

    @POST("api/phone-numbers")
    suspend fun createPhoneNumber(@Body request: PhoneNumberRequest): Response<ApiResponse<PhoneNumberResponse>>

    @GET("api/phone-numbers/{id}")
    suspend fun getPhoneNumber(@Path("id") id: String): Response<ApiResponse<PhoneNumberResponse>>

    @PUT("api/phone-numbers/{id}")
    suspend fun updatePhoneNumber(@Path("id") id: String, @Body request: PhoneNumberUpdateRequest): Response<ApiResponse<PhoneNumberResponse>>

    @DELETE("api/phone-numbers/{id}")
    suspend fun deletePhoneNumber(@Path("id") id: String): Response<Unit>

    @POST("api/phone-numbers/{id}/whatsapp-optin")
    suspend fun toggleWhatsAppOptIn(@Path("id") id: String, @Body request: WhatsappOptInRequest): Response<ApiResponse<PhoneNumberResponse>>

    @POST("api/phone-numbers/{id}/primary")
    suspend fun setPrimaryPhoneNumber(@Path("id") id: String): Response<ApiResponse<PhoneNumberResponse>>

    // Reports
    @Streaming
    @GET("api/students/{id}/report-card.pdf")
    suspend fun getStudentReportCard(@Path("id") id: String): Response<ResponseBody>

    // Subscriptions
    @GET("api/subscriptions/plans")
    suspend fun getSubscriptionPlans(): Response<ApiResponse<PlanCatalogResponse>>

    @GET("api/subscriptions/me")
    suspend fun getMySubscription(): Response<ApiResponse<SubscriptionMeResponse>>

    @POST("api/subscriptions/upgrade")
    suspend fun upgradeSubscription(@Body request: UpgradeRequest): Response<ApiResponse<UpgradeResponse>>

    // Marketplace / Lead Routing
    @GET("api/marketplace/dealerships")
    suspend fun getDealerships(): Response<ApiResponse<List<DealershipResponse>>>

    @GET("api/marketplace/schools/{schoolId}/leads")
    suspend fun getSchoolMarketplaceLeads(@Path("schoolId") schoolId: String): Response<ApiResponse<List<DealershipLeadResponse>>>

    @GET("api/marketplace/matchmaker/me")
    suspend fun getMyMarketplaceLeads(): Response<ApiResponse<List<DealershipLeadResponse>>>

    @POST("api/marketplace/matchmaker")
    suspend fun submitMatchmaker(@Body request: SubmitMatchmakerRequest): Response<ApiResponse<DealershipLeadResponse>>

    @POST("api/marketplace/leads/{id}/convert")
    suspend fun convertMarketplaceLead(@Path("id") id: String): Response<ApiResponse<DealershipLeadResponse>>

    @GET("api/marketplace/instructors/{instructorId}/payouts")
    suspend fun getInstructorPayouts(@Path("instructorId") instructorId: String): Response<ApiResponse<List<InstructorPayoutResponse>>>

    @POST("api/marketplace/payouts/{id}/mark-paid")
    suspend fun markPayoutPaid(@Path("id") id: String, @Body request: MarkPayoutPaidRequest): Response<ApiResponse<InstructorPayoutResponse>>

    // Insurance
    @GET("api/insurance/brokers")
    suspend fun getInsuranceBrokers(): Response<ApiResponse<List<InsuranceBrokerResponse>>>

    @GET("api/insurance/schools/{schoolId}/leads")
    suspend fun getSchoolInsuranceLeads(@Path("schoolId") schoolId: String): Response<ApiResponse<List<InsuranceLeadResponse>>>

    @POST("api/insurance/leads/{id}/quote")
    suspend fun markInsuranceQuoted(@Path("id") id: String): Response<ApiResponse<InsuranceLeadResponse>>

    @POST("api/insurance/leads/{id}/convert")
    suspend fun markInsuranceConverted(@Path("id") id: String): Response<ApiResponse<InsuranceLeadResponse>>

    @POST("api/insurance/leads/{id}/dead")
    suspend fun markInsuranceDead(@Path("id") id: String): Response<ApiResponse<InsuranceLeadResponse>>

    // Reminders
    @GET("api/reminders/session/{sessionId}")
    suspend fun getSessionReminders(@Path("sessionId") sessionId: String): Response<ApiResponse<List<ReminderResponse>>>

    @GET("api/reminders/me/pending")
    suspend fun getMyPendingReminders(): Response<ApiResponse<List<ReminderResponse>>>

    @POST("api/reminders/{id}/fire")
    suspend fun fireReminder(@Path("id") id: String): Response<ApiResponse<ReminderResponse>>

    // Risk (Phase 3)
    @GET("api/risk/aggregate")
    suspend fun getRiskAggregate(): Response<ApiResponse<RiskAggregateResponse>>

    @POST("api/risk/scores/students/{studentId}/generate")
    suspend fun generateRiskScore(@Path("studentId") studentId: String): Response<ApiResponse<RiskScoreResponse>>

    @GET("api/risk/scores/{hash}")
    suspend fun getRiskScoreByHash(@Path("hash") hash: String): Response<ApiResponse<RiskScoreResponse>>
}
