package com.sroadtutor.telemetry.dto;

import com.sroadtutor.telemetry.model.TelemetryEvent;

import java.time.Instant;
import java.util.UUID;

public record TelemetryEventResponse(
        UUID id,
        UUID sessionMistakeId,
        String vehicleMake,
        String vehicleModel,
        Integer vehicleYear,
        String telemetryJson,
        Long offsetMs,
        Instant syncedAt
) {

    public static TelemetryEventResponse fromEntity(TelemetryEvent t) {
        return new TelemetryEventResponse(
                t.getId(),
                t.getSessionMistakeId(),
                t.getVehicleMake(),
                t.getVehicleModel(),
                t.getVehicleYear(),
                t.getTelemetryJson(),
                t.getOffsetMs(),
                t.getSyncedAt()
        );
    }
}
