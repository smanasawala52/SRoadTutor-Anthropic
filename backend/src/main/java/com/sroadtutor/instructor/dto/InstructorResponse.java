package com.sroadtutor.instructor.dto;

import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.model.WorkingHours;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read projection of {@link Instructor}. The {@code workingHoursJson} column
 * is parsed into a typed {@link WorkingHours} for the response so SPA clients
 * never see raw JSON strings.
 */
public record InstructorResponse(
        UUID id,
        UUID userId,
        UUID schoolId,
        String licenseNo,
        String sgiCert,
        String vehicleMake,
        String vehicleModel,
        Integer vehicleYear,
        String vehiclePlate,
        String bio,
        BigDecimal hourlyRate,
        WorkingHours workingHours,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static InstructorResponse fromEntity(Instructor i) {
        return new InstructorResponse(
                i.getId(),
                i.getUserId(),
                i.getSchoolId(),
                i.getLicenseNo(),
                i.getSgiCert(),
                i.getVehicleMake(),
                i.getVehicleModel(),
                i.getVehicleYear(),
                i.getVehiclePlate(),
                i.getBio(),
                i.getHourlyRate(),
                WorkingHours.fromJson(i.getWorkingHoursJson()),
                i.isActive(),
                i.getCreatedAt(),
                i.getUpdatedAt()
        );
    }
}
