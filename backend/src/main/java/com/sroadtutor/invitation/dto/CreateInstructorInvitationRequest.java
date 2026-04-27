package com.sroadtutor.invitation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for {@code POST /api/schools/{schoolId}/invitations/instructor}.
 *
 * <p>{@code deliveryMode} = {@code TOKEN} → emails (or returns) a one-shot
 * link the invitee clicks to set their own password. {@code DUMMY_PWD} →
 * pre-creates the user account with the {@code test123} password and
 * {@code mustChangePassword=true}; the user logs in directly.</p>
 *
 * <p>Optional pre-population fields ({@code vehicleMake}, {@code vehicleModel},
 * etc.) are written into the {@code invitations.metadata} JSONB and applied
 * to the new {@code Instructor} row on accept (or immediately when
 * {@code DUMMY_PWD} mode short-circuits the accept step).</p>
 */
public record CreateInstructorInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200)        String fullName,

        @Pattern(regexp = "^(TOKEN|DUMMY_PWD)$",
                message = "deliveryMode must be one of TOKEN, DUMMY_PWD")
        String deliveryMode,

        @Pattern(regexp = "^(OWNER|HEAD|REGULAR)$",
                message = "roleAtSchool must be one of OWNER, HEAD, REGULAR")
        String roleAtSchool,

        // ---- optional Instructor profile pre-populate ----
        @Size(max = 64)  String licenseNo,
        @Size(max = 64)  String sgiCert,
        @Size(max = 64)  String vehicleMake,
        @Size(max = 64)  String vehicleModel,
        @Min(1900)       Integer vehicleYear,
        @Size(max = 20)  String vehiclePlate,
        @Size(max = 4000)String bio,

        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal hourlyRate
) {}
