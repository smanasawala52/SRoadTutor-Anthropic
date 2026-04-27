package com.sroadtutor.marketplace.repository;

import com.sroadtutor.marketplace.model.DealershipLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DealershipLeadRepository extends JpaRepository<DealershipLead, UUID> {

    List<DealershipLead> findByStudentId(UUID studentId);

    List<DealershipLead> findByParentUserId(UUID parentUserId);

    /** Used by the graduation trigger to find a parent's existing lead for a student. */
    Optional<DealershipLead> findFirstByStudentIdAndStatus(UUID studentId, String status);

    List<DealershipLead> findByDealershipId(UUID dealershipId);
}
