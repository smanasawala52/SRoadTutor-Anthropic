package com.sroadtutor.phone.repository;

import com.sroadtutor.phone.model.PhoneNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PR2 just delivers the lookups PR4 will need. Filtering by owner column is
 * the dominant access pattern; we expose one finder per owner type rather
 * than building a generic predicate now and regretting it later.
 */
@Repository
public interface PhoneNumberRepository extends JpaRepository<PhoneNumber, UUID> {

    List<PhoneNumber> findByUserId(UUID userId);

    List<PhoneNumber> findBySchoolId(UUID schoolId);

    List<PhoneNumber> findByInstructorId(UUID instructorId);

    List<PhoneNumber> findByStudentId(UUID studentId);

    Optional<PhoneNumber> findByUserIdAndPrimaryTrue(UUID userId);

    Optional<PhoneNumber> findBySchoolIdAndPrimaryTrue(UUID schoolId);

    Optional<PhoneNumber> findByInstructorIdAndPrimaryTrue(UUID instructorId);

    Optional<PhoneNumber> findByStudentIdAndPrimaryTrue(UUID studentId);

    /**
     * E.164 lookup that ignores ownership — used by inbound webhook hooks
     * (Twilio / Meta callbacks in a later phase) to map a number to whoever
     * owns it. Multiple owners can legitimately share the same e164.
     */
    @Query("SELECT p FROM PhoneNumber p WHERE p.e164 = :e164")
    List<PhoneNumber> findAllByE164(@Param("e164") String e164);
}
