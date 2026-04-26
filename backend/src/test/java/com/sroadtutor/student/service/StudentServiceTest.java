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
class StudentServiceTest {

    @Mock StudentRepository       studentRepo;
    @Mock ParentStudentRepository parentLinkRepo;
    @Mock InstructorRepository    instructorRepo;
    @Mock SchoolRepository        schoolRepo;
    @Mock UserRepository          userRepo;
    @Mock PasswordEncoder         passwordEncoder;

    @InjectMocks StudentService service;

    // ---------------- addByOwner ----------------

    @Test
    void addByOwner_createsUserAndStudentRow() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("kid@x.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
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
        when(parentLinkRepo.findByStudentId(any())).thenReturn(List.of());

        var req = new AddStudentRequest(
                "kid@x.com", "Tom Kid", null, null, 10, 10, null,
                null, null, null);

        StudentResponse resp = service.addByOwner(Role.OWNER, ownerId, schoolId, req);

        assertThat(resp.schoolId()).isEqualTo(schoolId);
        assertThat(resp.packageTotalLessons()).isEqualTo(10);
        assertThat(resp.lessonsRemaining()).isEqualTo(10);
        assertThat(resp.status()).isEqualTo("ACTIVE");
        assertThat(resp.parents()).isEmpty();

        // verify user was created with dummy password + must_change_password=true
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCap.capture());
        User createdUser = userCap.getValue();
        assertThat(createdUser.getEmail()).isEqualTo("kid@x.com");
        assertThat(createdUser.getRole()).isEqualTo(Role.STUDENT);
        assertThat(createdUser.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(createdUser.getSchoolId()).isEqualTo(schoolId);
        assertThat(createdUser.isMustChangePassword()).isTrue();
        assertThat(createdUser.getPasswordHash()).isEqualTo("HASHED");
    }

    @Test
    void addByOwner_lowercasesEmail() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("kid@x.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
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

        var req = new AddStudentRequest(
                "  KID@X.COM  ", "Tom", null, null, 0, 0, null,
                null, null, null);

        service.addByOwner(Role.OWNER, ownerId, schoolId, req);

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCap.capture());
        assertThat(userCap.getValue().getEmail()).isEqualTo("kid@x.com");
    }

    @Test
    void addByOwner_rejectsExistingEmail() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("kid@x.com")).thenReturn(true);

        var req = new AddStudentRequest(
                "kid@x.com", "Tom", null, null, 0, 0, null,
                null, null, null);

        assertThatThrownBy(() -> service.addByOwner(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("STUDENT_EMAIL_ALREADY_EXISTS"));

        verify(studentRepo, never()).save(any());
    }

    @Test
    void addByOwner_rejectsRemainingGreaterThanTotal() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("kid@x.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });

        var req = new AddStudentRequest(
                "kid@x.com", "Tom", null, null, 10, 11, null,
                null, null, null);

        assertThatThrownBy(() -> service.addByOwner(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("REMAINING_GT_TOTAL"));
    }

    @Test
    void addByOwner_rejectsInactiveSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(false).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        var req = new AddStudentRequest(
                "kid@x.com", "Tom", null, null, 0, 0, null,
                null, null, null);

        assertThatThrownBy(() -> service.addByOwner(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SCHOOL_INACTIVE"));
    }

    @Test
    void addByOwner_rejectsNonOwnerNonInstructor() {
        UUID userId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(UUID.randomUUID()).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        var req = new AddStudentRequest(
                "kid@x.com", "Tom", null, null, 0, 0, null,
                null, null, null);

        assertThatThrownBy(() -> service.addByOwner(Role.STUDENT, userId, schoolId, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void addByOwner_rejectsInstructorWrongSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID otherSchool = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Instructor i = Instructor.builder().id(instructorId).userId(UUID.randomUUID())
                .schoolId(otherSchool).active(true).build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(i));

        var req = new AddStudentRequest(
                "kid@x.com", "Tom", null, instructorId, 0, 0, null,
                null, null, null);

        assertThatThrownBy(() -> service.addByOwner(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INSTRUCTOR_WRONG_SCHOOL"));
    }

    @Test
    void addByOwner_createsParentUserWhenEmailNotFound() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("kid@x.com")).thenReturn(false);
        when(userRepo.findByEmailIgnoreCase("mom@x.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
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
        when(parentLinkRepo.save(any(ParentStudent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(parentLinkRepo.findByStudentId(any())).thenReturn(List.of());

        var req = new AddStudentRequest(
                "kid@x.com", "Tom Kid", null, null, 10, 10, null,
                "MOM@x.com", "Sarah Kid", "GUARDIAN");

        StudentResponse resp = service.addByOwner(Role.OWNER, ownerId, schoolId, req);

        // Two saves on userRepo: student + parent
        verify(userRepo, org.mockito.Mockito.times(2)).save(any(User.class));
        // Parent link persisted
        ArgumentCaptor<ParentStudent> linkCap = ArgumentCaptor.forClass(ParentStudent.class);
        verify(parentLinkRepo).save(linkCap.capture());
        assertThat(linkCap.getValue().getRelationship()).isEqualTo("GUARDIAN");
    }

    @Test
    void addByOwner_linksExistingParentUser() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID existingParentId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        User existingParent = User.builder()
                .id(existingParentId).email("mom@x.com").role(Role.PARENT)
                .authProvider(AuthProvider.LOCAL).active(true).build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("kid@x.com")).thenReturn(false);
        when(userRepo.findByEmailIgnoreCase("mom@x.com")).thenReturn(Optional.of(existingParent));
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
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
        when(parentLinkRepo.save(any(ParentStudent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(parentLinkRepo.findByStudentId(any())).thenReturn(List.of());

        var req = new AddStudentRequest(
                "kid@x.com", "Tom", null, null, 0, 0, null,
                "mom@x.com", null, null);

        service.addByOwner(Role.OWNER, ownerId, schoolId, req);

        // Only the student User is saved; existing parent is NOT re-saved
        verify(userRepo, org.mockito.Mockito.times(1)).save(any(User.class));
        ArgumentCaptor<ParentStudent> linkCap = ArgumentCaptor.forClass(ParentStudent.class);
        verify(parentLinkRepo).save(linkCap.capture());
        assertThat(linkCap.getValue().getParentUserId()).isEqualTo(existingParentId);
        assertThat(linkCap.getValue().getRelationship()).isEqualTo("PARENT");
    }

    @Test
    void addByOwner_rejectsParentEmailWithWrongRole() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        User otherRoleUser = User.builder()
                .id(UUID.randomUUID()).email("mom@x.com").role(Role.OWNER)
                .authProvider(AuthProvider.LOCAL).active(true).build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.existsByEmailIgnoreCase("kid@x.com")).thenReturn(false);
        when(userRepo.findByEmailIgnoreCase("mom@x.com")).thenReturn(Optional.of(otherRoleUser));
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
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

        var req = new AddStudentRequest(
                "kid@x.com", "Tom", null, null, 0, 0, null,
                "mom@x.com", "Sarah", null);

        assertThatThrownBy(() -> service.addByOwner(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("PARENT_EMAIL_BELONGS_TO_OTHER_ROLE"));
    }

    // ---------------- update ----------------

    @Test
    void update_appliesPartialFields() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Student st = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).packageTotalLessons(10).lessonsRemaining(10)
                .status("ACTIVE").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(studentRepo.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(parentLinkRepo.findByStudentId(any())).thenReturn(List.of());

        var req = new StudentUpdateRequest(null, null, 5, "PASSED", null);

        StudentResponse resp = service.update(Role.OWNER, ownerId, studentId, req);
        assertThat(resp.lessonsRemaining()).isEqualTo(5);
        assertThat(resp.status()).isEqualTo("PASSED");
    }

    @Test
    void update_rejectsRemainingGreaterThanTotal() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Student st = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).packageTotalLessons(10).lessonsRemaining(10)
                .status("ACTIVE").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        var req = new StudentUpdateRequest(null, null, 12, null, null);

        assertThatThrownBy(() -> service.update(Role.OWNER, ownerId, studentId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("REMAINING_GT_TOTAL"));
    }

    @Test
    void update_403ForUnrelatedOwner() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(UUID.randomUUID()).active(true).build();
        Student st = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).packageTotalLessons(0).lessonsRemaining(0)
                .status("ACTIVE").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        var req = new StudentUpdateRequest(null, null, null, "PASSED", null);

        assertThatThrownBy(() -> service.update(Role.OWNER, UUID.randomUUID(), studentId, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------- read scope ----------------

    @Test
    void getById_succeedsForLinkedParent() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        Student st = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(true);
        when(parentLinkRepo.findByStudentId(studentId)).thenReturn(List.of());

        StudentResponse r = service.getById(Role.PARENT, parentId, studentId);
        assertThat(r.id()).isEqualTo(studentId);
    }

    @Test
    void getById_403ForUnlinkedParent() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student st = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID()).status("ACTIVE").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> service.getById(Role.PARENT, parentId, studentId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getById_succeedsForStudentOnSelf() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student st = Student.builder().id(studentId).userId(userId)
                .schoolId(UUID.randomUUID()).status("ACTIVE").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(parentLinkRepo.findByStudentId(studentId)).thenReturn(List.of());

        StudentResponse r = service.getById(Role.STUDENT, userId, studentId);
        assertThat(r.id()).isEqualTo(studentId);
    }

    @Test
    void getById_403ForStudentOnOtherStudent() {
        UUID userId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student st = Student.builder().id(studentId).userId(otherUser)
                .schoolId(UUID.randomUUID()).status("ACTIVE").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));

        assertThatThrownBy(() -> service.getById(Role.STUDENT, userId, studentId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------- linkParent / unlinkParent ----------------

    @Test
    void linkParent_findsExistingParent() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID existingParentId = UUID.randomUUID();
        Student st = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        User existingParent = User.builder().id(existingParentId).email("mom@x.com")
                .role(Role.PARENT).authProvider(AuthProvider.LOCAL).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.findByEmailIgnoreCase("mom@x.com")).thenReturn(Optional.of(existingParent));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(existingParentId, studentId)).thenReturn(false);
        when(parentLinkRepo.save(any(ParentStudent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(parentLinkRepo.findByStudentId(studentId)).thenReturn(List.of());

        var req = new LinkParentRequest("mom@x.com", null, "GUARDIAN");
        service.linkParent(Role.OWNER, ownerId, studentId, req);

        verify(userRepo, never()).save(any(User.class)); // didn't create a new user
        ArgumentCaptor<ParentStudent> cap = ArgumentCaptor.forClass(ParentStudent.class);
        verify(parentLinkRepo).save(cap.capture());
        assertThat(cap.getValue().getRelationship()).isEqualTo("GUARDIAN");
    }

    @Test
    void linkParent_rejectsAlreadyLinkedPair() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID existingParentId = UUID.randomUUID();
        Student st = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        User existingParent = User.builder().id(existingParentId).email("mom@x.com")
                .role(Role.PARENT).authProvider(AuthProvider.LOCAL).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(userRepo.findByEmailIgnoreCase("mom@x.com")).thenReturn(Optional.of(existingParent));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(existingParentId, studentId)).thenReturn(true);

        var req = new LinkParentRequest("mom@x.com", null, null);
        assertThatThrownBy(() -> service.linkParent(Role.OWNER, ownerId, studentId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("PARENT_ALREADY_LINKED"));
    }

    @Test
    void unlinkParent_deletesExistingLink() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Student st = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        ParentStudent link = ParentStudent.builder()
                .id(UUID.randomUUID()).parentUserId(parentId).studentId(studentId)
                .relationship("PARENT").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(parentLinkRepo.findByParentUserIdAndStudentId(parentId, studentId))
                .thenReturn(Optional.of(link));

        service.unlinkParent(Role.OWNER, ownerId, studentId, parentId);

        verify(parentLinkRepo).delete(link);
    }

    @Test
    void unlinkParent_404IfNotLinked() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student st = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(st));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(parentLinkRepo.findByParentUserIdAndStudentId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlinkParent(Role.OWNER, ownerId, studentId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
