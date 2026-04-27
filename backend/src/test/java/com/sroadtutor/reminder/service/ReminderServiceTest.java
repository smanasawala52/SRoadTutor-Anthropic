package com.sroadtutor.reminder.service;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.phone.model.PhoneNumber;
import com.sroadtutor.phone.repository.PhoneNumberRepository;
import com.sroadtutor.reminder.dto.ReminderResponse;
import com.sroadtutor.reminder.model.Reminder;
import com.sroadtutor.reminder.repository.ReminderRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.session.model.LessonSession;
import com.sroadtutor.session.repository.LessonSessionRepository;
import com.sroadtutor.student.model.ParentStudent;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.ParentStudentRepository;
import com.sroadtutor.student.repository.StudentRepository;
import com.sroadtutor.whatsapp.model.WhatsappMessageLog;
import com.sroadtutor.whatsapp.model.WhatsappTemplate;
import com.sroadtutor.whatsapp.repository.WhatsappMessageLogRepository;
import com.sroadtutor.whatsapp.repository.WhatsappTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReminderServiceTest {

    @Mock ReminderRepository reminderRepo;
    @Mock LessonSessionRepository sessionRepo;
    @Mock StudentRepository studentRepo;
    @Mock ParentStudentRepository parentLinkRepo;
    @Mock InstructorRepository instructorRepo;
    @Mock SchoolRepository schoolRepo;
    @Mock UserRepository userRepo;
    @Mock PhoneNumberRepository phoneRepo;
    @Mock WhatsappTemplateRepository templateRepo;
    @Mock WhatsappMessageLogRepository logRepo;

    @InjectMocks ReminderService service;

    // ============================================================
    // Generate
    // ============================================================

    @Test
    void generate_createsPendingForStudentAndParents() {
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        UUID parentUserId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        School school = School.builder().id(schoolId).active(true).timezone("America/Regina").build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED")
                .instructorId(instructorId).studentId(studentId)
                .scheduledAt(Instant.now().plusSeconds(86400))
                .durationMins(60).location("Walmart parking lot").build();
        Student student = Student.builder().id(studentId).userId(studentUserId).schoolId(schoolId)
                .status("ACTIVE").build();

        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(reminderRepo.findBySessionIdAndStatusIn(any(), any())).thenReturn(List.of());
        when(reminderRepo.findBySessionIdAndRecipientUserIdAndReminderKindAndStatusIn(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(parentLinkRepo.findByStudentId(studentId)).thenReturn(List.of(
                ParentStudent.builder().id(UUID.randomUUID())
                        .parentUserId(parentUserId).studentId(studentId).build()));

        // Both recipients have a primary WhatsApp phone
        PhoneNumber studentPhone = PhoneNumber.builder()
                .id(UUID.randomUUID()).userId(studentUserId)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .primary(true).whatsapp(true).whatsappOptIn(true).build();
        PhoneNumber parentPhone = PhoneNumber.builder()
                .id(UUID.randomUUID()).userId(parentUserId)
                .countryCode("1").nationalNumber("3065551235").e164("+13065551235")
                .primary(true).whatsapp(true).whatsappOptIn(true).build();
        when(phoneRepo.findByUserIdAndPrimaryTrue(studentUserId)).thenReturn(Optional.of(studentPhone));
        when(phoneRepo.findByUserIdAndPrimaryTrue(parentUserId)).thenReturn(Optional.of(parentPhone));

        // Names + instructor lookup
        when(userRepo.findById(studentUserId)).thenReturn(Optional.of(
                User.builder().id(studentUserId).fullName("Tom Kid")
                        .role(Role.STUDENT).authProvider(AuthProvider.LOCAL).build()));
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(
                Instructor.builder().id(instructorId).userId(instructorUserId).build()));
        when(userRepo.findById(instructorUserId)).thenReturn(Optional.of(
                User.builder().id(instructorUserId).fullName("Jane Inst")
                        .role(Role.INSTRUCTOR).authProvider(AuthProvider.LOCAL).build()));

        // Template
        when(templateRepo.findActiveSchoolOverride(schoolId, "lesson_reminder", "en"))
                .thenReturn(Optional.empty());
        when(templateRepo.findActivePlatformDefault("lesson_reminder", "en")).thenReturn(Optional.of(
                WhatsappTemplate.builder()
                        .id(UUID.randomUUID()).code("lesson_reminder")
                        .body("Hi {{studentName}}, lesson {{lessonTimeLocal}} ({{lessonDuration}}m){{locationSuffix}}.")
                        .active(true).build()));

        when(reminderRepo.save(any(Reminder.class))).thenAnswer(inv -> {
            Reminder r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        int count = service.generatePendingForSession(sessionId);
        assertThat(count).isEqualTo(2);

        ArgumentCaptor<Reminder> cap = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepo, times(2)).save(cap.capture());
        for (Reminder r : cap.getAllValues()) {
            assertThat(r.getStatus()).isEqualTo("PENDING");
            assertThat(r.getReminderKind()).isEqualTo("LESSON_24H");
            assertThat(r.getChannel()).isEqualTo("WHATSAPP");
            assertThat(r.getPayloadJson()).contains("https://wa.me/1");
            assertThat(r.getPayloadJson()).contains("Tom Kid");
            assertThat(r.getPayloadJson()).contains("Walmart");
        }
    }

    @Test
    void generate_skipsIfActiveReminderAlreadyExists() {
        UUID sessionId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED")
                .instructorId(UUID.randomUUID()).studentId(UUID.randomUUID())
                .scheduledAt(Instant.now().plusSeconds(86400)).durationMins(60).build()));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(
                School.builder().id(schoolId).active(true).timezone("America/Regina").build()));
        when(reminderRepo.findBySessionIdAndStatusIn(any(), any())).thenReturn(List.of(
                Reminder.builder().id(UUID.randomUUID()).status("PENDING").build()));

        int count = service.generatePendingForSession(sessionId);
        assertThat(count).isZero();
        verify(reminderRepo, never()).save(any());
    }

    @Test
    void generate_marksFailedWhenRecipientHasNoWhatsAppPhone() {
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();

        School school = School.builder().id(schoolId).active(true).timezone("America/Regina").build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED")
                .instructorId(instructorId).studentId(studentId)
                .scheduledAt(Instant.now().plusSeconds(86400)).durationMins(60).build();
        Student student = Student.builder().id(studentId).userId(studentUserId).schoolId(schoolId)
                .status("ACTIVE").build();

        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(reminderRepo.findBySessionIdAndStatusIn(any(), any())).thenReturn(List.of());
        when(reminderRepo.findBySessionIdAndRecipientUserIdAndReminderKindAndStatusIn(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(parentLinkRepo.findByStudentId(studentId)).thenReturn(List.of());

        // No primary phone
        when(phoneRepo.findByUserIdAndPrimaryTrue(studentUserId)).thenReturn(Optional.empty());

        when(reminderRepo.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.generatePendingForSession(sessionId);

        ArgumentCaptor<Reminder> cap = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(cap.getValue().getFailedReason()).contains("primary phone");
    }

    @Test
    void generate_marksFailedWhenWhatsAppOptOut() {
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();

        School school = School.builder().id(schoolId).active(true).timezone("America/Regina").build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED")
                .instructorId(UUID.randomUUID()).studentId(studentId)
                .scheduledAt(Instant.now().plusSeconds(86400)).durationMins(60).build();
        Student student = Student.builder().id(studentId).userId(studentUserId).schoolId(schoolId)
                .status("ACTIVE").build();
        PhoneNumber phone = PhoneNumber.builder()
                .id(UUID.randomUUID()).userId(studentUserId)
                .primary(true).whatsapp(true).whatsappOptIn(false)  // opted out
                .build();

        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(reminderRepo.findBySessionIdAndStatusIn(any(), any())).thenReturn(List.of());
        when(reminderRepo.findBySessionIdAndRecipientUserIdAndReminderKindAndStatusIn(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(parentLinkRepo.findByStudentId(studentId)).thenReturn(List.of());
        when(phoneRepo.findByUserIdAndPrimaryTrue(studentUserId)).thenReturn(Optional.of(phone));

        when(reminderRepo.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.generatePendingForSession(sessionId);

        ArgumentCaptor<Reminder> cap = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("FAILED");
    }

    // ============================================================
    // Cancel cascade
    // ============================================================

    @Test
    void cancelForSession_flipsPendingToCancelled() {
        UUID sessionId = UUID.randomUUID();
        Reminder pending = Reminder.builder().id(UUID.randomUUID()).sessionId(sessionId).status("PENDING").build();
        Reminder sent = Reminder.builder().id(UUID.randomUUID()).sessionId(sessionId).status("SENT").build();
        Reminder failed = Reminder.builder().id(UUID.randomUUID()).sessionId(sessionId).status("FAILED").build();
        when(reminderRepo.findBySessionId(sessionId)).thenReturn(List.of(pending, sent, failed));
        when(reminderRepo.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        int n = service.cancelForSession(sessionId);
        assertThat(n).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo("CANCELLED");
        assertThat(sent.getStatus()).isEqualTo("SENT");      // unchanged
        assertThat(failed.getStatus()).isEqualTo("FAILED");  // unchanged
    }

    // ============================================================
    // Fire
    // ============================================================

    @Test
    void fire_createsAuditLogAndMarksSent() {
        UUID schoolId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();

        Reminder reminder = Reminder.builder()
                .id(reminderId).sessionId(sessionId).recipientUserId(UUID.randomUUID())
                .channel("WHATSAPP").reminderKind("LESSON_24H").status("PENDING")
                .scheduledFor(Instant.now())
                .payloadJson("{\"waMeUrl\":\"https://wa.me/13065551234?text=Hi\","
                        + "\"renderedBody\":\"Hi Tom\","
                        + "\"recipientPhoneId\":\"" + phoneId + "\"}")
                .build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED")
                .instructorId(UUID.randomUUID()).studentId(UUID.randomUUID())
                .scheduledAt(Instant.now().plusSeconds(3600)).durationMins(60).build();
        School school = School.builder().id(schoolId).ownerId(ownerId).active(true).build();

        when(reminderRepo.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(logRepo.save(any(WhatsappMessageLog.class))).thenAnswer(inv -> {
            WhatsappMessageLog l = inv.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            return l;
        });
        when(reminderRepo.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        ReminderResponse resp = service.fire(Role.OWNER, ownerId, reminderId);
        assertThat(resp.status()).isEqualTo("SENT");
        assertThat(reminder.getSentAt()).isNotNull();
        assertThat(reminder.getWaMeLogId()).isNotNull();

        ArgumentCaptor<WhatsappMessageLog> logCap = ArgumentCaptor.forClass(WhatsappMessageLog.class);
        verify(logRepo).save(logCap.capture());
        assertThat(logCap.getValue().getCorrelationId()).isEqualTo("reminder:" + reminderId);
        assertThat(logCap.getValue().getRenderedBody()).isEqualTo("Hi Tom");
        assertThat(logCap.getValue().getRecipientPhoneId()).isEqualTo(phoneId);
    }

    @Test
    void fire_isIdempotentOnAlreadySent() {
        UUID schoolId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        Reminder reminder = Reminder.builder()
                .id(reminderId).sessionId(UUID.randomUUID()).recipientUserId(UUID.randomUUID())
                .status("SENT").sentAt(Instant.now()).build();

        when(reminderRepo.findById(reminderId)).thenReturn(Optional.of(reminder));

        ReminderResponse resp = service.fire(Role.OWNER, ownerId, reminderId);
        assertThat(resp.status()).isEqualTo("SENT");
        verify(reminderRepo, never()).save(any());
        verify(logRepo, never()).save(any());
    }

    @Test
    void fire_rejectsCancelledReminder() {
        UUID reminderId = UUID.randomUUID();
        Reminder reminder = Reminder.builder()
                .id(reminderId).sessionId(UUID.randomUUID()).recipientUserId(UUID.randomUUID())
                .status("CANCELLED").build();
        when(reminderRepo.findById(reminderId)).thenReturn(Optional.of(reminder));

        assertThatThrownBy(() -> service.fire(Role.OWNER, UUID.randomUUID(), reminderId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("REMINDER_NOT_FIREABLE"));
    }

    @Test
    void fire_403ForUnrelatedOwner() {
        UUID schoolId = UUID.randomUUID();
        UUID realOwnerId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();

        Reminder reminder = Reminder.builder()
                .id(reminderId).sessionId(sessionId).recipientUserId(UUID.randomUUID())
                .status("PENDING")
                .payloadJson("{\"waMeUrl\":\"x\",\"renderedBody\":\"x\","
                        + "\"recipientPhoneId\":\"" + UUID.randomUUID() + "\"}")
                .build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED").build();
        School school = School.builder().id(schoolId).ownerId(realOwnerId).build();

        when(reminderRepo.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.fire(Role.OWNER, outsiderId, reminderId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void fire_rejectsMalformedPayload() {
        UUID schoolId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();

        Reminder reminder = Reminder.builder()
                .id(reminderId).sessionId(sessionId).recipientUserId(UUID.randomUUID())
                .status("PENDING").payloadJson("{}").build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(reminderRepo.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.fire(Role.OWNER, ownerId, reminderId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("REMINDER_PAYLOAD_INVALID"));
    }

    // ============================================================
    // listPendingForCurrentUser
    // ============================================================

    @Test
    void listPending_returnsRecipientRows() {
        UUID userId = UUID.randomUUID();
        Reminder r = Reminder.builder().id(UUID.randomUUID()).recipientUserId(userId)
                .status("PENDING").sessionId(UUID.randomUUID())
                .payloadJson("{\"waMeUrl\":\"x\",\"renderedBody\":\"y\"}").build();
        when(reminderRepo.findPendingForRecipient(any(), any())).thenReturn(List.of(r));

        List<ReminderResponse> list = service.listPendingForCurrentUser(userId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).waMeUrl()).isEqualTo("x");
    }
}
