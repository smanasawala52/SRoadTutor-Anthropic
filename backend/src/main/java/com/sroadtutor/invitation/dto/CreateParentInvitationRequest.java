package com.sroadtutor.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body for {@code POST /api/schools/{schoolId}/invitations/parent}.
 *
 * <p>{@code studentIds} is optional — if supplied, the parent gets pre-linked
 * to those students (same school, of course) on accept. The list is stored
 * in {@code invitations.metadata}.</p>
 */
public record CreateParentInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200)        String fullName,

        @Pattern(regexp = "^(TOKEN|DUMMY_PWD)$",
                message = "deliveryMode must be one of TOKEN, DUMMY_PWD")
        String deliveryMode,

        @Size(max = 32)                   String relationship,

        List<UUID>                        studentIds
) {}
