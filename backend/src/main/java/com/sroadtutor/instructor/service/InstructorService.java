package com.sroadtutor.instructor.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.dto.InstructorCreateRequest;
import com.sroadtutor.instructor.dto.InstructorUpdateRequest;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.model.InstructorSchool;
import com.sroadtutor.instructor.model.InstructorSchoolId;
import com.sroadtutor.instructor.model.WorkingHours;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.instructor.repository.InstructorSchoolRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.subscription.service.PlanLimitsService;
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
 * Instructors CRUD + multi-school attachment. Locked at PR6 kickoff:
 * <ul>
 *   <li><b>Self-register flow</b> — a {@code role=INSTRUCTOR} user calls
 *       {@link #createForCurrentUser} after signup to spin up their profile.
 *       No school attachment yet; the user is "available" until an OWNER
 *       attaches them.</li>
 *   <li><b>Owner attaches</b> — {@link #attachToSchool} writes an
 *       {@link InstructorSchool} join row (M:N), and back-links
 *       {@code users.school_id} if the instructor is unaffiliated.</li>
 *   <li><b>Owner detaches</b> — {@link #detachFromSchool} sets {@code leftAt}
 *       on the join row (soft delete). Historical rows stay around for audit.</li>
 *   <li><b>Profile edits</b> — {@link #update} accepts edits from either the
 *       instructor themselves or an OWNER of a school they're attached to.</li>
 *   <li><b>One profile per user</b> — UNIQUE on {@code instructors.user_id}
 *       at the DB; enforced at the service layer with a friendly error.</li>
 * </ul>
 */
@Service
public class InstructorService {

    private static final Logger log = LoggerFactory.getLogger(InstructorService.class);

    private final InstructorRepository instructorRepo;
    private final InstructorSchoolRepository linkRepo;
    private final SchoolRepository schoolRepo;
    private final UserRepository userRepo;

    public InstructorService(InstructorRepository instructorRepo,
                             InstructorSchoolRepository linkRepo,
                             SchoolRepository schoolRepo,
                             UserRepository userRepo) {
        this.instructorRepo = instructorRepo;
        this.linkRepo = linkRepo;
        this.schoolRepo = schoolRepo;
        this.userRepo = userRepo;
    }

    // ============================================================
    // Create (self-register)
    // ============================================================

    @Transactional
    public Instructor createForCurrentUser(Role role, UUID currentUserId, InstructorCreateRequest req) {
        if (role != Role.INSTRUCTOR) {
            throw new BadRequestException(
                    "NOT_INSTRUCTOR",
                    "Only users with role=INSTRUCTOR can create an instructor profile");
        }
        if (instructorRepo.existsByUserId(currentUserId)) {
            throw new BadRequestException(
                    "INSTRUCTOR_ALREADY_EXISTS",
                    "This user already has an instructor profile");
        }

        Instructor instructor = Instructor.builder()
                .userId(currentUserId)
                .licenseNo(nullIfBlank(req.licenseNo()))
                .sgiCert(nullIfBlank(req.sgiCert()))
                .vehicleMake(nullIfBlank(req.vehicleMake()))
                .vehicleModel(nullIfBlank(req.vehicleModel()))
                .vehicleYear(req.vehicleYear())
                .vehiclePlate(nullIfBlank(req.vehiclePlate()))
                .bio(nullIfBlank(req.bio()))
                .hourlyRate(req.hourlyRate())
                .workingHoursJson(req.workingHours() == null ? null : req.workingHours().toJson())
                .active(true)
                .build();
        instructor = instructorRepo.save(instructor);

        log.info("Instructor {} created (self-register) for user {}", instructor.getId(), currentUserId);
        return instructor;
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public Instructor getMine(UUID currentUserId) {
        return instructorRepo.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Caller has no instructor profile"));
    }

    @Transactional(readOnly = true)
    public Instructor getById(Role role, UUID currentUserId, UUID instructorId) {
        Instructor instructor = instructorRepo.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instructor not found: " + instructorId));
        requireReadScope(role, currentUserId, instructor);
        return instructor;
    }

    @Transactional(readOnly = true)
    public List<Instructor> listForSchool(Role role, UUID currentUserId, UUID schoolId) {
        // OWNER of this school OR an instructor at this school may list.
        // Students/parents do not get the roster for V1.
        requireSchoolMember(role, currentUserId, schoolId);
        return instructorRepo.findActiveBySchool(schoolId);
    }

    // ============================================================
    // Update
    // ============================================================

    @Transactional
    public Instructor update(Role role, UUID currentUserId, UUID instructorId, InstructorUpdateRequest req) {
        Instructor instructor = instructorRepo.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instructor not found: " + instructorId));
        requireWriteScope(role, currentUserId, instructor);

        if (!instructor.isActive()) {
            throw new BadRequestException(
                    "INSTRUCTOR_INACTIVE",
                    "Cannot edit a deactivated instructor profile");
        }

        if (req.licenseNo() != null)    instructor.setLicenseNo(nullIfBlank(req.licenseNo()));
        if (req.sgiCert() != null)      instructor.setSgiCert(nullIfBlank(req.sgiCert()));
        if (req.vehicleMake() != null)  instructor.setVehicleMake(nullIfBlank(req.vehicleMake()));
        if (req.vehicleModel() != null) instructor.setVehicleModel(nullIfBlank(req.vehicleModel()));
        if (req.vehicleYear() != null)  instructor.setVehicleYear(req.vehicleYear());
        if (req.vehiclePlate() != null) instructor.setVehiclePlate(nullIfBlank(req.vehiclePlate()));
        if (req.bio() != null)          instructor.setBio(nullIfBlank(req.bio()));
        if (req.hourlyRate() != null)   instructor.setHourlyRate(req.hourlyRate());
        if (req.workingHours() != null) instructor.setWorkingHoursJson(req.workingHours().toJson());

        return instructorRepo.save(instructor);
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    @Transactional
    public Instructor deactivate(Role role, UUID currentUserId, UUID instructorId) {
        Instructor instructor = instructorRepo.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instructor not found: " + instructorId));
        requireWriteScope(role, currentUserId, instructor);
        if (!instructor.isActive()) return instructor;
        instructor.setActive(false);
        log.info("Instructor {} deactivated by user {}", instructorId, currentUserId);
        return instructorRepo.save(instructor);
    }

    // ============================================================
    // School attach / detach (M:N)
    // ============================================================

    @Transactional
    public InstructorSchool attachToSchool(Role role, UUID currentUserId,
                                            UUID schoolId, UUID instructorId,
                                            String roleAtSchool) {
        // Only the OWNER of the target school may attach.
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + schoolId));
        requireOwnerOfSchool(role, currentUserId, school);
        if (!school.isActive()) {
            throw new BadRequestException(
                    "SCHOOL_INACTIVE",
                    "Cannot attach instructors to a deactivated school");
        }

        Instructor instructor = instructorRepo.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instructor not found: " + instructorId));
        if (!instructor.isActive()) {
            throw new BadRequestException(
                    "INSTRUCTOR_INACTIVE",
                    "Cannot attach a deactivated instructor");
        }

        InstructorSchoolId pk = new InstructorSchoolId(instructorId, schoolId);
        Optional<InstructorSchool> existing = linkRepo.findById(pk);
        InstructorSchool link;
        if (existing.isPresent()) {
            link = existing.get();
            if (link.getLeftAt() == null) {
                throw new BadRequestException(
                        "ALREADY_ATTACHED",
                        "Instructor is already attached to this school");
            }
            // Re-attach a previously-departed instructor: clear leftAt, refresh
            // joinedAt + roleAtSchool. We keep the same row to preserve audit.
            link.setLeftAt(null);
            link.setJoinedAt(Instant.now());
            if (roleAtSchool != null) link.setRoleAtSchool(roleAtSchool);
        } else {
            link = InstructorSchool.builder()
                    .id(pk)
                    .roleAtSchool(roleAtSchool == null ? "REGULAR" : roleAtSchool)
                    .joinedAt(Instant.now())
                    .build();
        }
        link = linkRepo.save(link);

        // If the instructor's user has no school_id yet, point it at this one
        // so JWT claims and downstream tenant lookups have a stable answer.
        userRepo.findById(instructor.getUserId()).ifPresent(u -> {
            if (u.getSchoolId() == null) {
                u.setSchoolId(schoolId);
                userRepo.save(u);
            }
        });

        // Likewise back-fill the legacy instructors.school_id if blank — keeps
        // queries that haven't migrated to the M:N join still pointing at a
        // sensible default.
        if (instructor.getSchoolId() == null) {
            instructor.setSchoolId(schoolId);
            instructorRepo.save(instructor);
        }

        log.info("Instructor {} attached to school {} as {} by OWNER {}",
                instructorId, schoolId, link.getRoleAtSchool(), currentUserId);
        return link;
    }

    @Transactional
    public InstructorSchool detachFromSchool(Role role, UUID currentUserId,
                                              UUID schoolId, UUID instructorId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + schoolId));
        requireOwnerOfSchool(role, currentUserId, school);

        InstructorSchool link = linkRepo.findById(new InstructorSchoolId(instructorId, schoolId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instructor is not attached to this school"));
        if (link.getLeftAt() != null) {
            // Idempotent — already detached.
            return link;
        }
        link.setLeftAt(Instant.now());
        log.info("Instructor {} detached from school {} by OWNER {}",
                instructorId, schoolId, currentUserId);
        return linkRepo.save(link);
    }

    // ============================================================
    // Scope helpers
    // ============================================================

    /**
     * Read scope: instructor themselves, OR the OWNER of any school the
     * instructor is currently attached to. (Cross-school OWNERs cannot read
     * each other's instructor rosters.)
     */
    private void requireReadScope(Role role, UUID currentUserId, Instructor instructor) {
        if (currentUserId.equals(instructor.getUserId())) {
            return;
        }
        if (role == Role.OWNER && callerOwnsAnyAttachedSchool(currentUserId, instructor.getId())) {
            return;
        }
        throw new AccessDeniedException("Caller cannot read this instructor profile");
    }

    /**
     * Write scope: same as read for now — instructor themselves OR the
     * OWNER of any attached school. We may tighten "owner can edit licenseNo
     * but instructor can't" later, but V1 trusts both equally.
     */
    private void requireWriteScope(Role role, UUID currentUserId, Instructor instructor) {
        if (currentUserId.equals(instructor.getUserId())) {
            return;
        }
        if (role == Role.OWNER && callerOwnsAnyAttachedSchool(currentUserId, instructor.getId())) {
            return;
        }
        throw new AccessDeniedException("Caller cannot edit this instructor profile");
    }

    private boolean callerOwnsAnyAttachedSchool(UUID currentUserId, UUID instructorId) {
        List<InstructorSchool> active = linkRepo.findByIdInstructorIdAndLeftAtIsNull(instructorId);
        for (InstructorSchool js : active) {
            Optional<School> s = schoolRepo.findById(js.getId().getSchoolId());
            if (s.isPresent() && currentUserId.equals(s.get().getOwnerId())) {
                return true;
            }
        }
        return false;
    }

    private static void requireOwnerOfSchool(Role role, UUID currentUserId, School school) {
        if (role != Role.OWNER) {
            throw new AccessDeniedException("Only an OWNER can manage instructor attachments");
        }
        if (!currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("OWNER can only manage their own school");
        }
    }

    private void requireSchoolMember(Role role, UUID currentUserId, UUID schoolId) {
        // OWNER of THIS school always allowed.
        Optional<School> s = schoolRepo.findById(schoolId);
        if (s.isEmpty()) {
            throw new ResourceNotFoundException("School not found: " + schoolId);
        }
        if (role == Role.OWNER && currentUserId.equals(s.get().getOwnerId())) {
            return;
        }
        // INSTRUCTOR currently attached to this school is allowed (so they can
        // see who else teaches there). Students/parents are not.
        if (role == Role.INSTRUCTOR) {
            Optional<Instructor> me = instructorRepo.findByUserId(currentUserId);
            if (me.isPresent()) {
                List<InstructorSchool> links = linkRepo.findByIdInstructorIdAndLeftAtIsNull(me.get().getId());
                for (InstructorSchool js : links) {
                    if (js.getId().getSchoolId().equals(schoolId)) return;
                }
            }
        }
        throw new AccessDeniedException("Caller cannot list instructors at this school");
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
