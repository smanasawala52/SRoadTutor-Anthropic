package com.sroadtutor.invitation.service;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.model.InstructorSchool;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.instructor.repository.InstructorSchoolRepository;
import com.sroadtutor.invitation.dto.AcceptInvitationRequest;
import com.sroadtutor.invitation.dto.CreateInstructorInvitationRequest;
import com.sroadtutor.invitation.dto.CreateInvitationResponse;
import com.sroadtutor.invitation.dto.CreateParentInvitationRequest;
import com.sroadtutor.invitation.dto.CreateStudentInvitationRequest;
import com.sroadtutor.invitation.model.Invitation;
import com.sroadtutor.invitation.repository.InvitationRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.student.model.ParentStudent;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.ParentStudentRepository;
import com.sroadtutor.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvitationServiceTest {

    @Mock InvitationRepository       invRepo;
    @Mock UserRepository             userRepo;
    @Mock SchoolRepository           schoolRepo;
    @Mock InstructorRepository       instructorRepo;
    @Mock InstructorSchoolRepository linkRepo;
    @Mock StudentRepository          studentRepo;
    @Mock ParentStudentRepository    parentLinkRepo;
    @Mock PasswordEncoder            passwordEncoder;

    @InjectMocks InvitationService service;

    // ============================================================
    // Instructor invite — TOKEN mode
    // ============================================================

    @Test
    void instructorInvite_tokenMode_returnsRawTokenAndDoesNotCreateUserYet() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("inst@x.com")).thenReturn(false);
        when(invRepo.findByEmailIgnoreCaseAndStatus(anyString(), anyString())).thenReturn(List.of());
        when(invRepo.save(any(Invitation.class))).thenAnswer(inv -> {
            Invitation i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            if (i.getCreatedAt() == null) i.setCreatedAt(Instant.now());
            return i;
        });

        var req = new CreateInstructorInvitationRequest(
                "inst@x.com", "Jane Inst", "TOKEN", "REGULAR",
                null, null, "Toyota", "Corolla", 2022, "ABC123",
                null, new BigDecimal("70.00"));

        CreateInvitationResponse resp = service.createInstructorInvitation(Role.OWNER, ownerId, schoolId, req);

        assertThat(resp.rawToken()).isNotBlank();
        assertThat(resp.acceptUrlForDev()).contains(resp.rawToken());
        assertThat(resp.deliveryMode()).isEqualTo("TOKEN");
        assertThat(resp.status()).isEqualTo("PENDING");
        assertThat(resp.acceptedUserId()).isNull();

        // No user/instructor created yet
        verify(userRepo, never()).save(any(User.class));
        verify(instructorRepo, never()).save(any(Instructor.class));
    }

    // ============================================================
    // Instructor invite — DUMMY_PWD mode (auto-accepted)
    // ============================================================

    @Test
    void instructorInvite_dummyMode_createsUserInstructorAndLinkInline() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("inst@x.com")).thenReturn(false);
        when(invRepo.findByEmailIgnoreCaseAndStatus(anyString(), anyString())).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(invRepo.save(any(Invitation.class))).thenAnswer(inv -> {
            Invitation i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            if (i.getCreatedAt() == null) i.setCreatedAt(Instant.now());
            return i;
        });
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        when(instructorRepo.save(any(Instructor.class))).thenAnswer(inv -> {
            Instructor i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });
        when(linkRepo.save(any(InstructorSchool.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new CreateInstructorInvitationRequest(
                "inst@x.com", "Jane Inst", "DUMMY_PWD", "HEAD",
                "L-123", "SGI-456", "Toyota", "Corolla", 2022, "ABC123",
                "Bio text", new BigDecimal("70.00"));

        CreateInvitationResponse resp = service.createInstructorInvitation(Role.OWNER, ownerId, schoolId, req);

        assertThat(resp.deliveryMode()).isEqualTo("DUMMY_PWD");
        assertThat(resp.status()).isEqualTo("ACCEPTED");
        assertThat(resp.rawToken()).isNull();
        assertThat(resp.acceptedUserId()).isNotNull();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCap.capture());
        assertThat(userCap.getValue().getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(userCap.getValue().getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(userCap.getValue().isMustChangePassword()).isTrue();
        assertThat(userCap.getValue().getPasswordHash()).isEqualTo("HASHED");
        assertThat(userCap.getValue().getSchoolId()).isEqualTo(schoolId);

        ArgumentCaptor<Instructor> insCap = ArgumentCaptor.forClass(Instructor.class);
        verify(instructorRepo).save(insCap.capture());
        assertThat(insCap.getValue().getLicenseNo()).isEqualTo("L-123");
        assertThat(insCap.getValue().getHourlyRate()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(insCap.getValue().getVehicleYear()).isEqualTo(2022);

        verify(linkRepo).save(any(InstructorSchool.class));
    }

    // ============================================================
    // Guards
    // ============================================================

    @Test
    void invite_rejectsExistingEmail() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("dup@x.com")).thenReturn(true);

        var req = new CreateInstructorInvitationRequest(
                "dup@x.com", "X", "TOKEN", null,
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createInstructorInvitation(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("EMAIL_ALREADY_HAS_ACCOUNT"));
    }

    @Test
    void invite_rejectsExistingPendingInvite() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("dup@x.com")).thenReturn(false);
        when(invRepo.findByEmailIgnoreCaseAndStatus("dup@x.com", "PENDING"))
                .thenReturn(List.of(Invitation.builder().id(UUID.randomUUID()).email("dup@x.com").status("PENDING").build()));

        var req = new CreateInstructorInvitationRequest(
                "dup@x.com", "X", "TOKEN", null,
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createInstructorInvitation(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INVITATION_ALREADY_PENDING"));
    }

    @Test
    void invite_rejectsInactiveSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(false).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        var req = new CreateInstructorInvitationRequest(
                "x@x.com", "X", "TOKEN", null,
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createInstructorInvitation(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SCHOOL_INACTIVE"));
    }

    @Test
    void invite_403ForNonOwner() {
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(UUID.randomUUID()).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        var req = new CreateInstructorInvitationRequest(
                "x@x.com", "X", "TOKEN", null,
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createInstructorInvitation(
                Role.INSTRUCTOR, UUID.randomUUID(), schoolId, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============================================================
    // Student invite — INSTRUCTOR can issue at their own school
    // ============================================================

    @Test
    void studentInvite_instructorAtSchool_canIssue() {
        UUID instUserId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(UUID.randomUUID()).active(true).build();
        Instructor me = Instructor.builder().id(instructorId).userId(instUserId).schoolId(schoolId).active(true).build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(instructorRepo.findByUserId(instUserId)).thenReturn(Optional.of(me));
        when(userRepo.existsByEmailIgnoreCase("kid@x.com")).thenReturn(false);
        when(invRepo.findByEmailIgnoreCaseAndStatus(anyString(), anyString())).thenReturn(List.of());
        when(invRepo.save(any(Invitation.class))).thenAnswer(inv -> {
            Invitation i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            if (i.getCreatedAt() == null) i.setCreatedAt(Instant.now());
            return i;
        });

        var req = new CreateStudentInvitationRequest(
                "kid@x.com", "Tom", "TOKEN",
                null, 10, null, null, null, null);

        CreateInvitationResponse resp = service.createStudentInvitation(
                Role.INSTRUCTOR, instUserId, schoolId, req);
        assertThat(resp.role()).isEqualTo("STUDENT");
        assertThat(resp.status()).isEqualTo("PENDING");
    }

    @Test
    void studentInvite_dummyMode_createsStudentRowWithMetadata() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("kid@x.com")).thenReturn(false);
        when(invRepo.findByEmailIgnoreCaseAndStatus(anyString(), anyString())).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(invRepo.save(any(Invitation.class))).thenAnswer(inv -> {
            Invitation i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            if (i.getCreatedAt() == null) i.setCreatedAt(Instant.now());
            return i;
        });
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        when(studentRepo.save(any(Student.class))).thenAnswer(inv -> {
            Student st = inv.getArgument(0);
            if (st.getId() == null) st.setId(UUID.randomUUID());
            return st;
        });

        var req = new CreateStudentInvitationRequest(
                "kid@x.com", "Tom", "DUMMY_PWD",
                null, 8, null, null, null, null);

        CreateInvitationResponse resp = service.createStudentInvitation(Role.OWNER, ownerId, schoolId, req);
        assertThat(resp.status()).isEqualTo("ACCEPTED");

        ArgumentCaptor<Student> stCap = ArgumentCaptor.forClass(Student.class);
        verify(studentRepo).save(stCap.capture());
        assertThat(stCap.getValue().getPackageTotalLessons()).isEqualTo(8);
        assertThat(stCap.getValue().getLessonsRemaining()).isEqualTo(8);
        assertThat(stCap.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    // ============================================================
    // Accept (TOKEN flow)
    // ============================================================

    @Test
    void accept_succeedsForFreshTokenAndCreatesUser() {
        String raw = "raw-accept-token";
        UUID schoolId = UUID.randomUUID();
        Invitation inv = Invitation.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .invitedByUserId(UUID.randomUUID())
                .email("inst@x.com").username("inst_aaaaaaaa")
                .role(Role.INSTRUCTOR.name()).deliveryMode("TOKEN")
                .tokenHash(InvitationService.sha256Hex(raw))
                .status("PENDING")
                .expiresAt(Instant.now().plusSeconds(3600))
                .metadata("{\"fullName\":\"Jane\",\"roleAtSchool\":\"REGULAR\"}")
                .build();

        when(invRepo.findByTokenHash(inv.getTokenHash())).thenReturn(Optional.of(inv));
        when(userRepo.existsByEmailIgnoreCase("inst@x.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(userRepo.save(any(User.class))).thenAnswer(invc -> {
            User u = invc.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        when(instructorRepo.save(any(Instructor.class))).thenAnswer(invc -> {
            Instructor i = invc.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });
        when(linkRepo.save(any(InstructorSchool.class))).thenAnswer(invc -> invc.getArgument(0));
        when(invRepo.save(any(Invitation.class))).thenAnswer(invc -> invc.getArgument(0));

        var req = new AcceptInvitationRequest("supersecret", null);
        CreateInvitationResponse resp = service.acceptByToken(raw, req);
        assertThat(resp.status()).isEqualTo("ACCEPTED");
        assertThat(resp.acceptedUserId()).isNotNull();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCap.capture());
        assertThat(userCap.getValue().isMustChangePassword()).isFalse();   // user chose own password
    }

    @Test
    void accept_rejectsExpiredInvitation() {
        String raw = "expired-token";
        Invitation inv = Invitation.builder()
                .id(UUID.randomUUID())
                .schoolId(UUID.randomUUID())
                .email("x@x.com").username("x_aaaaaaaa")
                .role(Role.STUDENT.name()).deliveryMode("TOKEN")
                .tokenHash(InvitationService.sha256Hex(raw))
                .status("PENDING")
                .expiresAt(Instant.now().minusSeconds(60))
                .metadata("{}")
                .build();
        when(invRepo.findByTokenHash(inv.getTokenHash())).thenReturn(Optional.of(inv));
        when(invRepo.save(any(Invitation.class))).thenAnswer(invc -> invc.getArgument(0));

        var req = new AcceptInvitationRequest("supersecret", null);
        assertThatThrownBy(() -> service.acceptByToken(raw, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INVITATION_EXPIRED"));
        // Lazy-expire should flip the status
        assertThat(inv.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void accept_rejectsRevokedInvitation() {
        String raw = "revoked-token";
        Invitation inv = Invitation.builder()
                .id(UUID.randomUUID())
                .email("x@x.com").username("x_aaaaaaaa")
                .role(Role.STUDENT.name()).deliveryMode("TOKEN")
                .tokenHash(InvitationService.sha256Hex(raw))
                .status("REVOKED")
                .expiresAt(Instant.now().plusSeconds(3600))
                .metadata("{}")
                .build();
        when(invRepo.findByTokenHash(inv.getTokenHash())).thenReturn(Optional.of(inv));

        var req = new AcceptInvitationRequest("supersecret", null);
        assertThatThrownBy(() -> service.acceptByToken(raw, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INVITATION_REVOKED"));
    }

    @Test
    void accept_rejectsAlreadyAccepted() {
        String raw = "double-token";
        Invitation inv = Invitation.builder()
                .id(UUID.randomUUID())
                .email("x@x.com").username("x_aaaaaaaa")
                .role(Role.STUDENT.name()).deliveryMode("TOKEN")
                .tokenHash(InvitationService.sha256Hex(raw))
                .status("ACCEPTED")
                .expiresAt(Instant.now().plusSeconds(3600))
                .metadata("{}")
                .build();
        when(invRepo.findByTokenHash(inv.getTokenHash())).thenReturn(Optional.of(inv));

        var req = new AcceptInvitationRequest("supersecret", null);
        assertThatThrownBy(() -> service.acceptByToken(raw, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INVITATION_ALREADY_ACCEPTED"));
    }

    @Test
    void accept_rejectsDummyModeViaTokenAccept() {
        String raw = "dummy-token";
        Invitation inv = Invitation.builder()
                .id(UUID.randomUUID())
                .email("x@x.com").username("x_aaaaaaaa")
                .role(Role.STUDENT.name()).deliveryMode("DUMMY_PWD")
                .tokenHash(null)  // dummy mode has no token
                .status("PENDING")
                .expiresAt(Instant.now().plusSeconds(3600))
                .metadata("{}")
                .build();
        // The lookup-by-token simulates a DUMMY_PWD invite somehow surfaced
        // in the accept flow. We expect WRONG_DELIVERY_MODE.
        when(invRepo.findByTokenHash(InvitationService.sha256Hex(raw))).thenReturn(Optional.of(inv));

        var req = new AcceptInvitationRequest("supersecret", null);
        assertThatThrownBy(() -> service.acceptByToken(raw, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("WRONG_DELIVERY_MODE"));
    }

    // ============================================================
    // Revoke / reissue
    // ============================================================

    @Test
    void revoke_setsStatusAndIsIdempotent() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Invitation inv = Invitation.builder()
                .id(invId).schoolId(schoolId).email("x@x.com").role("INSTRUCTOR")
                .deliveryMode("TOKEN").status("PENDING").expiresAt(Instant.now().plusSeconds(3600))
                .metadata("{}").build();

        when(invRepo.findById(invId)).thenReturn(Optional.of(inv));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(invRepo.save(any(Invitation.class))).thenAnswer(invc -> invc.getArgument(0));

        Invitation after = service.revoke(Role.OWNER, ownerId, invId);
        assertThat(after.getStatus()).isEqualTo("REVOKED");

        // second call: idempotent — status stays REVOKED, no new save needed
        when(invRepo.findById(invId)).thenReturn(Optional.of(after));
        Invitation again = service.revoke(Role.OWNER, ownerId, invId);
        assertThat(again.getStatus()).isEqualTo("REVOKED");
    }

    @Test
    void revoke_rejectsAcceptedInvite() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Invitation inv = Invitation.builder()
                .id(invId).schoolId(schoolId).email("x@x.com").role("INSTRUCTOR")
                .deliveryMode("TOKEN").status("ACCEPTED").expiresAt(Instant.now().plusSeconds(3600))
                .metadata("{}").build();
        when(invRepo.findById(invId)).thenReturn(Optional.of(inv));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.revoke(Role.OWNER, ownerId, invId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INVITATION_ALREADY_ACCEPTED"));
    }

    @Test
    void reissue_generatesFreshToken() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        String oldHash = "OLD-HASH";
        Invitation inv = Invitation.builder()
                .id(invId).schoolId(schoolId).email("x@x.com").role("INSTRUCTOR")
                .deliveryMode("TOKEN").status("EXPIRED").tokenHash(oldHash)
                .expiresAt(Instant.now().minusSeconds(3600))
                .metadata("{}").build();
        when(invRepo.findById(invId)).thenReturn(Optional.of(inv));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(invRepo.save(any(Invitation.class))).thenAnswer(invc -> invc.getArgument(0));

        CreateInvitationResponse resp = service.reissue(Role.OWNER, ownerId, invId);
        assertThat(resp.rawToken()).isNotBlank();
        assertThat(inv.getTokenHash()).isNotEqualTo(oldHash);
        assertThat(inv.getStatus()).isEqualTo("PENDING");
        assertThat(inv.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void reissue_rejectsAccepted() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Invitation inv = Invitation.builder()
                .id(invId).schoolId(schoolId).email("x@x.com").role("INSTRUCTOR")
                .deliveryMode("TOKEN").status("ACCEPTED").expiresAt(Instant.now().plusSeconds(3600))
                .metadata("{}").build();
        when(invRepo.findById(invId)).thenReturn(Optional.of(inv));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.reissue(Role.OWNER, ownerId, invId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INVITATION_ALREADY_ACCEPTED"));
    }

    // ============================================================
    // Parent invite with student linkage
    // ============================================================

    @Test
    void parentInvite_dummyMode_linksStudents() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId1 = UUID.randomUUID();
        UUID studentId2 = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Student st1 = Student.builder().id(studentId1).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        Student st2 = Student.builder().id(studentId2).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("mom@x.com")).thenReturn(false);
        when(invRepo.findByEmailIgnoreCaseAndStatus(anyString(), anyString())).thenReturn(List.of());
        when(studentRepo.findById(studentId1)).thenReturn(Optional.of(st1));
        when(studentRepo.findById(studentId2)).thenReturn(Optional.of(st2));
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(invRepo.save(any(Invitation.class))).thenAnswer(invc -> {
            Invitation i = invc.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            if (i.getCreatedAt() == null) i.setCreatedAt(Instant.now());
            return i;
        });
        when(userRepo.save(any(User.class))).thenAnswer(invc -> {
            User u = invc.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        when(parentLinkRepo.existsByParentUserIdAndStudentId(any(), any())).thenReturn(false);
        when(parentLinkRepo.save(any(ParentStudent.class))).thenAnswer(invc -> invc.getArgument(0));

        var req = new CreateParentInvitationRequest(
                "mom@x.com", "Sarah", "DUMMY_PWD",
                "GUARDIAN", List.of(studentId1, studentId2));

        CreateInvitationResponse resp = service.createParentInvitation(Role.OWNER, ownerId, schoolId, req);
        assertThat(resp.status()).isEqualTo("ACCEPTED");

        // 2 ParentStudent links saved (one per student)
        verify(parentLinkRepo, org.mockito.Mockito.times(2)).save(any(ParentStudent.class));
    }

    @Test
    void parentInvite_rejectsStudentFromOtherSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID otherSchool = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Student stu = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(otherSchool).status("ACTIVE").build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("mom@x.com")).thenReturn(false);
        when(invRepo.findByEmailIgnoreCaseAndStatus(anyString(), anyString())).thenReturn(List.of());
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(stu));

        var req = new CreateParentInvitationRequest(
                "mom@x.com", "Sarah", "TOKEN",
                "PARENT", List.of(studentId));

        assertThatThrownBy(() -> service.createParentInvitation(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("STUDENT_WRONG_SCHOOL"));
    }
}
