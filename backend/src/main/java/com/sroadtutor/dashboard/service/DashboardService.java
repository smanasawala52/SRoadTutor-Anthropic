package com.sroadtutor.dashboard.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.dashboard.dto.DashboardResponse;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.payment.model.Payment;
import com.sroadtutor.payment.repository.PaymentRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.session.model.LessonSession;
import com.sroadtutor.session.repository.LessonSessionRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.StudentRepository;
import com.sroadtutor.subscription.model.PlanTier;
import com.sroadtutor.subscription.service.PlanLimitsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owner dashboard rollup. One read endpoint, several aggregations.
 *
 * <ul>
 *   <li><b>Total revenue paid</b> — sum of {@code Payment.amount} where
 *       {@code status=PAID} (school-scoped, all time). Drives the "money
 *       in" tile.</li>
 *   <li><b>Total outstanding</b> — sum of UNPAID payments (school-scoped).
 *       Drives the "money owed" tile.</li>
 *   <li><b>Active students</b> — count of {@code Student.status=ACTIVE} in
 *       the school.</li>
 *   <li><b>Upcoming sessions</b> — SCHEDULED sessions in {@code [now, now+30d)}.</li>
 *   <li><b>Completed sessions in window</b> — {@code [now-30d, now)}.</li>
 *   <li><b>Monthly recurring revenue</b> — V1: derived from the school's
 *       current plan tier price (so a single school × $29 = $29 MRR). When
 *       the platform onboards more schools, the OWNER dashboard still only
 *       sees their own school's MRR — this is the per-school view, not the
 *       platform view. PR12.5 (full Stripe) will replace this with the
 *       actual subscription price-of-record.</li>
 *   <li><b>Instructor workloads</b> — per-instructor: scheduled sessions
 *       upcoming, completed sessions in window, active students assigned.</li>
 * </ul>
 *
 * <p>Window is fixed at 30 days for V1. Configurable per-call is tracked
 * as TD; the SPA can ask for "this month" / "last 7 days" later.</p>
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    /** Default rolling window for time-bounded metrics. */
    static final Duration WINDOW = Duration.ofDays(30);

    private final SchoolRepository schoolRepo;
    private final UserRepository userRepo;
    private final StudentRepository studentRepo;
    private final InstructorRepository instructorRepo;
    private final LessonSessionRepository sessionRepo;
    private final PaymentRepository paymentRepo;
    private final PlanLimitsService planLimits;

    public DashboardService(SchoolRepository schoolRepo,
                             UserRepository userRepo,
                             StudentRepository studentRepo,
                             InstructorRepository instructorRepo,
                             LessonSessionRepository sessionRepo,
                             PaymentRepository paymentRepo,
                             PlanLimitsService planLimits) {
        this.schoolRepo = schoolRepo;
        this.userRepo = userRepo;
        this.studentRepo = studentRepo;
        this.instructorRepo = instructorRepo;
        this.sessionRepo = sessionRepo;
        this.paymentRepo = paymentRepo;
        this.planLimits = planLimits;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getForCurrentOwner(Role role, UUID currentUserId) {
        if (role != Role.OWNER) {
            throw new AccessDeniedException("Only an OWNER can read the dashboard");
        }
        User caller = userRepo.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Caller user not found: " + currentUserId));
        if (caller.getSchoolId() == null) {
            throw new ResourceNotFoundException("Caller has no school assigned yet");
        }
        School school = schoolRepo.findById(caller.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + caller.getSchoolId()));
        if (!currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("OWNER can only read their own school's dashboard");
        }

        Instant now = Instant.now();
        Instant windowFrom = now.minus(WINDOW);
        Instant windowTo = now.plus(WINDOW);

        UUID schoolId = school.getId();

        // ---- money ----
        BigDecimal totalPaid = sumPaid(schoolId);
        BigDecimal totalOutstanding = sumOutstanding(schoolId);

        // ---- counts ----
        long activeStudents = studentRepo.findBySchoolIdAndStatus(schoolId, Student.STATUS_ACTIVE).size();
        List<LessonSession> upcoming = sessionRepo.findForSchoolInRange(schoolId, now, windowTo);
        long upcomingScheduled = upcoming.stream()
                .filter(s -> LessonSession.STATUS_SCHEDULED.equals(s.getStatus()))
                .count();
        List<LessonSession> recent = sessionRepo.findForSchoolInRange(schoolId, windowFrom, now);
        long completedInWindow = recent.stream()
                .filter(s -> LessonSession.STATUS_COMPLETED.equals(s.getStatus()))
                .count();

        // ---- MRR (per-school view; PR12.5 will be Stripe-driven) ----
        PlanTier plan = planLimits.currentPlan(schoolId);
        BigDecimal mrr = new BigDecimal(plan.monthlyPriceCad());

        // ---- instructor workloads ----
        List<DashboardResponse.InstructorWorkload> workloads = buildWorkloads(
                schoolId, windowFrom, windowTo);

        log.info("Dashboard rendered for school={} owner={} (paid={} outstanding={} mrr={})",
                schoolId, currentUserId, totalPaid, totalOutstanding, mrr);

        return new DashboardResponse(
                schoolId,
                school.getName(),
                plan.name(),
                new DashboardResponse.Window(windowFrom, windowTo, (int) WINDOW.toDays()),
                totalPaid,
                totalOutstanding,
                activeStudents,
                upcomingScheduled,
                completedInWindow,
                mrr,
                workloads);
    }

    // ============================================================
    // Money rollups
    // ============================================================

    private BigDecimal sumPaid(UUID schoolId) {
        // No "sum by school" finder on PaymentRepository — use the in-memory
        // sum via the existing per-student helpers OR scan via a dedicated
        // query. For V1 traffic we do an in-memory sum across the school's
        // outstanding + each student's paid total. Outstanding is exposed
        // school-wide; paid totals come per-student. Iterating students is
        // acceptable while school sizes stay sub-thousand — tracked as TD
        // when usage justifies a dedicated query.
        BigDecimal total = BigDecimal.ZERO;
        for (Student s : studentRepo.findBySchoolId(schoolId)) {
            BigDecimal paid = paymentRepo.sumPaidForStudent(s.getId());
            if (paid != null) total = total.add(paid);
        }
        return total;
    }

    private BigDecimal sumOutstanding(UUID schoolId) {
        BigDecimal total = BigDecimal.ZERO;
        for (Payment p : paymentRepo.findOutstandingForSchool(schoolId)) {
            total = total.add(p.getAmount());
        }
        return total;
    }

    // ============================================================
    // Instructor workloads
    // ============================================================

    private List<DashboardResponse.InstructorWorkload> buildWorkloads(
            UUID schoolId, Instant windowFrom, Instant windowTo) {

        Instant now = Instant.now();
        List<Instructor> instructors = instructorRepo.findActiveBySchool(schoolId);
        List<DashboardResponse.InstructorWorkload> out = new ArrayList<>(instructors.size());
        for (Instructor i : instructors) {
            String name = userRepo.findById(i.getUserId())
                    .map(User::getFullName)
                    .orElse(null);

            List<LessonSession> upcoming = sessionRepo.findForInstructorInRange(i.getId(), now, windowTo);
            long scheduledUpcoming = upcoming.stream()
                    .filter(s -> LessonSession.STATUS_SCHEDULED.equals(s.getStatus()))
                    .count();

            List<LessonSession> recent = sessionRepo.findForInstructorInRange(i.getId(), windowFrom, now);
            long completed = recent.stream()
                    .filter(s -> LessonSession.STATUS_COMPLETED.equals(s.getStatus()))
                    .count();

            long activeAssigned = studentRepo.findByInstructorId(i.getId()).stream()
                    .filter(s -> Student.STATUS_ACTIVE.equals(s.getStatus()))
                    .filter(s -> schoolId.equals(s.getSchoolId()))
                    .count();

            out.add(new DashboardResponse.InstructorWorkload(
                    i.getId(), name, scheduledUpcoming, completed, activeAssigned));
        }
        return out;
    }
}
