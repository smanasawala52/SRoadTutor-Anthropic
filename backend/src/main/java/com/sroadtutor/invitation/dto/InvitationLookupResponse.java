package com.sroadtutor.invitation.dto;

/**
 * Public response of {@code GET /api/invitations/lookup/{token}} — used by
 * the SPA's accept-invite landing page (BEFORE the invitee has credentials)
 * to render "Welcome, you're being invited as INSTRUCTOR at ABC Driving".
 *
 * <p>Deliberately leaks NO PII beyond what the link-holder already knows
 * (their own email + the school name). Status is included so the SPA can
 * render the right state (pending → form, accepted/expired/revoked → hint).</p>
 */
public record InvitationLookupResponse(
        String email,
        String fullName,
        String role,
        String deliveryMode,
        String status,
        String schoolName
) {}
