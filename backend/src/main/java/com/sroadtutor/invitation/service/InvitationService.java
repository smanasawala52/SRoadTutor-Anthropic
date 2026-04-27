package com.sroadtutor.invitation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.model.InstructorSchool;
import com.sroadtutor.instructor.model.InstructorSchoolId;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.instructor.repository.InstructorSchoolRepository;
import com.sroadtutor.invitation.dto.AcceptInvitationRequest;
import com.sroadtutor.invitation.dto.CreateInstructorInvitationRequest;
import com.sroadtutor.invitation.dto.CreateInvitationResponse;
import com.sroadtutor.invitation.dto.CreateParentInvitationRequest;
import com.sroadtutor.invitation.dto.CreateStudentInvitationRequest;
import com.sroadtutor.invitation.dto.InvitationLookupResponse;
import com.sroadtutor.invitation.model.Invitation;
import com.sroadtutor.invitation.repository.InvitationRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner-issued invitations for instructors / students / parents.
 *
 * <p>Two delivery modes — locked at PR8 kickoff:
 * <ul>
 *   <li><b>{@code TOKEN}</b> — random URL-safe token, SHA-256 hashed at rest;
 *       raw token returned once at create time. Invitee POSTs to
 *       {@code /api/invitations/{token}/accept} with their chosen password.
 *       Acceptance creates the User + role-specific row + (where applicable)
 *       linkages — all in one transaction.</li>
 *   <li><b>{@code DUMMY_PWD}</b> — owner pre-creates the User with the
 *       {@code test123} password and {@code mustChangePassword=true}; the
 *       role-specific row is created in the SAME transaction. Invitation
 *       row is marked {@code ACCEPTED} immediately.</li>
 * </ul>
 *
 * <p>Pre-population (per ADR-17): owner-supplied profile defaults are
 * serialised into {@code invitations.metadata} JSONB and applied when the
 * Instructor / Student row is created — either at accept time (TOKEN) or
 * inline at invite time (DUMMY_PWD).</p>
 *
 * <p>Authorization:
 * <ul>
 *   <li>Create — OWNER of the target school for instructor/parent invites.
 *       OWNER or attached INSTRUCTOR for student invites.</li>
 *   <li>List / revoke / reissue — OWNER of the school only.</li>
 *   <li>Lookup / accept — public (token bearer is the auth proof).</li>
 * </ul>
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    /** Per I2: 7 days. */
    static final Duration TOKEN_TTL = Duration.ofDays(7);

    /** Same dummy password used by {@code StudentService}. */
    static final String DUMMY_PASSWORD = "test123";

    /** Dev base URL for the accept link. SPA replaces with its own when wiring email. */
    private static final String DEV_ACCEPT_URL_BASE = "http://localhost:5173/accept-invite/";

    public static final String STATUS_PENDING  = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_EXPIRED  = "EXPIRED";
    public static final String STATUS_REVOKED  = "REVOKED";

    public static final String MODE_TOKEN     = "TOKEN";
    public static final String MODE_DUMMY_PWD = "DUMMY_PWD";

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule()).build();

    private final InvitationRepository      invRepo;
    private final UserRepository            userRepo;
    private final SchoolRepository          schoolRepo;
    private final InstructorRepository      instructorRepo;
    private final InstructorSchoolRepository linkRepo;
    private final StudentRepository         studentRepo;
    private final ParentStudentRepository   parentLinkRepo;
    private final PasswordEncoder           passwordEncoder;
    private final SecureRandom              random = new SecureRandom();

    public InvitationService(InvitationRepository invRepo,
                             UserRepository userRepo,
                             SchoolRepository schoolRepo,
                             InstructorRepository instructorRepo,
                             InstructorSchoolRepository linkRepo,
                             StudentRepository studentRepo,
                             ParentStudentRepository parentLinkRepo,
                             PasswordEncoder passwordEncoder) {
        this.invRepo = invRepo;
        this.userRepo = userRepo;
        this.schoolRepo = schoolRepo;
        this.instructorRepo = instructorRepo;
        this.linkRepo = linkRepo;
        this.studentRepo = studentRepo;
        this.parentLinkRepo = parentLinkRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ============================================================
    // Create — instructor
    // ============================================================

    @Transactional
    public CreateInvitationResponse createInstructorInvitation(
            Role role, UUID currentUserId, UUID schoolId, CreateInstructorInvitationRequest req) {

        School school = requireOwnerOfActiveSchool(role, currentUserId, schoolId);

        String email = req.email().trim().toLowerCase();
        guardAgainstExistingUserOrPendingInvite(email);

        String mode = req.deliveryMode() == null ? MODE_TOKEN : req.deliveryMode();
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "roleAtSchool",  req.roleAtSchool() == null ? "REGULAR" : req.roleAtSchool());
        putIfPresent(metadata, "licenseNo",     req.licenseNo());
        putIfPresent(metadata, "sgiCert",       req.sgiCert());
        putIfPresent(metadata, "vehicleMake",   req.vehicleMake());
        putIfPresent(metadata, "vehicleModel",  req.vehicleModel());
        putIfPresent(metadata, "vehicleYear",   req.vehicleYear());
        putIfPresent(metadata, "vehiclePlate",  req.vehiclePlate());
        putIfPresent(metadata, "bio",           req.bio());
        putIfPresent(metadata, "hourlyRate",    req.hourlyRate());

        return persistAndMaybeAccept(school, currentUserId, email, req.fullName().trim(),
                Role.INSTRUCTOR.name(), mode, metadata);
    }

    // ============================================================
    // Create — student
    // ============================================================

    @Transactional
    public CreateInvitationResponse createStudentInvitation(
            Role role, UUID currentUserId, UUID schoolId, CreateStudentInvitationRequest req) {

        School school = requireOwnerOrInstructorOfActiveSchool(role, currentUserId, schoolId);

        String email = req.email().trim().toLowerCase();
        guardAgainstExistingUserOrPendingInvite(email);

        // If an instructorId is supplied, validate it belongs to this school
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

        String mode = req.deliveryMode() == null ? MODE_TOKEN : req.deliveryMode();
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "instructorId",         req.instructorId());
        putIfPresent(metadata, "packageTotalLessons", req.packageTotalLessons());
        putIfPresent(metadata, "roadTestDate",         req.roadTestDate());
        putIfPresent(metadata, "parentEmail",
                req.parentEmail() == null ? null : req.parentEmail().trim().toLowerCase());
        putIfPresent(metadata, "parentFullName",       req.parentFullName());
        putIfPresent(metadata, "parentRelationship",   req.parentRelationship());

        return persistAndMaybeAccept(school, currentUserId, email, req.fullName().trim(),
                Role.STUDENT.name(), mode, metadata);
    }

    // ============================================================
    // Create — parent
    // ============================================================

    @Transactional
    public CreateInvitationResponse createParentInvitation(
            Role role, UUID currentUserId, UUID schoolId, CreateParentInvitationRequest req) {

        School school = requireOwnerOfActiveSchool(role, currentUserId, schoolId);

        String email = req.email().trim().toLowerCase();
        guardAgainstExistingUserOrPendingInvite(email);

        // If studentIds supplied, validate every one belongs to this school
        if (req.studentIds() != null) {
            for (UUID sid : req.studentIds()) {
                Student s = studentRepo.findById(sid)
                        .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + sid));
                if (!s.getSchoolId().equals(schoolId)) {
                    throw new BadRequestException(
                            "STUDENT_WRONG_SCHOOL",
                            "Student " + sid + " is not at this school");
                }
            }
        }

        String mode = req.deliveryMode() == null ? MODE_TOKEN : req.deliveryMode();
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "relationship",
                req.relationship() == null || req.relationship().isBlank() ? "PARENT" : req.relationship());
        if (req.studentIds() != null && !req.studentIds().isEmpty()) {
            metadata.put("studentIds", req.studentIds());
        }

        return persistAndMaybeAccept(school, currentUserId, email, req.fullName().trim(),
                Role.PARENT.name(), mode, metadata);
    }

    // ============================================================
    // Lookup (public — pre-accept landing page)
    // ============================================================

    @Transactional(readOnly = true)
    public InvitationLookupResponse lookupByToken(String rawToken) {
        Invitation inv = requireFreshInvitation(rawToken);
        String schoolName = schoolRepo.findById(inv.getSchoolId())
                .map(School::getName)
                .orElse(null);
        return new InvitationLookupResponse(
                inv.getEmail(),
                tryFullNameFromMetadata(inv),
                inv.getRole(),
                inv.getDeliveryMode(),
                inv.getStatus(),
                schoolName);
    }

    // ============================================================
    // Accept (public — token is the auth)
    // ============================================================

    @Transactional
    public CreateInvitationResponse acceptByToken(String rawToken, AcceptInvitationRequest req) {
        Invitation inv = requireFreshInvitation(rawToken);
        if (!MODE_TOKEN.equals(inv.getDeliveryMode())) {
            throw new BadRequestException(
                    "WRONG_DELIVERY_MODE",
                    "This invitation does not require token-acceptance");
        }
        if (userRepo.existsByEmailIgnoreCase(inv.getEmail())) {
            // Race: someone signed up with the same email after the invite
            // was issued. Refuse — owner must revoke and re-issue.
            throw new BadRequestException(
                    "EMAIL_ALREADY_HAS_ACCOUNT",
                    "An account with this email already exists");
        }

        String passwordHash = passwordEncoder.encode(req.password());
        String langPref = req.languagePref() == null || req.languagePref().isBlank()
                ? "en" : req.languagePref();

        User newUser = createUserForInvitation(inv, passwordHash, false, langPref);
        applyRoleSpecificMetadata(inv, newUser);

        Instant now = Instant.now();
        inv.setStatus(STATUS_ACCEPTED);
        inv.setAcceptedAt(now);
        inv.setAcceptedUserId(newUser.getId());
        invRepo.save(inv);

        log.info("Invitation {} accepted (TOKEN) — created {} {} for {}",
                inv.getId(), inv.getRole(), newUser.getId(), inv.getEmail());

        return toCreateResponse(inv, null, null, newUser.getId());
    }

    // ============================================================
    // List / revoke / reissue
    // ============================================================

    @Transactional(readOnly = true)
    public List<Invitation> listForSchool(Role role, UUID currentUserId, UUID schoolId, String status) {
        requireOwnerOfActiveSchool(role, currentUserId, schoolId);
        if (status == null || status.isBlank()) {
            return invRepo.findBySchoolId(schoolId);
        }
        return invRepo.findBySchoolIdAndStatus(schoolId, status);
    }

    @Transactional
    public Invitation revoke(Role role, UUID currentUserId, UUID invitationId) {
        Invitation inv = invRepo.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invitation not found: " + invitationId));
        requireOwnerOfActiveSchool(role, currentUserId, inv.getSchoolId());
        if (STATUS_ACCEPTED.equals(inv.getStatus())) {
            throw new BadRequestException(
                    "INVITATION_ALREADY_ACCEPTED",
                    "Cannot revoke an already-accepted invitation");
        }
        if (STATUS_REVOKED.equals(inv.getStatus())) {
            return inv; // idempotent
        }
        inv.setStatus(STATUS_REVOKED);
        log.info("Invitation {} revoked by OWNER {}", invitationId, currentUserId);
        return invRepo.save(inv);
    }

    @Transactional
    public CreateInvitationResponse reissue(Role role, UUID currentUserId, UUID invitationId) {
        Invitation inv = invRepo.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invitation not found: " + invitationId));
        requireOwnerOfActiveSchool(role, currentUserId, inv.getSchoolId());

        if (STATUS_ACCEPTED.equals(inv.getStatus())) {
            throw new BadRequestException(
                    "INVITATION_ALREADY_ACCEPTED",
                    "Cannot reissue an accepted invitation");
        }
        if (!MODE_TOKEN.equals(inv.getDeliveryMode())) {
            throw new BadRequestException(
                    "WRONG_DELIVERY_MODE",
                    "Only TOKEN-mode invitations can be reissued");
        }

        Instant now = Instant.now();
        String rawToken = randomUrlSafeToken();
        inv.setTokenHash(sha256Hex(rawToken));
        inv.setStatus(STATUS_PENDING);
        inv.setExpiresAt(now.plus(TOKEN_TTL));
        invRepo.save(inv);

        String acceptUrl = DEV_ACCEPT_URL_BASE + rawToken;
        log.info("Invitation {} reissued by OWNER {} — new expiry {}", invitationId, currentUserId, inv.getExpiresAt());
        return toCreateResponse(inv, rawToken, acceptUrl, null);
    }

    // ============================================================
    // Internals — persist + maybe-accept
    // ============================================================

    private CreateInvitationResponse persistAndMaybeAccept(
            School school, UUID inviterUserId, String email, String fullName,
            String role, String mode, Map<String, Object> metadata) {

        if (!MODE_TOKEN.equals(mode) && !MODE_DUMMY_PWD.equals(mode)) {
            throw new BadRequestException(
                    "INVALID_DELIVERY_MODE",
                    "deliveryMode must be TOKEN or DUMMY_PWD");
        }
        // Ensure fullName persists in metadata so the lookup endpoint can render it.
        metadata.putIfAbsent("fullName", fullName);

        Instant now = Instant.now();
        String rawToken = null;
        String acceptUrl = null;
        String tokenHash = null;
        if (MODE_TOKEN.equals(mode)) {
            rawToken = randomUrlSafeToken();
            tokenHash = sha256Hex(rawToken);
            acceptUrl = DEV_ACCEPT_URL_BASE + rawToken;
        }

        Invitation inv = Invitation.builder()
                .schoolId(school.getId())
                .invitedByUserId(inviterUserId)
                .email(email)
                .username(generateUsername(email))
                .role(role)
                .deliveryMode(mode)
                .tokenHash(tokenHash) // null for DUMMY_PWD per chk_invitation_token_or_dummy
                .status(STATUS_PENDING)
                .expiresAt(now.plus(TOKEN_TTL))
                .acceptedAt(null)
                .acceptedUserId(null)
                .metadata(serialize(metadata))
                .build();
        inv = invRepo.save(inv);

        if (MODE_DUMMY_PWD.equals(mode)) {
            // Pre-create the user + role row right now; mark invitation accepted.
            User newUser = createUserForInvitation(inv, passwordEncoder.encode(DUMMY_PASSWORD), true, "en");
            applyRoleSpecificMetadata(inv, newUser);
            inv.setStatus(STATUS_ACCEPTED);
            inv.setAcceptedAt(now);
            inv.setAcceptedUserId(newUser.getId());
            invRepo.save(inv);

            log.info("Invitation {} accepted (DUMMY_PWD) — created {} {} for {}",
                    inv.getId(), inv.getRole(), newUser.getId(), email);
            return toCreateResponse(inv, null, null, newUser.getId());
        }

        log.info("Invitation {} created (TOKEN) for {} as {}", inv.getId(), email, role);
        return toCreateResponse(inv, rawToken, acceptUrl, null);
    }

    private CreateInvitationResponse toCreateResponse(
            Invitation inv, String rawToken, String acceptUrl, UUID acceptedUserId) {
        return new CreateInvitationResponse(
                inv.getId(),
                inv.getEmail(),
                inv.getRole(),
                inv.getDeliveryMode(),
                inv.getStatus(),
                rawToken,
                acceptUrl,
                acceptedUserId == null ? inv.getAcceptedUserId() : acceptedUserId,
                inv.getExpiresAt(),
                inv.getCreatedAt());
    }

    // ============================================================
    // Internals — User + role-specific row creation
    // ============================================================

    private User createUserForInvitation(Invitation inv, String passwordHash,
                                         boolean mustChangePassword, String languagePref) {
        Role role = Role.valueOf(inv.getRole());
        Map<String, Object> meta = parse(inv.getMetadata());
        String fullName = strFromMeta(meta, "fullName");

        User user = User.builder()
                .email(inv.getEmail())
                .username(inv.getUsername())
                .passwordHash(passwordHash)
                .mustChangePassword(mustChangePassword)
                .fullName(fullName)
                .role(role)
                .authProvider(AuthProvider.LOCAL)
                .schoolId(inv.getSchoolId())
                .languagePref(languagePref)
                .active(true)
                .build();
        return userRepo.save(user);
    }

    private void applyRoleSpecificMetadata(Invitation inv, User newUser) {
        Map<String, Object> meta = parse(inv.getMetadata());
        Role role = Role.valueOf(inv.getRole());
        switch (role) {
            case INSTRUCTOR -> applyInstructorMetadata(inv, newUser, meta);
            case STUDENT    -> applyStudentMetadata(inv, newUser, meta);
            case PARENT     -> applyParentMetadata(inv, newUser, meta);
            default -> { /* OWNER never invited */ }
        }
    }

    private void applyInstructorMetadata(Invitation inv, User newUser, Map<String, Object> meta) {
        Instructor instructor = Instructor.builder()
                .userId(newUser.getId())
                .schoolId(inv.getSchoolId())
                .licenseNo(strFromMeta(meta, "licenseNo"))
                .sgiCert(strFromMeta(meta, "sgiCert"))
                .vehicleMake(strFromMeta(meta, "vehicleMake"))
                .vehicleModel(strFromMeta(meta, "vehicleModel"))
                .vehicleYear(intFromMeta(meta, "vehicleYear"))
                .vehiclePlate(strFromMeta(meta, "vehiclePlate"))
                .bio(strFromMeta(meta, "bio"))
                .hourlyRate(decimalFromMeta(meta, "hourlyRate"))
                .active(true)
                .build();
        instructor = instructorRepo.save(instructor);

        String roleAtSchool = strFromMeta(meta, "roleAtSchool");
        InstructorSchool link = InstructorSchool.builder()
                .id(new InstructorSchoolId(instructor.getId(), inv.getSchoolId()))
                .roleAtSchool(roleAtSchool == null ? "REGULAR" : roleAtSchool)
                .joinedAt(Instant.now())
                .build();
        linkRepo.save(link);
    }

    private void applyStudentMetadata(Invitation inv, User newUser, Map<String, Object> meta) {
        UUID instructorId = uuidFromMeta(meta, "instructorId");
        Integer total = intFromMeta(meta, "packageTotalLessons");
        int packageTotal = total == null ? 0 : total;

        Student student = Student.builder()
                .userId(newUser.getId())
                .schoolId(inv.getSchoolId())
                .instructorId(instructorId)
                .packageTotalLessons(packageTotal)
                .lessonsRemaining(packageTotal)
                .status(Student.STATUS_ACTIVE)
                .roadTestDate(localDateFromMeta(meta, "roadTestDate"))
                .build();
        student = studentRepo.save(student);

        // Optional parent linkage
        String parentEmail = strFromMeta(meta, "parentEmail");
        if (parentEmail != null && !parentEmail.isBlank()) {
            String parentFullName = strFromMeta(meta, "parentFullName");
            String relationship = strFromMeta(meta, "parentRelationship");
            User parent = findOrCreateParent(parentEmail, parentFullName, inv.getSchoolId());
            ParentStudent link = ParentStudent.builder()
                    .parentUserId(parent.getId())
                    .studentId(student.getId())
                    .relationship(relationship == null || relationship.isBlank() ? "PARENT" : relationship)
                    .build();
            parentLinkRepo.save(link);
        }
    }

    private void applyParentMetadata(Invitation inv, User newUser, Map<String, Object> meta) {
        @SuppressWarnings("unchecked")
        List<Object> studentIds = (List<Object>) meta.get("studentIds");
        if (studentIds == null || studentIds.isEmpty()) return;
        String relationship = strFromMeta(meta, "relationship");
        String rel = relationship == null || relationship.isBlank() ? "PARENT" : relationship;
        for (Object sid : studentIds) {
            UUID studentId = UUID.fromString(sid.toString());
            // Pair-uniqueness already enforced at DB; pre-check is a friendly guard.
            if (parentLinkRepo.existsByParentUserIdAndStudentId(newUser.getId(), studentId)) continue;
            ParentStudent link = ParentStudent.builder()
                    .parentUserId(newUser.getId())
                    .studentId(studentId)
                    .relationship(rel)
                    .build();
            parentLinkRepo.save(link);
        }
    }

    private User findOrCreateParent(String email, String fullName, UUID schoolId) {
        Optional<User> existing = userRepo.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.getRole() != Role.PARENT) {
                throw new BadRequestException(
                        "PARENT_EMAIL_BELONGS_TO_OTHER_ROLE",
                        "Email " + email + " already belongs to a " + u.getRole()
                                + " account; cannot link as PARENT.");
            }
            return u;
        }
        User parent = User.builder()
                .email(email)
                .username(generateUsername(email))
                .passwordHash(passwordEncoder.encode(DUMMY_PASSWORD))
                .mustChangePassword(true)
                .fullName(fullName == null ? null : fullName.trim())
                .role(Role.PARENT)
                .authProvider(AuthProvider.LOCAL)
                .schoolId(schoolId)
                .languagePref("en")
                .active(true)
                .build();
        return userRepo.save(parent);
    }

    // ============================================================
    // Scope / guards
    // ============================================================

    private School requireOwnerOfActiveSchool(Role role, UUID currentUserId, UUID schoolId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        if (!school.isActive()) {
            throw new BadRequestException("SCHOOL_INACTIVE", "School is deactivated");
        }
        if (role != Role.OWNER) {
            throw new AccessDeniedException("Only an OWNER can issue invitations of this kind");
        }
        if (!currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("OWNER can only manage their own school");
        }
        return school;
    }

    private School requireOwnerOrInstructorOfActiveSchool(Role role, UUID currentUserId, UUID schoolId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        if (!school.isActive()) {
            throw new BadRequestException("SCHOOL_INACTIVE", "School is deactivated");
        }
        switch (role) {
            case OWNER -> {
                if (currentUserId.equals(school.getOwnerId())) return school;
            }
            case INSTRUCTOR -> {
                Optional<Instructor> me = instructorRepo.findByUserId(currentUserId);
                if (me.isPresent() && schoolId.equals(me.get().getSchoolId())) return school;
            }
            default -> { /* deny */ }
        }
        throw new AccessDeniedException("Caller cannot invite at this school");
    }

    private void guardAgainstExistingUserOrPendingInvite(String email) {
        if (userRepo.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException(
                    "EMAIL_ALREADY_HAS_ACCOUNT",
                    "An account with this email already exists");
        }
        List<Invitation> pending = invRepo.findByEmailIgnoreCaseAndStatus(email, STATUS_PENDING);
        if (!pending.isEmpty()) {
            throw new BadRequestException(
                    "INVITATION_ALREADY_PENDING",
                    "A pending invitation already exists for this email");
        }
    }

    private Invitation requireFreshInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("INVALID_INVITATION_TOKEN", "Token is required");
        }
        Invitation inv = invRepo.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new BadRequestException(
                        "INVALID_INVITATION_TOKEN",
                        "Invitation token not recognised"));
        Instant now = Instant.now();
        if (STATUS_REVOKED.equals(inv.getStatus())) {
            throw new BadRequestException(
                    "INVITATION_REVOKED",
                    "This invitation has been revoked");
        }
        if (STATUS_ACCEPTED.equals(inv.getStatus())) {
            throw new BadRequestException(
                    "INVITATION_ALREADY_ACCEPTED",
                    "This invitation has already been accepted");
        }
        if (inv.getExpiresAt() != null && !inv.getExpiresAt().isAfter(now)) {
            // Lazy-mark EXPIRED on first read after expiry.
            inv.setStatus(STATUS_EXPIRED);
            invRepo.save(inv);
            throw new BadRequestException(
                    "INVITATION_EXPIRED",
                    "This invitation has expired");
        }
        if (STATUS_EXPIRED.equals(inv.getStatus())) {
            throw new BadRequestException(
                    "INVITATION_EXPIRED",
                    "This invitation has expired");
        }
        return inv;
    }

    // ============================================================
    // Tiny helpers (token, hash, JSON, username, metadata getters)
    // ============================================================

    private String randomUrlSafeToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String generateUsername(String email) {
        String local = email == null ? "user" : email.split("@", 2)[0];
        if (local.length() > 50) local = local.substring(0, 50);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return (local + "_" + suffix).toLowerCase();
    }

    private static void putIfPresent(Map<String, Object> m, String key, Object value) {
        if (value == null) return;
        if (value instanceof String s && s.isBlank()) return;
        m.put(key, value);
    }

    private static String serialize(Map<String, Object> m) {
        try {
            return JSON.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize invitation metadata", e);
        }
    }

    private static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = JSON.readValue(json, Map.class);
            return m;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse invitation metadata: " + e.getMessage(), e);
        }
    }

    private static String tryFullNameFromMetadata(Invitation inv) {
        Map<String, Object> m = parse(inv.getMetadata());
        return strFromMeta(m, "fullName");
    }

    private static String strFromMeta(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static Integer intFromMeta(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID uuidFromMeta(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal decimalFromMeta(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate localDateFromMeta(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try {
            return LocalDate.parse(v.toString());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
