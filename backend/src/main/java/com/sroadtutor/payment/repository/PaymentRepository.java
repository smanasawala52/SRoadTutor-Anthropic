package com.sroadtutor.payment.repository;

import com.sroadtutor.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByStudentId(UUID studentId);

    List<Payment> findBySessionId(UUID sessionId);

    Optional<Payment> findFirstBySessionId(UUID sessionId);

    @Query("""
            SELECT p FROM Payment p
            WHERE p.schoolId = :schoolId
              AND p.status = 'UNPAID'
            ORDER BY p.createdAt ASC
            """)
    List<Payment> findOutstandingForSchool(@Param("schoolId") UUID schoolId);

    /** Sum of UNPAID amounts for a student — drives the "amount owed" line on the SPA. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.studentId = :studentId AND p.status = 'UNPAID'
            """)
    BigDecimal sumOutstandingForStudent(@Param("studentId") UUID studentId);

    /** Sum of PAID amounts for a student — drives the "total paid" line. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.studentId = :studentId AND p.status = 'PAID'
            """)
    BigDecimal sumPaidForStudent(@Param("studentId") UUID studentId);
}
