package com.sroadtutor.telemetry.repository;

import com.sroadtutor.telemetry.model.TelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TelemetryEventRepository extends JpaRepository<TelemetryEvent, UUID> {

    List<TelemetryEvent> findBySessionMistakeIdOrderBySyncedAtAsc(UUID sessionMistakeId);

    long count();
}
