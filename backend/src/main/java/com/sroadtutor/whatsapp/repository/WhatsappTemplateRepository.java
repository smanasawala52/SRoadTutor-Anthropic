package com.sroadtutor.whatsapp.repository;

import com.sroadtutor.whatsapp.model.WhatsappTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The look-up that drives PR4's "render this template for a school" flow:
 * resolve to a school-specific override if one exists, fall back to the
 * platform default. The two queries below let the service layer try
 * school-first and then platform-default explicitly — keeps SQL simple and
 * the resolution order legible.
 */
@Repository
public interface WhatsappTemplateRepository extends JpaRepository<WhatsappTemplate, UUID> {

    @Query("""
            SELECT t FROM WhatsappTemplate t
            WHERE t.schoolId = :schoolId AND t.code = :code AND t.language = :language
              AND t.active = true
            """)
    Optional<WhatsappTemplate> findActiveSchoolOverride(@Param("schoolId") UUID schoolId,
                                                       @Param("code") String code,
                                                       @Param("language") String language);

    @Query("""
            SELECT t FROM WhatsappTemplate t
            WHERE t.schoolId IS NULL AND t.code = :code AND t.language = :language
              AND t.active = true
            """)
    Optional<WhatsappTemplate> findActivePlatformDefault(@Param("code") String code,
                                                        @Param("language") String language);

    List<WhatsappTemplate> findBySchoolId(UUID schoolId);
}
