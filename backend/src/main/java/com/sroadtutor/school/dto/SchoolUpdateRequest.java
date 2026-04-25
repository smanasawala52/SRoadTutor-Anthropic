package com.sroadtutor.school.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/schools/{id}}. All fields are nullable — service
 * applies a field if non-null, leaves it otherwise. The owner FK is immutable
 * (use the create endpoint to spin up a new school) and {@code planTier},
 * {@code stripeCustomerId} are write-locked outside of PR9's billing module.
 */
public record SchoolUpdateRequest(
        @Size(min = 1, max = 200) String name,

        @Pattern(regexp = "^(SGI|ICBC|MTO|DMV)$",
                message = "jurisdiction must be one of SGI, ICBC, MTO, DMV")
        String jurisdiction,

        @Pattern(regexp = "^[A-Z]{2}$",
                message = "province must be a 2-letter uppercase code")
        @Size(max = 8) String province,

        @Size(max = 40) String gstNumber,
        @Size(max = 40) String pstNumber,
        @Size(max = 40) String hstNumber,
        @Size(max = 80) String businessRegistrationNumber
) {}
