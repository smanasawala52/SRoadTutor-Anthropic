package com.sroadtutor.marketplace.repository;

import com.sroadtutor.marketplace.model.InstructorPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstructorPayoutRepository extends JpaRepository<InstructorPayout, UUID> {

    List<InstructorPayout> findByInstructorIdOrderByCreatedAtDesc(UUID instructorId);

    Optional<InstructorPayout> findByLeadId(UUID leadId);

    List<InstructorPayout> findByStatus(String status);
}
