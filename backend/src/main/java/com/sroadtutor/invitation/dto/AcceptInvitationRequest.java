package com.sroadtutor.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/invitations/{token}/accept}. The invitee
 * supplies their chosen password. Password rules mirror the signup flow.
 *
 * <p>Only TOKEN-mode invitations reach this endpoint — DUMMY_PWD invites
 * are accepted at create-time and the user logs in directly via
 * {@code POST /auth/login} with {@code test123}.</p>
 */
public record AcceptInvitationRequest(
        @NotBlank @Size(min = 8, max = 128) String password,

        /** Optional language preference; defaults to "en". */
        @Size(max = 8) String languagePref
) {}
