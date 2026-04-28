package com.sroadtutor.insurance.repository;

import com.sroadtutor.insurance.model.InsuranceBroker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InsuranceBrokerRepository extends JpaRepository<InsuranceBroker, UUID> {

    List<InsuranceBroker> findByActiveTrue();

    /**
     * Active brokers whose {@code province} matches OR is null (nationwide).
     * Province-specific brokers come first via the ORDER clause so the
     * routing picker prefers them.
     */
    @Query("""
            SELECT b FROM InsuranceBroker b
            WHERE b.active = true
              AND (b.province = :province OR b.province IS NULL)
            ORDER BY CASE WHEN b.province IS NULL THEN 1 ELSE 0 END
            """)
    List<InsuranceBroker> findEligibleForProvince(@Param("province") String province);
}
