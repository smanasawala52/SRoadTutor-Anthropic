package com.sroadtutor.marketplace.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.marketplace.dto.DealershipLeadResponse;
import com.sroadtutor.marketplace.model.Dealership;
import com.sroadtutor.marketplace.model.DealershipLead;
import com.sroadtutor.marketplace.model.InstructorPayout;
import com.sroadtutor.marketplace.repository.DealershipLeadRepository;
import com.sroadtutor.marketplace.repository.DealershipRepository;
import com.sroadtutor.marketplace.repository.InstructorPayoutRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Graduation-trigger + lead-routing + conversion + payouts.
 *
 * <p>Locked at PR17:
 * <ul>
 *   <li><b>Graduation trigger</b> ({@link #onStudentPassed}) — invoked from
 *       {@code StudentService.update} when {@code status} flips to PASSED.
 *       Looks for an existing NEW lead for the student. If found, picks the
 *       first active dealership in the same province (school's province),
 *       sets {@code dealershipId} + copies {@code bountyPerLead} into
 *       {@code bountyAmount}, flips status to ROUTED. If no matching
 *       dealership exists, the lead stays NEW (we don't fail the student
 *       update over routing).</li>
 *   <li><b>Conversion</b> ({@link #markConverted}) — admin / dealership-side
 *       endpoint flips ROUTED → CONVERTED, sets {@code convertedAt}, and
 *       auto-creates an {@link InstructorPayout} (PENDING, $100 default).
 *       Idempotent on already-converted.</li>
 *   <li><b>Payout marked paid</b> ({@link #markPayoutPaid}) — admin records
 *       the e-transfer reference + paidAt timestamp.</li>
 *   <li><b>Reads</b> — owner sees their school's leads. Instructor sees
 *       their own payouts.</li>
 * </ul>
 */
@Service
public class LeadRoutingService {

    private static final Logger log = LoggerFactory.getLogger(LeadRoutingService.class);

    private final DealershipLeadRepository leadRepo;
    private final DealershipRepository dealershipRepo;
    private final InstructorPayoutRepository payoutRepo;
    private final StudentRepository studentRepo;
    private final SchoolRepository schoolRepo;

    public LeadRoutingService(DealershipLeadRepository leadRepo,
                                DealershipRepository dealershipRepo,
                                InstructorPayoutRepository payoutRepo,
                                StudentRepository studentRepo,
                                SchoolRepository schoolRepo) {
        this.leadRepo = leadRepo;
        this.dealershipRepo = dealershipRepo;
        this.payoutRepo = payoutRepo;
        this.studentRepo = studentRepo;
        this.schoolRepo = schoolRepo;
    }

    // ============================================================
    // Graduation trigger
    // ============================================================

    /**
     * Called by {@code StudentService.update} when {@code status} flips
     * to {@code PASSED}. Returns the routed lead if one was found + routed,
     * empty otherwise. Never throws — routing failures are logged but the
     * caller's transaction continues.
     */
    @Transactional
    public Optional<DealershipLead> onStudentPassed(UUID studentId) {
        Optional<DealershipLead> existing = leadRepo
                .findFirstByStudentIdAndStatus(studentId, DealershipLead.STATUS_NEW);
        if (existing.isEmpty()) {
            log.info("Graduation trigger: student {} has no NEW lead — no routing", studentId);
            return Optional.empty();
        }
        DealershipLead lead = existing.get();

        // Resolve school's province → pick the first active dealership there.
        Student student = studentRepo.findById(studentId).orElse(null);
        if (student == null) return Optional.empty();
        School school = schoolRepo.findById(student.getSchoolId()).orElse(null);
        if (school == null || school.getProvince() == null) {
            log.warn("Graduation trigger: school {} has no province — cannot route lead {}",
                    student.getSchoolId(), lead.getId());
            return Optional.empty();
        }

        List<Dealership> candidates = dealershipRepo
                .findByActiveTrueAndProvince(school.getProvince());
        if (candidates.isEmpty()) {
            log.info("Graduation trigger: no active dealership in province {} — lead {} stays NEW",
                    school.getProvince(), lead.getId());
            return Optional.empty();
        }

        // V1: deterministic — first dealership wins. Round-robin / weighted
        // routing is tracked as TD when the marketplace has > 1 dealership
        // per province.
        Dealership picked = candidates.get(0);

        lead.setDealershipId(picked.getId());
        lead.setBountyAmount(picked.getBountyPerLead());
        lead.setStatus(DealershipLead.STATUS_ROUTED);
        lead = leadRepo.save(lead);

        log.info("Graduation trigger: lead {} routed to dealership {} (province={}, bounty={})",
                lead.getId(), picked.getId(), school.getProvince(), picked.getBountyPerLead());
        return Optional.of(lead);
    }

    // ============================================================
    // Conversion + payout
    // ============================================================

    /**
     * Admin flips a lead to CONVERTED. Auto-creates an InstructorPayout
     * (PENDING) for the student's instructor — $100 default per investor
     * blueprint. Idempotent on already-converted (returns existing).
     */
    @Transactional
    public DealershipLead markConverted(Role role, UUID currentUserId, UUID leadId) {
        DealershipLead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + leadId));
        if (DealershipLead.STATUS_CONVERTED.equals(lead.getStatus())) {
            return lead; // idempotent
        }
        if (!DealershipLead.STATUS_ROUTED.equals(lead.getStatus())) {
            throw new BadRequestException(
                    "LEAD_NOT_ROUTED",
                    "Only ROUTED leads can be converted (current=" + lead.getStatus() + ")");
        }

        // V1: only OWNER of the student's school can mark CONVERTED. PR17.5
        // will accept dealership-side webhook calls.
        UUID studentId = lead.getStudentId();
        Student student = studentRepo.findById(lead.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + studentId));

        UUID schoolId = student.getSchoolId();
        School school = schoolRepo.findById(student.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + schoolId));
        if (role != Role.OWNER || !currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("Only the school OWNER can mark a lead CONVERTED");
        }

        lead.setStatus(DealershipLead.STATUS_CONVERTED);
        lead.setConvertedAt(Instant.now());
        lead = leadRepo.save(lead);

        // Auto-payout — only if the student has an assigned instructor.
        // Idempotent: an existing payout for this lead is left alone.
        if (student.getInstructorId() != null
                && payoutRepo.findByLeadId(lead.getId()).isEmpty()) {
            InstructorPayout payout = InstructorPayout.builder()
                    .instructorId(student.getInstructorId())
                    .leadId(lead.getId())
                    .payoutAmount(InstructorPayout.DEFAULT_PAYOUT_AMOUNT)
                    .status(InstructorPayout.STATUS_PENDING)
                    .build();
            payoutRepo.save(payout);
            log.info("Auto-created PENDING payout for instructor {} (lead {}, ${})",
                    student.getInstructorId(), lead.getId(),
                    InstructorPayout.DEFAULT_PAYOUT_AMOUNT);
        }

        log.info("Lead {} marked CONVERTED by OWNER {}", lead.getId(), currentUserId);
        return lead;
    }

    /**
     * Marks a payout as paid. Admin / OWNER records the e-transfer
     * reference. Idempotent on already-PAID.
     */
    @Transactional
    public InstructorPayout markPayoutPaid(Role role, UUID currentUserId, UUID payoutId, String eTransferRef) {
        InstructorPayout payout = payoutRepo.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));
        if (InstructorPayout.STATUS_PAID.equals(payout.getStatus())) {
            return payout; // idempotent
        }

        // Scope: only the OWNER of the school where the converted lead
        // originated may mark paid.
        DealershipLead lead = leadRepo.findById(payout.getLeadId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lead missing for payout: " + payoutId));
        Student student = studentRepo.findById(lead.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student missing for payout: " + payoutId));
        School school = schoolRepo.findById(student.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School missing for payout: " + payoutId));
        if (role != Role.OWNER || !currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("Only the school OWNER can mark a payout paid");
        }

        payout.setStatus(InstructorPayout.STATUS_PAID);
        payout.setETransferRef(eTransferRef == null || eTransferRef.isBlank() ? null : eTransferRef.trim());
        payout.setPaidAt(Instant.now());
        return payoutRepo.save(payout);
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public List<DealershipLeadResponse> listLeadsForOwnerSchool(Role role, UUID currentUserId, UUID schoolId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        if (role != Role.OWNER || !currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("Only the OWNER can view leads for this school");
        }
        // Walk students of this school and aggregate.
        List<DealershipLeadResponse> out = new java.util.ArrayList<>();
        for (Student s : studentRepo.findBySchoolId(schoolId)) {
            for (DealershipLead l : leadRepo.findByStudentId(s.getId())) {
                out.add(DealershipLeadResponse.fromEntity(l));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<InstructorPayout> myPayouts(Role role, UUID currentUserId, UUID instructorId) {
        // INSTRUCTOR sees their own; OWNER sees any instructor at their school.
        if (role == Role.INSTRUCTOR) {
            // The caller's instructor row ↔ the requested instructorId pair
            // is checked at the controller via SecurityUtil. Trust here.
            return payoutRepo.findByInstructorIdOrderByCreatedAtDesc(instructorId);
        }
        if (role == Role.OWNER) {
            return payoutRepo.findByInstructorIdOrderByCreatedAtDesc(instructorId);
        }
        throw new AccessDeniedException("Caller cannot view payouts");
    }
}
