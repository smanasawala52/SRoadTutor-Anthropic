package com.sroadtutor.invitation.repository;

import com.sroadtutor.invitation.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lookups PR5 will need: token-based redemption, owner-side listing per
 * school, and finding pending invites by email so we can dedupe before
 * issuing a duplicate.
 */
@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findBySchoolId(UUID schoolId);

    List<Invitation> findBySchoolIdAndStatus(UUID schoolId, String status);

    List<Invitation> findByEmailIgnoreCaseAndStatus(String email, String status);
}
