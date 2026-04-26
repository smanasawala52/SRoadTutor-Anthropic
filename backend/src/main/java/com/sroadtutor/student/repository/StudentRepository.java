package com.sroadtutor.student.repository;

import com.sroadtutor.student.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    List<Student> findBySchoolId(UUID schoolId);

    List<Student> findByInstructorId(UUID instructorId);

    List<Student> findBySchoolIdAndStatus(UUID schoolId, String status);
}
