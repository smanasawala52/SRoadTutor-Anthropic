package com.sroadtutor.whatsapp.repository;

import com.sroadtutor.whatsapp.model.WhatsappMessageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The audit-log access patterns are tenant-scoped and time-bounded — owners
 * answer "what did we send this week?" — so the school-id-and-time finder
 * is the workhorse.
 */
@Repository
public interface WhatsappMessageLogRepository
        extends JpaRepository<WhatsappMessageLog, UUID> {

    List<WhatsappMessageLog> findBySchoolIdAndLinkGeneratedAtBetween(
            UUID schoolId, Instant from, Instant to);

    List<WhatsappMessageLog> findBySenderUserIdOrderByLinkGeneratedAtDesc(UUID senderUserId);

    List<WhatsappMessageLog> findByRecipientPhoneIdOrderByLinkGeneratedAtDesc(UUID recipientPhoneId);
}
