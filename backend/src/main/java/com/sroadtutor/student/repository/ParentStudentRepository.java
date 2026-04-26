package com.sroadtutor.student.repository;

import com.sroadtutor.student.model.ParentStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParentStudentRepository extends JpaRepository<ParentStudent, UUID> {

    List<ParentStudent> findByStudentId(UUID studentId);

    List<ParentStudent> findByParentUserId(UUID parentUserId);

    Optional<ParentStudent> findByParentUserIdAndStudentId(UUID parentUserId, UUID studentId);

    boolean existsByParentUserIdAndStudentId(UUID parentUserId, UUID studentId);
}
