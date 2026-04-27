package com.sroadtutor.subscription.repository;

import com.sroadtutor.subscription.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /** Active = not cancelled. One per school in V1. */
    Optional<Subscription> findFirstBySchoolIdAndCancelledAtIsNullOrderByCreatedAtDesc(UUID schoolId);
}
