package com.sroadtutor.instructor.repository;

import com.sroadtutor.instructor.model.InstructorSchool;
import com.sroadtutor.instructor.model.InstructorSchoolId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * PR6 will lean on this repo to drive the JWT {@code schoolIds} claim and
 * to power the school-scoped instructor list. Active membership is the
 * common case; historical (left-at-set) rows stay around for audit.
 */
@Repository
public interface InstructorSchoolRepository
        extends JpaRepository<InstructorSchool, InstructorSchoolId> {

    List<InstructorSchool> findByIdInstructorId(UUID instructorId);

    List<InstructorSchool> findByIdSchoolId(UUID schoolId);

    List<InstructorSchool> findByIdInstructorIdAndLeftAtIsNull(UUID instructorId);

    List<InstructorSchool> findByIdSchoolIdAndLeftAtIsNull(UUID schoolId);
}
