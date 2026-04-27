package com.sroadtutor.payment.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.payment.dto.MarkPaidRequest;
import com.sroadtutor.payment.dto.PaymentResponse;
import com.sroadtutor.payment.dto.RecordPaymentRequest;
import com.sroadtutor.payment.dto.StudentLedgerResponse;
import com.sroadtutor.payment.model.Payment;
import com.sroadtutor.payment.repository.PaymentRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.session.model.LessonSession;
import com.sroadtutor.session.repository.LessonSessionRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.ParentStudentRepository;
import com.sroadtutor.student.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment ledger. Locked at PR11:
 * <ul>
 *   <li><b>Auto-create at session COMPLETE</b> ({@link #createForCompletedSession}) —
 *       wired from {@link com.sroadtutor.session.service.SessionService#complete}.
 *       Amount = {@code instructor.hourly_rate × duration/60}, rounded to 2dp.
 *       If hourly_rate is null, amount=0 (owner fills in later). Status=UNPAID,
 *       method=null. One auto-row per session — re-completing (impossible by
 *       design) would no-op via the existing-row guard.</li>
 *   <li><b>Manual record</b> ({@link #record}) — owner / instructor enters
 *       cash / e-transfer / write-off. {@code STRIPE} method rejected — those
 *       arrive via Stripe webhook in PR12.5.</li>
 *   <li><b>Mark paid</b> ({@link #markPaid}) — flips UNPAID → PAID. Idempotent
 *       on already-PAID. Stores method + paidAt.</li>
 *   <li><b>Scope</b> — write: OWNER of school OR assigned INSTRUCTOR. Read:
 *       above + the student themselves + linked PARENT.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepo;
    private final SchoolRepository schoolRepo;
    private final InstructorRepository instructorRepo;
    private final StudentRepository studentRepo;
    private final ParentStudentRepository parentLinkRepo;
    private final LessonSessionRepository sessionRepo;

    public PaymentService(PaymentRepository paymentRepo,
                          SchoolRepository schoolRepo,
                          InstructorRepository instructorRepo,
                          StudentRepository studentRepo,
                          ParentStudentRepository parentLinkRepo,
                          LessonSessionRepository sessionRepo) {
        this.paymentRepo = paymentRepo;
        this.schoolRepo = schoolRepo;
        this.instructorRepo = instructorRepo;
        this.studentRepo = studentRepo;
        this.parentLinkRepo = parentLinkRepo;
        this.sessionRepo = sessionRepo;
    }

    // ============================================================
    // Auto-create at session complete
    // ============================================================

    /**
     * Wired from {@code SessionService.complete}. Creates an UNPAID Payment
     * tied to the completed session. Idempotent — if a Payment already
     * exists for this session, returns the existing row.
     */
    @Transactional
    public Payment createForCompletedSession(LessonSession session) {
        Optional<Payment> existing = paymentRepo.findFirstBySessionId(session.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Instructor instructor = instructorRepo.findById(session.getInstructorId()).orElse(null);
        BigDecimal rate = instructor == null ? null : instructor.getHourlyRate();
        BigDecimal amount;
        if (rate == null) {
            amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            amount = rate
                    .multiply(BigDecimal.valueOf(session.getDurationMins()))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }

        Payment payment = Payment.builder()
                .schoolId(session.getSchoolId())
                .studentId(session.getStudentId())
                .sessionId(session.getId())
                .amount(amount)
                .currency("CAD")
                .method(null)
                .status(Payment.STATUS_UNPAID)
                .build();
        payment = paymentRepo.save(payment);

        log.info("Auto-created UNPAID payment {} for session={} amount={}",
                payment.getId(), session.getId(), amount);
        return payment;
    }

    // ============================================================
    // Manual record
    // ============================================================

    @Transactional
    public Payment record(Role role, UUID currentUserId, RecordPaymentRequest req) {
        Student student = studentRepo.findById(req.studentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + req.studentId()));

        School school = schoolRepo.findById(student.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + student.getSchoolId()));
        if (!school.isActive()) {
            throw new BadRequestException(
                    "SCHOOL_INACTIVE",
                    "Cannot record payments at a deactivated school");
        }
        requireWriteScope(role, currentUserId, school, student);

        // Optional sessionId must belong to this student.
        if (req.sessionId() != null) {
            LessonSession s = sessionRepo.findById(req.sessionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Session not found: " + req.sessionId()));
            if (!s.getStudentId().equals(student.getId())) {
                throw new BadRequestException(
                        "SESSION_WRONG_STUDENT",
                        "Session does not belong to this student");
            }
        }

        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(
                    "INVALID_AMOUNT",
                    "amount must be >= 0");
        }
        String method = req.method() == null ? Payment.METHOD_OTHER : req.method();
        boolean alreadyPaid = req.paidAt() != null;

        Payment payment = Payment.builder()
                .schoolId(student.getSchoolId())
                .studentId(student.getId())
                .sessionId(req.sessionId())
                .amount(req.amount().setScale(2, RoundingMode.HALF_UP))
                .currency(req.currency() == null || req.currency().isBlank() ? "CAD" : req.currency().toUpperCase())
                .method(method)
                .status(alreadyPaid ? Payment.STATUS_PAID : Payment.STATUS_UNPAID)
                .paidAt(req.paidAt())
                .build();
        payment = paymentRepo.save(payment);

        log.info("Manual payment {} recorded by {}={} student={} amount={} status={}",
                payment.getId(), role, currentUserId, student.getId(), payment.getAmount(), payment.getStatus());
        return payment;
    }

    // ============================================================
    // Mark paid
    // ============================================================

    @Transactional
    public Payment markPaid(Role role, UUID currentUserId, UUID paymentId, MarkPaidRequest req) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (Payment.STATUS_PAID.equals(payment.getStatus())) {
            return payment; // idempotent
        }

        UUID studentId = payment.getStudentId();
        UUID schoolId = payment.getSchoolId();
        Student student = studentRepo.findById(payment.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + studentId));
        School school = schoolRepo.findById(payment.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + schoolId));
        requireWriteScope(role, currentUserId, school, student);

        payment.setStatus(Payment.STATUS_PAID);
        payment.setMethod(req.method());
        payment.setPaidAt(req.paidAt() == null ? Instant.now() : req.paidAt());
        payment = paymentRepo.save(payment);

        log.info("Payment {} marked PAID by {}={} method={} paidAt={}",
                paymentId, role, currentUserId, req.method(), payment.getPaidAt());
        return payment;
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public PaymentResponse getById(Role role, UUID currentUserId, UUID paymentId) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        Student student = studentRepo.findById(payment.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + payment.getStudentId()));
        School school = schoolRepo.findById(payment.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + payment.getSchoolId()));
        requireReadScope(role, currentUserId, school, student);
        return PaymentResponse.fromEntity(payment);
    }

    @Transactional(readOnly = true)
    public StudentLedgerResponse getStudentLedger(Role role, UUID currentUserId, UUID studentId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        School school = schoolRepo.findById(student.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + student.getSchoolId()));
        requireReadScope(role, currentUserId, school, student);

        List<Payment> rows = paymentRepo.findByStudentId(studentId);
        BigDecimal paid = paymentRepo.sumPaidForStudent(studentId);
        BigDecimal outstanding = paymentRepo.sumOutstandingForStudent(studentId);
        return new StudentLedgerResponse(
                studentId,
                paid == null ? BigDecimal.ZERO : paid,
                outstanding == null ? BigDecimal.ZERO : outstanding,
                "CAD",
                rows.stream().map(PaymentResponse::fromEntity).toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getOutstandingForSchool(Role role, UUID currentUserId, UUID schoolId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        if (role != Role.OWNER || !currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("Only OWNER of this school can view outstanding payments");
        }
        return paymentRepo.findOutstandingForSchool(schoolId).stream()
                .map(PaymentResponse::fromEntity)
                .toList();
    }

    // ============================================================
    // Scope
    // ============================================================

    private void requireReadScope(Role role, UUID currentUserId, School school, Student student) {
        switch (role) {
            case OWNER -> {
                if (currentUserId.equals(school.getOwnerId())) return;
            }
            case INSTRUCTOR -> {
                Optional<Instructor> me = instructorRepo.findByUserId(currentUserId);
                if (me.isPresent() && me.get().getId().equals(student.getInstructorId())) return;
            }
            case STUDENT -> {
                if (currentUserId.equals(student.getUserId())) return;
            }
            case PARENT -> {
                if (parentLinkRepo.existsByParentUserIdAndStudentId(currentUserId, student.getId())) return;
            }
        }
        throw new AccessDeniedException("Caller cannot read this payment");
    }

    private void requireWriteScope(Role role, UUID currentUserId, School school, Student student) {
        switch (role) {
            case OWNER -> {
                if (currentUserId.equals(school.getOwnerId())) return;
            }
            case INSTRUCTOR -> {
                Optional<Instructor> me = instructorRepo.findByUserId(currentUserId);
                if (me.isPresent() && me.get().getId().equals(student.getInstructorId())) return;
            }
            default -> { /* deny */ }
        }
        throw new AccessDeniedException("Only OWNER or assigned INSTRUCTOR can record / mark payments");
    }
}
