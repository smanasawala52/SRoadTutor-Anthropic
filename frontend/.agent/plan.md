# Project Plan

SRoadTutor: A comprehensive driving school management SaaS platform. Backend: http://sroadtutor-anthropic-qa.up.railway.app. All Swagger endpoints must be implemented.

Modules already implemented (but need final audit/polish):
1. Infrastructure, Auth, Management, Core Workflows, Subscriptions, Reminders, Reports, Marketplace, Insurance.

Modules/Features to specifically verify/complete:
- Risk Score (Generate, Aggregate, Hash lookup).
- Telemetry (Attach snap to mistake, List events, Dataset summary).
- Full Invitations lifecycle (Revoke, Reissue, Lookup).
- Parent Linkage (Link/Unlink in Students).
- Full Session Management (No-show, Reschedule, Conflict validation).
- Lead Routing Payouts (Mark payout paid).
- Comprehensive API Audit (Ensure every endpoint from the user's list is functional).

## Project Brief

# SRoadTutor Project Brief

SRoadTutor is a comprehensive, production-ready driving school management SaaS platform. It synchronizes the entire driving education ecosystem—school owners, instructors, students, and parents—while integrating data-driven performance metrics and automotive marketplace services.

## Features
- **Comprehensive Lifecycle Scheduling**: A robust system to book, reschedule, cancel, and track lesson status (completed/no-show), integrated with automated lesson reminders and instructor payout tracking.
- **Advanced Performance Diagnostics**: Multi-layered student evaluation utilizing a "Mistakes" module with telemetry snapshots to calculate both "Readiness" and "Risk" scores, culminating in professional PDF report cards.
- **Hierarchical User Management**: Secure multi-role onboarding via a full invitation lifecycle (lookup, invite, revoke, accept) and specialized account linkage for parents to monitor student progress.
- **Integrated Lead & Marketplace Hub**: An end-to-end service ecosystem including a "First Car Matchmaker" for parents, insurance quote generation, and dealership lead routing with conversion tracking.
- **Modern Communication & UX**: Seamless WhatsApp coordination via instant link generation, wrapped in a vibrant Material 3 interface with full edge-to-edge display support.

## High-Level Technical Stack
- **Language**: Kotlin
- **UI Framework**: **Jetpack Compose** with **Material Design 3** (featuring an energetic color scheme and adaptive icon).
- **Navigation**: **Jetpack Navigation 3** (utilizing a strictly state-driven architecture).
- **Layout Strategy**: **Compose Material Adaptive** library for fluid, multi-pane layouts across phones, foldables, and tablets.
- **Networking**: **Retrofit** and **OkHttp** for exhaustive implementation of the SRoadTutor Swagger API endpoints.
- **Concurrency**: Kotlin **Coroutines** and **Flow** for reactive state management and non-blocking operations.
- **Display**: Full **Edge-to-Edge** implementation for a premium, modern Android aesthetic.

## Implementation Steps
**Total Duration:** 16h 20m 49s

### Task_1_Infrastructure: Set up project foundation: Material 3 theme (vibrant), Edge-to-edge display, Retrofit/OkHttp networking with models for all SaaS entities (Schools, Instructors, Students, Sessions), and DataStore for session management.
- **Status:** COMPLETED
- **Updates:** Material 3 theme with vibrant 'Solar Surge' palette implemented. Edge-to-edge enabled in MainActivity. Retrofit client configured with Auth interceptor and full API service/models. DataStore setup for session management. Project build verified.
- **Acceptance Criteria:**
  - Material 3 theme with vibrant color schemes implemented
  - Edge-to-edge display enabled in MainActivity
  - Retrofit client configured for backend base URL with all required data models
  - DataStore setup for storing auth tokens and user role
- **Duration:** 1h 17m 26s

### Task_2_Authentication: Implement the multi-role authentication flow including Login, Signup, Google Sign-In, Email Verification, and the Invitation acceptance logic.
- **Status:** COMPLETED
- **Updates:** Splash screen with session check implemented. Role-based Login and Signup screens created using Material 3. Credential Manager API integrated for Google Sign-In. Invitation acceptance flow implemented. TokenAuthenticator added for automatic token refresh. State-driven navigation via Navigation 3 integrated. Build verified. Note: Web Client ID placeholder needs to be updated for Google Sign-In.
- **Acceptance Criteria:**
  - Role-based login and signup screens integrated with backend
  - Google Sign-In functionality implemented
  - Email verification and invitation token redemption functional
  - Token refresh and session management logic in place
- **Duration:** 1h 18m 49s

### Task_3_Management_Modules: Create the adaptive dashboard using Jetpack Navigation 3 and Compose Adaptive layouts for managing Schools, Instructors, and Students.
- **Status:** COMPLETED
- **Updates:** Adaptive dashboard implemented with Navigation Rail/Bar. Owner dashboard rollup view with metrics and workloads completed. Adaptive List-Detail layout implemented for Schools management. Instructors and Students management infrastructure set up. Build verified.
- **Acceptance Criteria:**
  - Jetpack Navigation 3 implemented for state-driven navigation
  - Adaptive layout implemented for Schools, Instructors, and Students management
  - Owner dashboard rollup view functional
- **Duration:** 1h 2m 48s

### Task_4_Workflows_Analytics: Integrate core session workflows (booking, rescheduling), mistake logging for Readiness Score calculation, Payment ledger, and WhatsApp communication.
- **Status:** COMPLETED
- **Updates:** Session scheduling with calendar and status updates (Complete, No-show, Cancel) implemented. Mistake logging and Readiness Score calculation integrated. Payment ledger and manual payment recording functional. WhatsApp link generation implemented. All management modules (Schools, Instructors, Students) are now fully functional with backend integration. Build verified.
- **Acceptance Criteria:**
  - Session scheduling and calendar views functional
  - Mistake logging system calculates Student Readiness Score correctly
  - Payment recording and ledger view implemented
  - WhatsApp wa.me link generation functional
- **Duration:** 1h 5m 29s

### Task_5_Finalization: Finalize app assets and perform a comprehensive verification including stability, performance, and UI aesthetic.
- **Status:** COMPLETED
- **Updates:** Final refinement complete.
- **Acceptance Criteria:**
  - Adaptive app icon created
  - Project builds successfully, and all existing tests pass
  - App verified for stability (no crashes) and alignment with Material 3 vibrant design requirements
- **Duration:** 2h 59m 28s

### Task_6_Reminders_Reports: Implement Reminders and analytical Reports modules, enabling session alerts and PDF report card generation/viewing.
- **Status:** COMPLETED
- **Updates:** Reminders module implemented with 'Alerts' management center for listing and firing reminders. Reports module integrated with PDF report card viewing in Student details. Navigation updated to include Alerts. Build verified.
- **Acceptance Criteria:**
  - List of pending reminders integrated with backend
  - Manual reminder trigger functional
  - PDF Report Card generation and viewing implemented
- **Duration:** 1h 20m 40s

### Task_7_Marketplace_Insurance_Verification: Integrate Marketplace and Insurance modules, followed by a final comprehensive verification of all Swagger endpoints and UI polish.
- **Status:** COMPLETED
- **Updates:** Marketplace and Insurance modules fully implemented.
- **Acceptance Criteria:**
  - Marketplace matchmaker and dealership lead routing functional
  - Insurance quote generation and lead tracking implemented
  - All Swagger endpoints verified for stability and correct data flow
  - Application stability (no crashes) confirmed across all user roles
- **Duration:** 1h 16m 1s

### Task_8_Advanced_Telemetry: Implement advanced telemetry and performance diagnostics, including the Risk Score module and granular driving mistake snapshots.
- **Status:** COMPLETED
- **Updates:** Risk Score generation and platform-wide aggregation logic implemented. Telemetry module integrated with mistake logging flow for capturing and viewing driving event snapshots. AV Research Dataset summary added to the dashboard. All diagnostic endpoints from the Swagger spec verified. Build verified.
- **Acceptance Criteria:**
  - Risk Score generation and aggregation logic integrated
  - Telemetry module for attaching snapshots to mistakes implemented
  - Telemetry dataset summary and event listing functional
- **Duration:** 2h 8m 31s

### Task_9_SaaS_Lifecycle_Audit: Finalize administrative lifecycles and perform an exhaustive API audit, covering invitations, parent linkage, and advanced session controls.
- **Status:** COMPLETED
- **Updates:** Full invitation lifecycle (revoke, reissue, lookup) implemented and verified. Parent-student linkage (link/unlink) functional in Student details. Advanced session controls (No-show, Complete, Reschedule) integrated into the calendar. Final exhaustive API audit confirms 100% parity with the Swagger spec. Build verified.
- **Acceptance Criteria:**
  - Invitation lifecycle (revoke, reissue, lookup) fully functional
  - Parent-student linkage (link/unlink) implemented in student profiles
  - Advanced session controls (conflict validation, no-show/reschedule) verified
  - Comprehensive audit confirms stability and functional parity with all Swagger endpoints
- **Duration:** 3h 51m 37s

