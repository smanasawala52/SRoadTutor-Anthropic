package com.sroadtutor.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for {@code POST /api/schools/{schoolId}/invitations/student}. See
 * {@link CreateInstructorInvitationRequest} for the deliveryMode contract.
 *
 * <p>Optional fields ({@code packageTotalLessons}, {@code instructorId},
 * parent fields) are stored in {@code invitations.metadata} and applied on
 * accept — same find-or-create-parent semantics as
 * {@code StudentService.addByOwner}.</p>
 */
public record CreateStudentInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200)        String fullName,

        @Pattern(regexp = "^(TOKEN|DUMMY_PWD)$",
                message = "deliveryMode must be one of TOKEN, DUMMY_PWD")
        String deliveryMode,

        UUID                              instructorId,
        @Min(0)                           Integer packageTotalLessons,
        LocalDate                         roadTestDate,

        @Email @Size(max = 254)           String parentEmail,
        @Size(max = 200)                  String parentFullName,
        @Size(max = 32)                   String parentRelationship
) {}
