package com.sroadtutor.marketplace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.marketplace.dto.DealershipLeadResponse;
import com.sroadtutor.marketplace.dto.SubmitMatchmakerRequest;
import com.sroadtutor.marketplace.model.DealershipLead;
import com.sroadtutor.marketplace.repository.DealershipLeadRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.ParentStudentRepository;
import com.sroadtutor.student.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * First Car Matchmaker — parent-side intake. Locked at PR16:
 *
 * <ul>
 *   <li><b>PARENT only</b> — students don't submit (they typically don't
 *       buy the car), instructors don't submit on parents' behalf in V1.
 *       OWNER could submit too but the use-case is parent-initiated.</li>
 *   <li><b>Linked-student check</b> — caller must be linked to the student
 *       via {@code parent_student}.</li>
 *   <li><b>One active NEW lead per student</b> — re-submitting overwrites
 *       the existing NEW lead in place rather than piling up duplicates.
 *       Once the lead is ROUTED or CONVERTED, a re-submit creates a fresh
 *       NEW row (the old one belongs to the routed dealership).</li>
 * </ul>
 */
@Service
public class MatchmakerService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakerService.class);

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule()).build();

    private final DealershipLeadRepository leadRepo;
    private final StudentRepository studentRepo;
    private final ParentStudentRepository parentLinkRepo;

    public MatchmakerService(DealershipLeadRepository leadRepo,
                              StudentRepository studentRepo,
                              ParentStudentRepository parentLinkRepo) {
        this.leadRepo = leadRepo;
        this.studentRepo = studentRepo;
        this.parentLinkRepo = parentLinkRepo;
    }

    @Transactional
    public DealershipLeadResponse submit(Role role, UUID currentUserId, SubmitMatchmakerRequest req) {
        if (role != Role.PARENT) {
            throw new AccessDeniedException("Only a PARENT can submit the First Car Matchmaker");
        }
        Student student = studentRepo.findById(req.studentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + req.studentId()));
        if (!parentLinkRepo.existsByParentUserIdAndStudentId(currentUserId, student.getId())) {
            throw new AccessDeniedException("Student is not linked to this parent");
        }

        String prefsJson = serialize(req.vehiclePreferences());

        // Reuse an existing NEW lead if one exists — the parent is updating
        // their preferences before graduation rather than creating a duplicate.
        DealershipLead lead = leadRepo
                .findFirstByStudentIdAndStatus(student.getId(), DealershipLead.STATUS_NEW)
                .orElseGet(() -> DealershipLead.builder()
                        .studentId(student.getId())
                        .parentUserId(currentUserId)
                        .status(DealershipLead.STATUS_NEW)
                        .build());

        lead.setParentUserId(currentUserId);
        lead.setVehiclePrefJson(prefsJson);
        lead.setBudget(req.budget());
        lead.setFinancingReady(req.financingReady());
        lead = leadRepo.save(lead);

        log.info("Matchmaker submitted by PARENT {} for student {} (lead {})",
                currentUserId, student.getId(), lead.getId());
        return DealershipLeadResponse.fromEntity(lead);
    }

    @Transactional(readOnly = true)
    public List<DealershipLeadResponse> myLeads(Role role, UUID currentUserId) {
        if (role != Role.PARENT) {
            throw new AccessDeniedException("Only a PARENT can view their submitted leads");
        }
        return leadRepo.findByParentUserId(currentUserId).stream()
                .map(DealershipLeadResponse::fromEntity)
                .toList();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static String serialize(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return "{}";
        try {
            return JSON.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException(
                    "INVALID_VEHICLE_PREFERENCES",
                    "Could not serialise vehicle preferences: " + ex.getMessage());
        }
    }
}
