package com.sroadtutor.risk.repository;

import com.sroadtutor.risk.model.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {

    Optional<RiskScore> findByStudentAnonymizedHash(String hash);

    /** Distribution count by tier across all rows — drives the aggregate API. */
    @Query("""
            SELECT r.riskTier, COUNT(r)
            FROM RiskScore r
            GROUP BY r.riskTier
            """)
    List<Object[]> countsByTier();

    List<RiskScore> findAllByLicensedToInsurer(String insurer);
}
