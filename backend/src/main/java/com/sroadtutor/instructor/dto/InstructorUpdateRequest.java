package com.sroadtutor.instructor.dto;

import com.sroadtutor.instructor.model.WorkingHours;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for {@code PUT /api/instructors/{id}}. All fields nullable — patch
 * semantics. The owning {@code userId} is immutable; to "move" a profile
 * to a different user, delete + recreate (out of V1 scope).
 */
public record InstructorUpdateRequest(
        @Size(max = 64) String licenseNo,
        @Size(max = 64) String sgiCert,
        @Size(max = 64) String vehicleMake,
        @Size(max = 64) String vehicleModel,
        @Min(1900) Integer vehicleYear,
        @Size(max = 20) String vehiclePlate,
        @Size(max = 4000) String bio,

        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal hourlyRate,

        @Valid WorkingHours workingHours
) {}
