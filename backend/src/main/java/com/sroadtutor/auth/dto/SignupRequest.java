package com.sroadtutor.auth.dto;

import com.sroadtutor.auth.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /auth/signup body.
 *
 * <p>Password policy: min 8 chars, at least one letter + one number.
 * Intentionally <em>not</em> overly strict — mobile users hate it and complexity
 * alone is a weak defence. Length + BCrypt strength + MFA later is better.</p>
 *
 * <p>Phone is intentionally absent from signup. It is collected post-auth via
 * {@code POST /api/phone-numbers} (PR4) so the same flow handles WhatsApp opt-in
 * and verification consistently for every channel a user might add.</p>
 */
public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "Password must contain at least one letter and one number")
        String password,
        @NotBlank @Size(max = 200) String fullName,
        @NotNull Role role
) {}
