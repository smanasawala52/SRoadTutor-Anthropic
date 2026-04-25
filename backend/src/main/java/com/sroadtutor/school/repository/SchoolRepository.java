package com.sroadtutor.school.repository;

import com.sroadtutor.school.model.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Schools are looked up by id (the dominant pattern), by owner (the
 * "do-I-already-own-a-school?" guard at create time), and that's it for V1.
 * Cross-school listing is reserved for a platform-admin role that doesn't
 * exist yet.
 */
@Repository
public interface SchoolRepository extends JpaRepository<School, UUID> {

    Optional<School> findByOwnerId(UUID ownerId);

    boolean existsByOwnerId(UUID ownerId);
}
