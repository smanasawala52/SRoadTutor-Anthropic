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
 * Intentionally *not* overly strict — mobile users hate it and complexity
 * alone is a weak defence.  Length + BCrypt strength + MFA later is better.</p>
 */
public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "Password must contain at least one letter and one number")
        String password,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 32) String phone,
        @NotNull Role role
) {}
