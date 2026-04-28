package com.sroadtutor.insurance.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.insurance.dto.InsuranceLeadResponse;
import com.sroadtutor.insurance.model.InsuranceBroker;
import com.sroadtutor.insurance.model.InsuranceLead;
import com.sroadtutor.insurance.repository.InsuranceBrokerRepository;
import com.sroadtutor.insurance.repository.InsuranceLeadRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Insurance lead routing + lifecycle. Locked at PR18:
 *
 * <ul>
 *   <li><b>Auto-create on graduation</b> ({@link #onStudentPassed}) — invoked
 *       from {@code StudentService.update} alongside the dealership trigger.
 *       Creates a single InsuranceLead per student-graduation event. Picks
 *       an eligible broker (province-match preferred, nationwide fallback)
 *       and copies the broker's {@code bountyPerQuote} onto the lead.
 *       Falls back to NEW if no eligible broker exists.</li>
 *   <li><b>markQuoted</b> — broker confirms a quote was started; that's
 *       the bounty trigger per investor blueprint ("bounty per quote
 *       started"). OWNER of the school records this on behalf of the broker
 *       in V1; future PR adds a B2B-API endpoint.</li>
 *   <li><b>markConverted</b> — broker bound a policy. Future commission
 *       tier (out of V1 scope; we just track the timestamp).</li>
 *   <li><b>Reads</b> — OWNER sees their school's leads. List active
 *       brokers is open to any authenticated role.</li>
 * </ul>
 */
@Service
public class InsuranceLeadService {

    private static final Logger log = LoggerFactory.getLogger(InsuranceLeadService.class);

    private final InsuranceLeadRepository leadRepo;
    private final InsuranceBrokerRepository brokerRepo;
    private final StudentRepository studentRepo;
    private final SchoolRepository schoolRepo;

    public InsuranceLeadService(InsuranceLeadRepository leadRepo,
                                  InsuranceBrokerRepository brokerRepo,
                                  StudentRepository studentRepo,
                                  SchoolRepository schoolRepo) {
        this.leadRepo = leadRepo;
        this.brokerRepo = brokerRepo;
        this.studentRepo = studentRepo;
        this.schoolRepo = schoolRepo;
    }

    // ============================================================
    // Graduation trigger
    // ============================================================

    /**
     * Called from {@code StudentService.update} when {@code status} flips
     * to PASSED. Returns the routed lead, or empty if creation was skipped
     * (e.g. an existing non-DEAD lead already exists). Never throws — same
     * fail-soft contract as the dealership graduation trigger.
     */
    @Transactional
    public Optional<InsuranceLead> onStudentPassed(UUID studentId) {
        // Skip if the student already has any non-DEAD lead — graduation
        // shouldn't fire a duplicate insurance pipeline.
        Optional<InsuranceLead> existing = leadRepo
                .findFirstByStudentIdOrderByCreatedAtDesc(studentId);
        if (existing.isPresent()
                && !InsuranceLead.STATUS_DEAD.equals(existing.get().getStatus())) {
            log.info("Insurance graduation: student {} already has lead {} (status={}) — skipping",
                    studentId, existing.get().getId(), existing.get().getStatus());
            return Optional.empty();
        }

        Student student = studentRepo.findById(studentId).orElse(null);
        if (student == null) return Optional.empty();
        School school = schoolRepo.findById(student.getSchoolId()).orElse(null);
        if (school == null) return Optional.empty();

        // Province-specific brokers preferred; nationwide brokers come last
        // in the eligible list per the repo's ORDER clause. Pick the head.
        List<InsuranceBroker> eligible = brokerRepo.findEligibleForProvince(school.getProvince());
        InsuranceBroker picked = eligible.isEmpty() ? null : eligible.get(0);

        InsuranceLead.InsuranceLeadBuilder builder = InsuranceLead.builder()
                .studentId(studentId);
        if (picked == null) {
            builder.status(InsuranceLead.STATUS_NEW);
            log.info("Insurance graduation: no eligible broker for province {} — lead stays NEW",
                    school.getProvince());
        } else {
            builder.status(InsuranceLead.STATUS_ROUTED)
                    .brokerId(picked.getId())
                    .bountyAmount(picked.getBountyPerQuote());
            log.info("Insurance graduation: lead routed for student {} → broker {} (province={}, bounty={})",
                    studentId, picked.getId(), school.getProvince(), picked.getBountyPerQuote());
        }
        InsuranceLead lead = leadRepo.save(builder.build());
        return Optional.of(lead);
    }

    // ============================================================
    // Quote / convert
    // ============================================================

    @Transactional
    public InsuranceLead markQuoted(Role role, UUID currentUserId, UUID leadId) {
        InsuranceLead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance lead not found: " + leadId));
        if (InsuranceLead.STATUS_QUOTED.equals(lead.getStatus())
                || InsuranceLead.STATUS_CONVERTED.equals(lead.getStatus())) {
            return lead; // idempotent — already past QUOTED
        }
        if (!InsuranceLead.STATUS_ROUTED.equals(lead.getStatus())) {
            throw new BadRequestException(
                    "INSURANCE_LEAD_NOT_ROUTED",
                    "Only ROUTED leads can be marked QUOTED (current=" + lead.getStatus() + ")");
        }
        requireOwnerOfSchoolForLead(role, currentUserId, lead);

        lead.setStatus(InsuranceLead.STATUS_QUOTED);
        lead.setQuotedAt(Instant.now());
        log.info("Insurance lead {} marked QUOTED by OWNER {}", leadId, currentUserId);
        return leadRepo.save(lead);
    }

    @Transactional
    public InsuranceLead markConverted(Role role, UUID currentUserId, UUID leadId) {
        InsuranceLead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance lead not found: " + leadId));
        if (InsuranceLead.STATUS_CONVERTED.equals(lead.getStatus())) {
            return lead;
        }
        if (!InsuranceLead.STATUS_QUOTED.equals(lead.getStatus())) {
            throw new BadRequestException(
                    "INSURANCE_LEAD_NOT_QUOTED",
                    "Only QUOTED leads can be marked CONVERTED (current=" + lead.getStatus() + ")");
        }
        requireOwnerOfSchoolForLead(role, currentUserId, lead);

        lead.setStatus(InsuranceLead.STATUS_CONVERTED);
        lead.setConvertedAt(Instant.now());
        log.info("Insurance lead {} marked CONVERTED by OWNER {}", leadId, currentUserId);
        return leadRepo.save(lead);
    }

    @Transactional
    public InsuranceLead markDead(Role role, UUID currentUserId, UUID leadId) {
        InsuranceLead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance lead not found: " + leadId));
        if (InsuranceLead.STATUS_DEAD.equals(lead.getStatus())) return lead;
        if (InsuranceLead.STATUS_CONVERTED.equals(lead.getStatus())) {
            throw new BadRequestException(
                    "INSURANCE_LEAD_ALREADY_CONVERTED",
                    "A CONVERTED lead cannot be marked DEAD");
        }
        requireOwnerOfSchoolForLead(role, currentUserId, lead);
        lead.setStatus(InsuranceLead.STATUS_DEAD);
        return leadRepo.save(lead);
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public List<InsuranceLeadResponse> listLeadsForOwnerSchool(Role role, UUID currentUserId, UUID schoolId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        if (role != Role.OWNER || !currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("Only the OWNER can view insurance leads for this school");
        }
        List<InsuranceLeadResponse> out = new ArrayList<>();
        for (Student s : studentRepo.findBySchoolId(schoolId)) {
            for (InsuranceLead l : leadRepo.findByStudentId(s.getId())) {
                out.add(InsuranceLeadResponse.fromEntity(l));
            }
        }
        return out;
    }

    // ============================================================
    // Scope helper
    // ============================================================

    private void requireOwnerOfSchoolForLead(Role role, UUID currentUserId, InsuranceLead lead) {
        if (lead.getStudentId() == null) {
            throw new AccessDeniedException("Lead has no student — cannot resolve scope");
        }
        Student s = studentRepo.findById(lead.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student missing for lead: " + lead.getId()));
        School school = schoolRepo.findById(s.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School missing for lead: " + lead.getId()));
        if (role != Role.OWNER || !currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("Only the school OWNER can manage this insurance lead");
        }
    }
}
