package com.sroadtutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.sroadtutor.data.local.SessionManager
import com.sroadtutor.di.NetworkModule
import com.sroadtutor.navigation.Route
import com.sroadtutor.ui.risk.RiskScoreScreen
import com.sroadtutor.ui.risk.RiskViewModel
import com.sroadtutor.ui.telemetry.TelemetryScreen
import com.sroadtutor.ui.telemetry.TelemetryViewModel
import com.sroadtutor.ui.MainScaffold
import com.sroadtutor.ui.auth.*
import com.sroadtutor.ui.dashboard.DashboardScreen
import com.sroadtutor.ui.dashboard.DashboardViewModel
import com.sroadtutor.ui.insurance.InsuranceScreen
import com.sroadtutor.ui.insurance.InsuranceViewModel
import com.sroadtutor.ui.invitations.InvitationsScreen
import com.sroadtutor.ui.invitations.InvitationsViewModel
import com.sroadtutor.ui.marketplace.MarketplaceScreen
import com.sroadtutor.ui.marketplace.MarketplaceViewModel
import com.sroadtutor.ui.marketplace.MatchmakerScreen
import com.sroadtutor.ui.phone.PhoneNumbersScreen
import com.sroadtutor.ui.phone.PhoneNumbersViewModel
import com.sroadtutor.ui.reminders.RemindersScreen
import com.sroadtutor.ui.reminders.RemindersViewModel
import com.sroadtutor.ui.instructors.InstructorsScreen
import com.sroadtutor.ui.instructors.InstructorsViewModel
import com.sroadtutor.ui.mistakes.MistakeLoggingScreen
import com.sroadtutor.ui.mistakes.MistakesScreen
import com.sroadtutor.ui.mistakes.MistakesViewModel
import com.sroadtutor.ui.payments.PaymentLedgerScreen
import com.sroadtutor.ui.sessions.SessionCalendarScreen
import com.sroadtutor.ui.sessions.SessionsViewModel
import com.sroadtutor.ui.schools.SchoolsScreen
import com.sroadtutor.ui.schools.SchoolsViewModel
import com.sroadtutor.ui.students.StudentsScreen
import com.sroadtutor.ui.students.StudentsViewModel
import com.sroadtutor.ui.subscriptions.SubscriptionsScreen
import com.sroadtutor.ui.subscriptions.SubscriptionsViewModel
import com.sroadtutor.ui.splash.SplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.ui.theme.SRoadTutorTheme

class MainActivity : ComponentActivity() {
    /**
     * Apply the persisted language preference before any resource is read.
     * AppLocales.wrap synchronously reads DataStore and rebuilds a base
     * Context with the chosen Locale + LayoutDirection, so RTL languages
     * (ar/ur) automatically mirror layouts.
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.sroadtutor.i18n.AppLocales.wrap(newBase))
    }

    /**
     * Extract an invitation token from a deep-link Intent.
     * Supports: sroadtutor://invitations/{token} and https://sroadtutor.com/invitations/{token}.
     * Returns null if the intent isn't an invitation deep-link.
     */
    private fun extractInvitationToken(intent: android.content.Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme == "sroadtutor" && data.host == "invitations") {
            return data.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        if ((data.scheme == "https" || data.scheme == "http") &&
            data.host == "sroadtutor.com" &&
            data.pathSegments.firstOrNull() == "invitations"
        ) {
            return data.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val apiService = NetworkModule.provideApiService(this)
        val sessionManager = SessionManager(this)
        val authViewModel = AuthViewModel(apiService, sessionManager)
        val invitationViewModel = InvitationViewModel(apiService, sessionManager)
        val dashboardViewModel = DashboardViewModel(apiService)
        val sessionsViewModel = SessionsViewModel(apiService)
        val schoolsViewModel = SchoolsViewModel(apiService)
        val instructorsViewModel = InstructorsViewModel(apiService, sessionManager)
        val studentsViewModel = StudentsViewModel(apiService, sessionManager)
        val phoneNumbersViewModel = PhoneNumbersViewModel(apiService)
        val invitationsViewModel = InvitationsViewModel(apiService)
        val subscriptionsViewModel = SubscriptionsViewModel(apiService)
        val remindersViewModel = RemindersViewModel(apiService)
        val marketplaceViewModel = MarketplaceViewModel(apiService)
        val insuranceViewModel = InsuranceViewModel(apiService)
        val riskViewModel = RiskViewModel(apiService)
        val telemetryViewModel = TelemetryViewModel(apiService)
        val mistakesViewModel = MistakesViewModel(apiService)

        // Invitation deep-link: route directly into the invitation flow before
        // doing the splash → auth dance.
        val initialInvitationToken = extractInvitationToken(intent)

        setContent {
            SRoadTutorTheme {
                val initialRoute: Any = initialInvitationToken
                    ?.let { Route.Invitation(it) }
                    ?: Route.Splash
                val backStack = remember { mutableStateListOf<Any>(initialRoute) }
                val userRole by sessionManager.userRole.collectAsStateWithLifecycle(initialValue = null)
                val schoolId by sessionManager.schoolId.collectAsStateWithLifecycle(initialValue = null)
                val userId by sessionManager.userId.collectAsStateWithLifecycle(initialValue = null)
                val instructorIdState by sessionManager.instructorId.collectAsStateWithLifecycle(initialValue = null)
                val studentIdState by sessionManager.studentId.collectAsStateWithLifecycle(initialValue = null)
                val accessToken by sessionManager.accessToken.collectAsStateWithLifecycle(initialValue = null)

                // Watch for session loss to force redirect to login
                LaunchedEffect(accessToken) {
                    if (accessToken == null) {
                        val current = backStack.lastOrNull()
                        if (current != null && 
                            current !is Route.Splash && 
                            current !is Route.Login && 
                            current !is Route.Signup &&
                            current !is Route.Invitation) {
                            backStack.clear()
                            backStack.add(Route.Login)
                        }
                    }
                }

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key: Any ->
                        when (key) {
                            is Route.Splash -> NavEntry(key) {
                                SplashScreen(
                                    sessionManager = sessionManager,
                                    onNavigateToMain = {
                                        backStack.clear()
                                        backStack.add(Route.Dashboard)
                                    },
                                    onNavigateToAuth = {
                                        backStack.clear()
                                        backStack.add(Route.Login)
                                    },
                                    onNavigateToVerification = {
                                        backStack.clear()
                                        backStack.add(Route.EmailVerification)
                                    }
                                )
                            }
                            is Route.Login -> NavEntry(key) {
                                LoginScreen(
                                    viewModel = authViewModel,
                                    onLoginSuccess = {
                                        backStack.clear()
                                        backStack.add(Route.Dashboard)
                                    },
                                    onNavigateToSignup = { backStack.add(Route.Signup) },
                                    onNavigateToVerification = {
                                        backStack.add(Route.EmailVerification)
                                    },
                                    onOpenLanguage = { backStack.add(Route.Language) }
                                )
                            }
                            is Route.Signup -> NavEntry(key) {
                                SignupScreen(
                                    viewModel = authViewModel,
                                    onSignupSuccess = {
                                        backStack.clear()
                                        backStack.add(Route.Dashboard)
                                    },
                                    onNavigateToLogin = { backStack.removeLastOrNull() },
                                    onNavigateToVerification = {
                                        backStack.add(Route.EmailVerification)
                                    }
                                )
                            }
                            is Route.EmailVerification -> NavEntry(key) {
                                EmailVerificationScreen(
                                    viewModel = authViewModel,
                                    onVerificationSuccess = {
                                        backStack.clear()
                                        backStack.add(Route.Dashboard)
                                    },
                                    onBackToLogin = {
                                        backStack.clear()
                                        backStack.add(Route.Login)
                                    }
                                )
                            }
                            is Route.Invitation -> NavEntry(key) {
                                InvitationScreen(
                                    token = key.token,
                                    viewModel = invitationViewModel,
                                    onSuccess = {
                                        backStack.clear()
                                        backStack.add(Route.Login)
                                    }
                                )
                            }
                            is Route.Dashboard -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        DashboardScreen(dashboardViewModel, riskViewModel, userRole)
                                    }
                                }
                            }
                            is Route.Sessions -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        // Backend filters by entity IDs (instructor.id / student.id), NOT user_id.
                                        // SessionManager populates instructorId / studentId on profile load.
                                        SessionCalendarScreen(
                                            viewModel = sessionsViewModel,
                                            schoolId = if (userRole == com.sroadtutor.data.remote.models.UserRole.OWNER) schoolId else null,
                                            instructorId = if (userRole == com.sroadtutor.data.remote.models.UserRole.INSTRUCTOR) instructorIdState else null,
                                            studentId = if (userRole == com.sroadtutor.data.remote.models.UserRole.STUDENT) studentIdState else null,
                                            userRole = userRole,
                                            onLogMistake = { sessionId ->
                                                backStack.add(Route.MistakeLogging(sessionId))
                                            },
                                            onViewMistakes = { sessionId ->
                                                backStack.add(Route.MistakesList(sessionId))
                                            }
                                        )
                                    }
                                }
                            }
                            is Route.Schools -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        SchoolsScreen(schoolsViewModel)
                                    }
                                }
                            }
                            is Route.Instructors -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        val sid = schoolId
                                        if (sid.isNullOrBlank()) {
                                            MissingSchoolPlaceholder()
                                        } else {
                                            InstructorsScreen(
                                                viewModel = instructorsViewModel,
                                                apiService = apiService,
                                                schoolId = sid,
                                                onViewPhones = { instructorId ->
                                                    backStack.add(Route.PhoneNumbers(instructorId, com.sroadtutor.data.remote.models.PhoneNumberOwnerType.INSTRUCTOR))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            is Route.Students -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        val sid = schoolId
                                        if (sid.isNullOrBlank()) {
                                            MissingSchoolPlaceholder()
                                        } else {
                                            StudentsScreen(
                                                viewModel = studentsViewModel,
                                                apiService = apiService,
                                                schoolId = sid,
                                                onViewPayments = { studentId ->
                                                    backStack.add(Route.PaymentLedger(studentId))
                                                },
                                                onViewPhones = { studentId ->
                                                    backStack.add(Route.PhoneNumbers(studentId, com.sroadtutor.data.remote.models.PhoneNumberOwnerType.STUDENT))
                                                },
                                                onViewRiskScore = { studentId ->
                                                    backStack.add(Route.RiskScore(studentId, null))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            is Route.Invitations -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        val sid = schoolId
                                        if (sid.isNullOrBlank()) {
                                            MissingSchoolPlaceholder()
                                        } else {
                                            InvitationsScreen(
                                                viewModel = invitationsViewModel,
                                                schoolId = sid,
                                                onBack = { backStack.removeLastOrNull() }
                                            )
                                        }
                                    }
                                }
                            }
                            is Route.Subscription -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        SubscriptionsScreen(
                                            viewModel = subscriptionsViewModel,
                                            onBack = { backStack.removeLastOrNull() }
                                        )
                                    }
                                }
                            }
                            is Route.Reminders -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        RemindersScreen(
                                            viewModel = remindersViewModel,
                                            onBack = { backStack.removeLastOrNull() }
                                        )
                                    }
                                }
                            }
                            is Route.Marketplace -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        val sid = schoolId
                                        if (sid.isNullOrBlank()) {
                                            MissingSchoolPlaceholder()
                                        } else {
                                            MarketplaceScreen(
                                                viewModel = marketplaceViewModel,
                                                schoolId = sid,
                                                onBack = { backStack.removeLastOrNull() }
                                            )
                                        }
                                    }
                                }
                            }
                            is Route.Matchmaker -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        // Matchmaker is for PARENT/STUDENT to submit a vehicle preference for the student.
                                        // Use the persisted student.id (from /api/students/me) — NOT user.id.
                                        // For PARENT users, studentId may be null until they pick a child; allow blank to trigger UI handling.
                                        MatchmakerScreen(
                                            viewModel = marketplaceViewModel,
                                            studentId = studentIdState ?: "",
                                            onSuccess = { backStack.removeLastOrNull() },
                                            onBack = { backStack.removeLastOrNull() }
                                        )
                                    }
                                }
                            }
                            is Route.Insurance -> NavEntry(key) {
                                MainScaffold(key, userRole, { backStack.clear(); backStack.add(it) }, { authViewModel.logout(); backStack.clear(); backStack.add(Route.Login) }, onOpenLanguage = { backStack.add(Route.Language) }) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        val sid = schoolId
                                        if (sid.isNullOrBlank()) {
                                            MissingSchoolPlaceholder()
                                        } else {
                                            InsuranceScreen(
                                                viewModel = insuranceViewModel,
                                                schoolId = sid,
                                                onBack = { backStack.removeLastOrNull() }
                                            )
                                        }
                                    }
                                }
                            }
                            is Route.MistakeLogging -> NavEntry(key) {
                                MistakeLoggingScreen(
                                    sessionId = key.sessionId,
                                    apiService = apiService,
                                    onLogged = { mistakeId ->
                                        // Navigate to Telemetry attachment for this mistake
                                        backStack.removeLastOrNull()
                                        backStack.add(Route.Telemetry(mistakeId))
                                    }
                                )
                            }
                            is Route.MistakesList -> NavEntry(key) {
                                MistakesScreen(
                                    viewModel = mistakesViewModel,
                                    sessionId = key.sessionId,
                                    onViewTelemetry = { mistakeId ->
                                        backStack.add(Route.Telemetry(mistakeId))
                                    },
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            is Route.RiskScore -> NavEntry(key) {
                                RiskScoreScreen(
                                    viewModel = riskViewModel,
                                    studentId = key.studentId,
                                    hash = key.hash,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            is Route.Telemetry -> NavEntry(key) {
                                TelemetryScreen(
                                    viewModel = telemetryViewModel,
                                    mistakeId = key.mistakeId,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            is Route.PaymentLedger -> NavEntry(key) {
                                PaymentLedgerScreen(
                                    studentId = key.studentId,
                                    apiService = apiService
                                )
                            }
                            is Route.PhoneNumbers -> NavEntry(key) {
                                PhoneNumbersScreen(
                                    viewModel = phoneNumbersViewModel,
                                    ownerId = key.ownerId,
                                    ownerType = key.ownerType,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            is Route.Language -> NavEntry(key) {
                                com.sroadtutor.ui.settings.LanguageScreen(
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            else -> NavEntry(Unit) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Unknown Route")
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MissingSchoolPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("This screen requires a school context. Please re-login or contact your administrator.")
    }
}
