package com.sroadtutor.insurance.repository;

import com.sroadtutor.insurance.model.InsuranceLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsuranceLeadRepository extends JpaRepository<InsuranceLead, UUID> {

    List<InsuranceLead> findByStudentId(UUID studentId);

    Optional<InsuranceLead> findFirstByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<InsuranceLead> findByBrokerId(UUID brokerId);

    List<InsuranceLead> findByStatus(String status);
}
