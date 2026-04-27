package com.sroadtutor.school.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.school.dto.SchoolCreateRequest;
import com.sroadtutor.school.dto.SchoolUpdateRequest;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Schools CRUD + lifecycle. Locked at PR5 kickoff:
 * <ul>
 *   <li><b>One school per OWNER</b> — checked in {@link #createForCurrentOwner};
 *       fails fast with {@code ALREADY_OWN_SCHOOL}.</li>
 *   <li><b>Only OWNERs may create</b> — {@code NOT_OWNER} for any other role.</li>
 *   <li><b>Soft-delete via {@code is_active}</b> — {@link #deactivate} flips the
 *       flag; hard delete is forbidden because every other tenant row
 *       references this id (instructors, students, sessions, audit log).</li>
 *   <li><b>{@code planTier} write-locked outside PR9</b> — any attempt to flip
 *       the tier from FREE through this service is silently ignored. Stripe's
 *       webhook will be the only writer once billing lands.</li>
 *   <li><b>Back-link on create</b> — the user's {@code schoolId} is set in the
 *       same transaction so the JWT-claim path picks it up on the next refresh.</li>
 * </ul>
 *
 * <p>Cross-school reads are blocked at the controller layer via the OWNER /
 * tenant-member checks below; there is no platform-admin endpoint in V1.</p>
 */
@Service
public class SchoolService {

    private static final Logger log = LoggerFactory.getLogger(SchoolService.class);

    private final SchoolRepository schoolRepo;
    private final UserRepository userRepo;

    public SchoolService(SchoolRepository schoolRepo, UserRepository userRepo) {
        this.schoolRepo = schoolRepo;
        this.userRepo = userRepo;
    }

    // ============================================================
    // Create
    // ============================================================

    /**
     * Creates the caller's school. Requires {@link Role#OWNER}, enforces
     * one-school-per-owner, and back-links {@code users.school_id} so the
     * tenant pointer is consistent at the end of the transaction.
     */
    @Transactional
    public School createForCurrentOwner(Role role, UUID currentUserId, SchoolCreateRequest req) {
        if (role != Role.OWNER) {
            throw new BadRequestException(
                    "NOT_OWNER",
                    "Only users with role=OWNER can create a school");
        }

        User caller = userRepo.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Current user not found: " + currentUserId));

        // Two guards stacked: (1) the OWNER hasn't already created a school,
        // (2) the OWNER's user row isn't already pinned to some other school
        // (e.g. they were once an instructor at school A; that role transition
        // is out of scope for V1, so refuse the create).
        if (schoolRepo.existsByOwnerId(currentUserId)) {
            throw new BadRequestException(
                    "ALREADY_OWN_SCHOOL",
                    "This owner already has a school. One school per owner in V1.");
        }
        if (caller.getSchoolId() != null) {
            throw new BadRequestException(
                    "USER_ALREADY_IN_SCHOOL",
                    "Caller is already assigned to a school. Leave that school first.");
        }

        School school = School.builder()
                .name(req.name().trim())
                .ownerId(currentUserId)
                .jurisdiction(req.jurisdiction() == null || req.jurisdiction().isBlank()
                        ? "SGI"
                        : req.jurisdiction())
                .province(req.province())
                .gstNumber(nullIfBlank(req.gstNumber()))
                .pstNumber(nullIfBlank(req.pstNumber()))
                .hstNumber(nullIfBlank(req.hstNumber()))
                .businessRegistrationNumber(nullIfBlank(req.businessRegistrationNumber()))
                .planTier("FREE")
                .active(true)
                .synthetic(false)
                .metadata("{}")
                .build();
        school = schoolRepo.save(school);

        // Back-link: the user is now a member of the school they just created.
        // We persist this so any downstream tenant lookup ({@code PhoneOwnershipLookup},
        // future scope checkers) sees the relationship without needing to special-case
        // OWNER-without-school.
        caller.setSchoolId(school.getId());
        userRepo.save(caller);

        log.info("School {} created by OWNER {} (jurisdiction={}, province={})",
                school.getId(), currentUserId, school.getJurisdiction(), school.getProvince());
        return school;
    }

    // ============================================================
    // Reads
    // ============================================================

    /**
     * Returns the caller's school — the row whose id matches
     * {@code users.school_id}. Any authenticated role is allowed; the
     * controller maps to a leaner DTO for non-OWNER roles.
     */
    @Transactional(readOnly = true)
    public School getMine(UUID currentUserId) {
        User caller = userRepo.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Current user not found: " + currentUserId));
        if (caller.getSchoolId() == null) {
            throw new ResourceNotFoundException(
                    "Caller is not assigned to a school yet");
        }
        return schoolRepo.findById(caller.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + caller.getSchoolId()));
    }

    /**
     * Full read of a school by id. Only the OWNER of this exact school is
     * allowed; everyone else gets 403 (not 404, intentionally — we want the
     * client to know the resource exists when the caller is in the same
     * school but on a non-OWNER role).
     */
    @Transactional(readOnly = true)
    public School getById(Role role, UUID currentUserId, UUID schoolId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + schoolId));
        requireOwnerOf(role, currentUserId, school);
        return school;
    }

    // ============================================================
    // Update
    // ============================================================

    @Transactional
    public School update(Role role, UUID currentUserId, UUID schoolId, SchoolUpdateRequest req) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + schoolId));
        requireOwnerOf(role, currentUserId, school);

        if (!school.isActive()) {
            throw new BadRequestException(
                    "SCHOOL_INACTIVE",
                    "Cannot edit a deactivated school. Reactivate first.");
        }

        if (req.name() != null)         school.setName(req.name().trim());
        if (req.jurisdiction() != null) school.setJurisdiction(req.jurisdiction());
        if (req.province() != null)     school.setProvince(req.province());
        if (req.timezone() != null) {
            String tz = req.timezone().trim();
            if (tz.isEmpty()) {
                // explicit empty string is not a valid timezone — refuse rather
                // than silently revert to default
                throw new BadRequestException(
                        "INVALID_TIMEZONE",
                        "timezone must be a non-blank IANA zone id");
            }
            try {
                java.time.ZoneId.of(tz);
            } catch (java.time.DateTimeException ex) {
                throw new BadRequestException(
                        "INVALID_TIMEZONE",
                        "Unknown timezone: " + tz);
            }
            school.setTimezone(tz);
        }

        // Tax IDs accept blanks meaning "clear it"; otherwise trim + persist.
        if (req.gstNumber() != null) school.setGstNumber(nullIfBlank(req.gstNumber()));
        if (req.pstNumber() != null) school.setPstNumber(nullIfBlank(req.pstNumber()));
        if (req.hstNumber() != null) school.setHstNumber(nullIfBlank(req.hstNumber()));
        if (req.businessRegistrationNumber() != null) {
            school.setBusinessRegistrationNumber(nullIfBlank(req.businessRegistrationNumber()));
        }

        return schoolRepo.save(school);
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    /**
     * Soft-deactivates the school. Idempotent — already-inactive schools
     * are returned unchanged. Re-activation goes through {@link #reactivate}.
     */
    @Transactional
    public School deactivate(Role role, UUID currentUserId, UUID schoolId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + schoolId));
        requireOwnerOf(role, currentUserId, school);
        if (!school.isActive()) {
            return school;
        }
        school.setActive(false);
        log.info("School {} deactivated by OWNER {}", school.getId(), currentUserId);
        return schoolRepo.save(school);
    }

    @Transactional
    public School reactivate(Role role, UUID currentUserId, UUID schoolId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + schoolId));
        requireOwnerOf(role, currentUserId, school);
        if (school.isActive()) {
            return school;
        }
        school.setActive(true);
        log.info("School {} reactivated by OWNER {}", school.getId(), currentUserId);
        return schoolRepo.save(school);
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Throws {@link AccessDeniedException} (403) unless the caller is OWNER and
     * the school's {@code ownerId} matches them. The role check goes first so a
     * non-OWNER caller never learns whether the school exists.
     */
    private static void requireOwnerOf(Role role, UUID currentUserId, School school) {
        if (role != Role.OWNER) {
            throw new AccessDeniedException("Only an OWNER can manage a school");
        }
        if (!currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("OWNER can only manage their own school");
        }
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
