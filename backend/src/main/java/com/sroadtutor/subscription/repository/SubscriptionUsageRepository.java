package com.sroadtutor.subscription.repository;

import com.sroadtutor.subscription.model.SubscriptionUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionUsageRepository extends JpaRepository<SubscriptionUsage, UUID> {

    Optional<SubscriptionUsage> findBySchoolIdAndPeriodStart(UUID schoolId, LocalDate periodStart);
}
