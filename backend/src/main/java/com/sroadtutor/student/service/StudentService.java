package com.sroadtutor.student.service;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.student.dto.AddStudentRequest;
import com.sroadtutor.student.dto.LinkParentRequest;
import com.sroadtutor.student.dto.StudentResponse;
import com.sroadtutor.student.dto.StudentUpdateRequest;
import com.sroadtutor.student.model.ParentStudent;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.ParentStudentRepository;
import com.sroadtutor.student.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Students CRUD + parent linkage. The marquee operation is
 * {@link #addByOwner}: it creates a {@link User} (role=STUDENT, dummy
 * password {@code test123}, {@code mustChangePassword=true}), the
 * {@link Student} row, and optionally a parent {@link User} +
 * {@link ParentStudent} link — all in one transaction.
 *
 * <p>Locked at PR7 kickoff:
 * <ul>
 *   <li>Caller must be either OWNER of the target school OR an INSTRUCTOR
 *       attached to that school. Students/parents cannot create other
 *       students.</li>
 *   <li>The {@code instructorId} on the request, if supplied, must belong
 *       to the same school — cross-school routing is forbidden.</li>
 *   <li>Email uniqueness for the new STUDENT user is checked up front; the
 *       service refuses to silently relink an existing email to a new
 *       student row (out of V1 scope).</li>
 *   <li>Parent linkage by email finds-or-creates: if the email already
 *       exists as a PARENT user, link to it; if it exists with any other
 *       role, refuse with {@code PARENT_EMAIL_BELONGS_TO_OTHER_ROLE};
 *       otherwise create a new PARENT user with the dummy password.</li>
 *   <li>Hard delete is forbidden — {@code status=DROPPED} retires a
 *       student, {@code status=PASSED} marks graduation.</li>
 * </ul>
 */
@Service
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    /** D6 — owner-pre-created accounts use this password and force rotation on first login. */
    static final String DUMMY_PASSWORD = "test123";

    private final StudentRepository       studentRepo;
    private final ParentStudentRepository parentLinkRepo;
    private final InstructorRepository    instructorRepo;
    private final SchoolRepository        schoolRepo;
    private final UserRepository          userRepo;
    private final PasswordEncoder         passwordEncoder;

    public StudentService(StudentRepository studentRepo,
                          ParentStudentRepository parentLinkRepo,
                          InstructorRepository instructorRepo,
                          SchoolRepository schoolRepo,
                          UserRepository userRepo,
                          PasswordEncoder passwordEncoder) {
        this.studentRepo = studentRepo;
        this.parentLinkRepo = parentLinkRepo;
        this.instructorRepo = instructorRepo;
        this.schoolRepo = schoolRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ============================================================
    // Add by owner / instructor
    // ============================================================

    @Transactional
    public StudentResponse addByOwner(Role role, UUID currentUserId, UUID schoolId, AddStudentRequest req) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        if (!school.isActive()) {
            throw new BadRequestException(
                    "SCHOOL_INACTIVE",
                    "Cannot add students to a deactivated school");
        }
        requireOwnerOrInstructorOfSchool(role, currentUserId, school);

        // ---- student email uniqueness ----
        String studentEmail = req.studentEmail().trim().toLowerCase();
        if (userRepo.existsByEmailIgnoreCase(studentEmail)) {
            throw new BadRequestException(
                    "STUDENT_EMAIL_ALREADY_EXISTS",
                    "A user with this email already exists. Use a different email.");
        }

        // ---- optional instructor must belong to the same school ----
        if (req.instructorId() != null) {
            Instructor instructor = instructorRepo.findById(req.instructorId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Instructor not found: " + req.instructorId()));
            if (instructor.getSchoolId() != null && !instructor.getSchoolId().equals(schoolId)) {
                throw new BadRequestException(
                        "INSTRUCTOR_WRONG_SCHOOL",
                        "Assigned instructor is not at this school");
            }
        }

        // ---- create the student User ----
        User studentUser = User.builder()
                .email(studentEmail)
                .username(generateUsername(studentEmail))
                .passwordHash(passwordEncoder.encode(DUMMY_PASSWORD))
                .mustChangePassword(true)
                .fullName(req.studentFullName().trim())
                .role(Role.STUDENT)
                .authProvider(AuthProvider.LOCAL)
                .schoolId(schoolId)
                .languagePref(req.languagePref() == null || req.languagePref().isBlank()
                        ? "en"
                        : req.languagePref())
                .active(true)
                .build();
        studentUser = userRepo.save(studentUser);

        // ---- create the Student row ----
        int total = req.packageTotalLessons() == null ? 0 : req.packageTotalLessons();
        int remaining = req.lessonsRemaining() == null ? total : req.lessonsRemaining();
        if (remaining > total) {
            // Caller can pass remaining > total (e.g., bonus lessons), but it's
            // worth a guard against the obvious typo. Allow ≤ total only.
            throw new BadRequestException(
                    "REMAINING_GT_TOTAL",
                    "lessonsRemaining cannot exceed packageTotalLessons");
        }
        Student student = Student.builder()
                .userId(studentUser.getId())
                .schoolId(schoolId)
                .instructorId(req.instructorId())
                .packageTotalLessons(total)
                .lessonsRemaining(remaining)
                .status(Student.STATUS_ACTIVE)
                .roadTestDate(req.roadTestDate())
                .build();
        student = studentRepo.save(student);

        // ---- optional parent linkage ----
        List<StudentResponse.ParentLink> parents = new ArrayList<>();
        if (req.parentEmail() != null && !req.parentEmail().isBlank()) {
            ParentLinkResult pl = findOrCreateParent(
                    req.parentEmail().trim().toLowerCase(),
                    req.parentFullName(),
                    schoolId);
            ParentStudent link = ParentStudent.builder()
                    .parentUserId(pl.parentUser.getId())
                    .studentId(student.getId())
                    .relationship(req.parentRelationship() == null || req.parentRelationship().isBlank()
                            ? "PARENT"
                            : req.parentRelationship())
                    .build();
            parentLinkRepo.save(link);
            parents.add(new StudentResponse.ParentLink(
                    pl.parentUser.getId(),
                    pl.parentUser.getEmail(),
                    pl.parentUser.getFullName(),
                    link.getRelationship()));
        }

        log.info("Student {} added to school {} by {} {} (parent linked: {})",
                student.getId(), schoolId, role, currentUserId, !parents.isEmpty());

        return StudentResponse.from(student, parents);
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public StudentResponse getById(Role role, UUID currentUserId, UUID studentId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        requireReadScope(role, currentUserId, student);
        return StudentResponse.from(student, loadParentLinks(student.getId()));
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> listForSchool(Role role, UUID currentUserId, UUID schoolId) {
        // OWNER of school OR INSTRUCTOR at school may list students.
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        requireOwnerOrInstructorOfSchool(role, currentUserId, school);

        List<Student> rows = studentRepo.findBySchoolId(schoolId);

        // For instructors, narrow to their assigned students. (Owner sees the full list.)
        if (role == Role.INSTRUCTOR) {
            UUID instructorId = currentInstructorId(currentUserId);
            rows = rows.stream()
                    .filter(s -> instructorId.equals(s.getInstructorId()))
                    .toList();
        }

        return rows.stream()
                .map(s -> StudentResponse.from(s, loadParentLinks(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse getMine(UUID currentUserId) {
        Student student = studentRepo.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Caller has no student profile"));
        return StudentResponse.from(student, loadParentLinks(student.getId()));
    }

    // ============================================================
    // Update
    // ============================================================

    @Transactional
    public StudentResponse update(Role role, UUID currentUserId, UUID studentId, StudentUpdateRequest req) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        requireWriteScope(role, currentUserId, student);

        if (req.instructorId() != null) {
            Instructor instructor = instructorRepo.findById(req.instructorId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Instructor not found: " + req.instructorId()));
            if (instructor.getSchoolId() != null && !instructor.getSchoolId().equals(student.getSchoolId())) {
                throw new BadRequestException(
                        "INSTRUCTOR_WRONG_SCHOOL",
                        "Assigned instructor is not at this student's school");
            }
            student.setInstructorId(req.instructorId());
        }
        if (req.packageTotalLessons() != null) student.setPackageTotalLessons(req.packageTotalLessons());
        if (req.lessonsRemaining() != null)    student.setLessonsRemaining(req.lessonsRemaining());

        if (student.getLessonsRemaining() > student.getPackageTotalLessons()) {
            throw new BadRequestException(
                    "REMAINING_GT_TOTAL",
                    "lessonsRemaining cannot exceed packageTotalLessons");
        }

        if (req.status() != null)        student.setStatus(req.status());
        if (req.roadTestDate() != null)  student.setRoadTestDate(req.roadTestDate());

        student = studentRepo.save(student);
        return StudentResponse.from(student, loadParentLinks(student.getId()));
    }

    // ============================================================
    // Parent link management
    // ============================================================

    @Transactional
    public StudentResponse linkParent(Role role, UUID currentUserId, UUID studentId, LinkParentRequest req) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        requireWriteScope(role, currentUserId, student);

        ParentLinkResult pl = findOrCreateParent(
                req.parentEmail().trim().toLowerCase(),
                req.parentFullName(),
                student.getSchoolId());

        if (parentLinkRepo.existsByParentUserIdAndStudentId(pl.parentUser.getId(), student.getId())) {
            throw new BadRequestException(
                    "PARENT_ALREADY_LINKED",
                    "This parent is already linked to this student");
        }

        ParentStudent link = ParentStudent.builder()
                .parentUserId(pl.parentUser.getId())
                .studentId(student.getId())
                .relationship(req.relationship() == null || req.relationship().isBlank()
                        ? "PARENT"
                        : req.relationship())
                .build();
        parentLinkRepo.save(link);

        log.info("Parent {} linked to student {} by {} {}",
                pl.parentUser.getId(), student.getId(), role, currentUserId);

        return StudentResponse.from(student, loadParentLinks(student.getId()));
    }

    @Transactional
    public void unlinkParent(Role role, UUID currentUserId, UUID studentId, UUID parentUserId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        requireWriteScope(role, currentUserId, student);

        ParentStudent link = parentLinkRepo.findByParentUserIdAndStudentId(parentUserId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent " + parentUserId + " is not linked to student " + studentId));
        parentLinkRepo.delete(link);

        log.info("Parent {} unlinked from student {} by {} {}",
                parentUserId, studentId, role, currentUserId);
    }

    // ============================================================
    // Internals
    // ============================================================

    /** Holds the looked-up-or-created parent user + a "was created now?" flag. */
    private record ParentLinkResult(User parentUser, boolean newlyCreated) {}

    private ParentLinkResult findOrCreateParent(String parentEmail, String parentFullName, UUID schoolId) {
        Optional<User> existing = userRepo.findByEmailIgnoreCase(parentEmail);
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.getRole() != Role.PARENT) {
                throw new BadRequestException(
                        "PARENT_EMAIL_BELONGS_TO_OTHER_ROLE",
                        "Email " + parentEmail + " already belongs to a " + u.getRole()
                                + " account; cannot link as PARENT.");
            }
            return new ParentLinkResult(u, false);
        }
        // Create a fresh PARENT user with the dummy password — same pattern as
        // the student creation above. They'll be forced to rotate on first login.
        User parentUser = User.builder()
                .email(parentEmail)
                .username(generateUsername(parentEmail))
                .passwordHash(passwordEncoder.encode(DUMMY_PASSWORD))
                .mustChangePassword(true)
                .fullName(parentFullName == null ? null : parentFullName.trim())
                .role(Role.PARENT)
                .authProvider(AuthProvider.LOCAL)
                .schoolId(schoolId)
                .languagePref("en")
                .active(true)
                .build();
        parentUser = userRepo.save(parentUser);
        return new ParentLinkResult(parentUser, true);
    }

    private List<StudentResponse.ParentLink> loadParentLinks(UUID studentId) {
        List<ParentStudent> links = parentLinkRepo.findByStudentId(studentId);
        if (links.isEmpty()) return List.of();
        List<StudentResponse.ParentLink> out = new ArrayList<>(links.size());
        for (ParentStudent link : links) {
            userRepo.findById(link.getParentUserId()).ifPresent(parent ->
                    out.add(new StudentResponse.ParentLink(
                            parent.getId(),
                            parent.getEmail(),
                            parent.getFullName(),
                            link.getRelationship())));
        }
        return out;
    }

    /**
     * Mirrors the username generator on {@code AuthService}: lowercased email
     * local-part (truncated to 50 chars) + "_" + 8 hex chars from a fresh UUID.
     */
    private static String generateUsername(String email) {
        String local = email == null ? "user" : email.split("@", 2)[0];
        if (local.length() > 50) local = local.substring(0, 50);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return (local + "_" + suffix).toLowerCase();
    }

    // ============================================================
    // Scope helpers
    // ============================================================

    /**
     * Read scope: OWNER of student's school, INSTRUCTOR assigned to this
     * student, the student themselves, or a parent linked to this student.
     */
    private void requireReadScope(Role role, UUID currentUserId, Student student) {
        switch (role) {
            case OWNER -> {
                Optional<School> s = schoolRepo.findById(student.getSchoolId());
                if (s.isPresent() && currentUserId.equals(s.get().getOwnerId())) return;
            }
            case INSTRUCTOR -> {
                if (student.getInstructorId() != null) {
                    UUID myInstructorId = currentInstructorIdOrNull(currentUserId);
                    if (myInstructorId != null && myInstructorId.equals(student.getInstructorId())) return;
                }
            }
            case STUDENT -> {
                if (currentUserId.equals(student.getUserId())) return;
            }
            case PARENT -> {
                if (parentLinkRepo.existsByParentUserIdAndStudentId(currentUserId, student.getId())) return;
            }
        }
        throw new AccessDeniedException("Caller cannot read this student");
    }

    /**
     * Write scope: OWNER of school OR INSTRUCTOR assigned to this student.
     * Students and parents are read-only at V1.
     */
    private void requireWriteScope(Role role, UUID currentUserId, Student student) {
        switch (role) {
            case OWNER -> {
                Optional<School> s = schoolRepo.findById(student.getSchoolId());
                if (s.isPresent() && currentUserId.equals(s.get().getOwnerId())) return;
            }
            case INSTRUCTOR -> {
                if (student.getInstructorId() != null) {
                    UUID myInstructorId = currentInstructorIdOrNull(currentUserId);
                    if (myInstructorId != null && myInstructorId.equals(student.getInstructorId())) return;
                }
            }
            default -> { /* fall through to denial */ }
        }
        throw new AccessDeniedException("Caller cannot edit this student");
    }

    private void requireOwnerOrInstructorOfSchool(Role role, UUID currentUserId, School school) {
        switch (role) {
            case OWNER -> {
                if (currentUserId.equals(school.getOwnerId())) return;
            }
            case INSTRUCTOR -> {
                Optional<Instructor> me = instructorRepo.findByUserId(currentUserId);
                if (me.isPresent() && school.getId().equals(me.get().getSchoolId())) return;
            }
            default -> { /* deny */ }
        }
        throw new AccessDeniedException("Caller cannot manage students at this school");
    }

    private UUID currentInstructorId(UUID currentUserId) {
        return instructorRepo.findByUserId(currentUserId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Caller has no instructor profile"))
                .getId();
    }

    private UUID currentInstructorIdOrNull(UUID currentUserId) {
        return instructorRepo.findByUserId(currentUserId).map(Instructor::getId).orElse(null);
    }
}
