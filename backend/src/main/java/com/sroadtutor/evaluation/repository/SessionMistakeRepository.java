package com.sroadtutor.evaluation.repository;

import com.sroadtutor.evaluation.model.SessionMistake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionMistakeRepository extends JpaRepository<SessionMistake, UUID> {

    List<SessionMistake> findBySessionIdOrderByLoggedAtAsc(UUID sessionId);

    List<SessionMistake> findByStudentIdOrderByLoggedAtAsc(UUID studentId);

    @Query("""
            SELECT m FROM SessionMistake m
            WHERE m.studentId = :studentId
            ORDER BY m.loggedAt DESC
            """)
    List<SessionMistake> findByStudentIdRecentFirst(@Param("studentId") UUID studentId);

    long countBySessionId(UUID sessionId);
}
