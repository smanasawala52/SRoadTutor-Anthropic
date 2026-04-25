package com.sroadtutor.whatsapp.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.phone.model.PhoneNumber;
import com.sroadtutor.phone.repository.PhoneNumberRepository;
import com.sroadtutor.phone.service.PhoneScopeChecker;
import com.sroadtutor.whatsapp.dto.ClickConfirmResponse;
import com.sroadtutor.whatsapp.dto.WaMeLinkRequest;
import com.sroadtutor.whatsapp.dto.WaMeLinkResponse;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link WaMeService}. Covers template rendering,
 * placeholder validation, free-text body, sender == clicker enforcement,
 * idempotent click-confirm, and the verifiedAt flip on first click.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaMeServiceTest {

    @Mock PhoneNumberRepository phoneRepo;
    @Mock WhatsappTemplateRepository templateRepo;
    @Mock WhatsappMessageLogRepository logRepo;
    @Mock PhoneScopeChecker scopeChecker;
    @Mock UserRepository userRepo;

    @InjectMocks WaMeService waMeService;

    // ============================================================
    // generateLink — happy paths
    // ============================================================

    @Test
    void generateLink_freeTextBuildsCanonicalWaMeUrl() {
        UUID senderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        PhoneNumber recipient = PhoneNumber.builder()
                .id(phoneId).userId(UUID.randomUUID())
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .whatsapp(true).whatsappOptIn(true)
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(recipient));
        when(userRepo.findById(senderId)).thenReturn(Optional.of(
                User.builder().id(senderId).schoolId(schoolId).build()));
        when(logRepo.save(any(WhatsappMessageLog.class))).thenAnswer(inv -> {
            WhatsappMessageLog l = inv.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            if (l.getLinkGeneratedAt() == null) l.setLinkGeneratedAt(Instant.now());
            return l;
        });

        var req = new WaMeLinkRequest(phoneId, null, null, "Hello world", "lesson:1");
        WaMeLinkResponse resp = waMeService.generateLink(Role.OWNER, senderId, req);

        // URL contains the digits-only number and url-encoded body.
        assertThat(resp.waMeUrl()).startsWith("https://wa.me/13065551234?text=");
        assertThat(resp.waMeUrl()).contains("Hello+world");
        assertThat(resp.renderedBody()).isEqualTo("Hello world");
        assertThat(resp.recipientPhoneId()).isEqualTo(phoneId);
        // Tenant context is captured from the sender.
        ArgumentCaptor<WhatsappMessageLog> captor = ArgumentCaptor.forClass(WhatsappMessageLog.class);
        verify(logRepo).save(captor.capture());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(schoolId);
        assertThat(captor.getValue().getSenderUserId()).isEqualTo(senderId);
        assertThat(captor.getValue().getCorrelationId()).isEqualTo("lesson:1");
        assertThat(captor.getValue().getTemplateId()).isNull();
    }

    @Test
    void generateLink_templateRendersAllPlaceholders() {
        UUID senderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PhoneNumber recipient = PhoneNumber.builder()
                .id(phoneId).studentId(UUID.randomUUID())
                .countryCode("44").nationalNumber("7700900123").e164("+447700900123")
                .whatsapp(true).whatsappOptIn(true)
                .build();
        WhatsappTemplate template = WhatsappTemplate.builder()
                .id(templateId).code("lesson_reminder")
                .body("Hi {{ name }}, your lesson at {{time}} is confirmed.")
                .active(true)
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(recipient));
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(template));
        when(userRepo.findById(senderId)).thenReturn(Optional.empty());
        when(logRepo.save(any(WhatsappMessageLog.class))).thenAnswer(inv -> {
            WhatsappMessageLog l = inv.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            if (l.getLinkGeneratedAt() == null) l.setLinkGeneratedAt(Instant.now());
            return l;
        });

        var req = new WaMeLinkRequest(
                phoneId, templateId,
                Map.of("name", "Ada", "time", "5pm"),
                null, null);
        WaMeLinkResponse resp = waMeService.generateLink(Role.OWNER, senderId, req);

        assertThat(resp.renderedBody()).isEqualTo("Hi Ada, your lesson at 5pm is confirmed.");
        assertThat(resp.waMeUrl()).startsWith("https://wa.me/447700900123?text=");
    }

    @Test
    void generateLink_templateWinsWhenBothProvided() {
        UUID senderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PhoneNumber recipient = PhoneNumber.builder()
                .id(phoneId).userId(UUID.randomUUID())
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .whatsapp(true).whatsappOptIn(true)
                .build();
        WhatsappTemplate template = WhatsappTemplate.builder()
                .id(templateId).code("greeting").body("Hello!").active(true).build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(recipient));
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(template));
        when(userRepo.findById(senderId)).thenReturn(Optional.empty());
        when(logRepo.save(any(WhatsappMessageLog.class))).thenAnswer(inv -> {
            WhatsappMessageLog l = inv.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            if (l.getLinkGeneratedAt() == null) l.setLinkGeneratedAt(Instant.now());
            return l;
        });

        var req = new WaMeLinkRequest(phoneId, templateId, null, "should be ignored", null);
        WaMeLinkResponse resp = waMeService.generateLink(Role.OWNER, senderId, req);

        assertThat(resp.renderedBody()).isEqualTo("Hello!");
    }

    // ============================================================
    // generateLink — error paths
    // ============================================================

    @Test
    void generateLink_throwsWhenRecipientMissing() {
        UUID senderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.empty());

        var req = new WaMeLinkRequest(phoneId, null, null, "hi", null);
        assertThatThrownBy(() -> waMeService.generateLink(Role.OWNER, senderId, req))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(logRepo, never()).save(any());
    }

    @Test
    void generateLink_throwsWhenWhatsappOptOut() {
        UUID senderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        PhoneNumber recipient = PhoneNumber.builder()
                .id(phoneId).userId(UUID.randomUUID())
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .whatsapp(true).whatsappOptIn(false) // opted out
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(recipient));

        var req = new WaMeLinkRequest(phoneId, null, null, "hi", null);
        assertThatThrownBy(() -> waMeService.generateLink(Role.OWNER, senderId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("WhatsApp");
    }

    @Test
    void generateLink_throwsWhenWhatsappFalse() {
        UUID senderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        PhoneNumber recipient = PhoneNumber.builder()
                .id(phoneId).userId(UUID.randomUUID())
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .whatsapp(false).whatsappOptIn(true)
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(recipient));

        var req = new WaMeLinkRequest(phoneId, null, null, "hi", null);
        assertThatThrownBy(() -> waMeService.generateLink(Role.OWNER, senderId, req))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void generateLink_throwsWhenTemplateInactive() {
        UUID senderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PhoneNumber recipient = PhoneNumber.builder()
                .id(phoneId).userId(UUID.randomUUID())
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .whatsapp(true).whatsappOptIn(true)
                .build();
        WhatsappTemplate template = WhatsappTemplate.builder()
                .id(templateId).code("legacy").body("x").active(false).build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(recipient));
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(template));

        var req = new WaMeLinkRequest(phoneId, templateId, null, null, null);
        assertThatThrownBy(() -> waMeService.generateLink(Role.OWNER, senderId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("TEMPLATE_INACTIVE"));
    }

    @Test
    void generateLink_throwsWhenNeitherTemplateNorBody() {
        UUID senderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        PhoneNumber recipient = PhoneNumber.builder()
                .id(phoneId).userId(UUID.randomUUID())
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .whatsapp(true).whatsappOptIn(true)
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(recipient));

        var req = new WaMeLinkRequest(phoneId, null, null, "  ", null);
        assertThatThrownBy(() -> waMeService.generateLink(Role.OWNER, senderId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("MISSING_BODY_OR_TEMPLATE"));
    }

    @Test
    void generateLink_throwsWhenPlaceholderMissing() {
        UUID senderId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PhoneNumber recipient = PhoneNumber.builder()
                .id(phoneId).userId(UUID.randomUUID())
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .whatsapp(true).whatsappOptIn(true)
                .build();
        WhatsappTemplate template = WhatsappTemplate.builder()
                .id(templateId).code("greeting").body("Hi {{name}}!").active(true).build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(recipient));
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(template));

        var req = new WaMeLinkRequest(phoneId, templateId, Map.of(), null, null);
        assertThatThrownBy(() -> waMeService.generateLink(Role.OWNER, senderId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("PLACEHOLDER_MISSING"));
        verify(logRepo, never()).save(any());
    }

    // ============================================================
    // confirmClick
    // ============================================================

    @Test
    void confirmClick_firstClickFlipsVerifiedAtAndStampsLog() {
        UUID senderId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        WhatsappMessageLog logRow = WhatsappMessageLog.builder()
                .id(logId).senderUserId(senderId).recipientPhoneId(phoneId)
                .renderedBody("hi").linkGeneratedAt(Instant.now())
                .build();
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(UUID.randomUUID()).verifiedAt(null)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();
        when(logRepo.findById(logId)).thenReturn(Optional.of(logRow));
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));
        when(logRepo.save(any(WhatsappMessageLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));

        ClickConfirmResponse resp = waMeService.confirmClick(senderId, logId);

        assertThat(resp.phoneVerifiedNow()).isTrue();
        assertThat(resp.phoneVerifiedAt()).isNotNull();
        assertThat(resp.clickedAt()).isNotNull();
        assertThat(phone.getVerifiedAt()).isNotNull();
        verify(phoneRepo, times(1)).save(phone);
    }

    @Test
    void confirmClick_secondClickPreservesOriginalClickedAtAndDoesNotReFlipVerifiedAt() {
        UUID senderId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        Instant firstClick = Instant.now().minusSeconds(60);
        Instant priorVerification = Instant.now().minusSeconds(120);
        WhatsappMessageLog logRow = WhatsappMessageLog.builder()
                .id(logId).senderUserId(senderId).recipientPhoneId(phoneId)
                .renderedBody("hi").linkGeneratedAt(Instant.now().minusSeconds(180))
                .linkClickedAt(firstClick)
                .build();
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(UUID.randomUUID()).verifiedAt(priorVerification)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();
        when(logRepo.findById(logId)).thenReturn(Optional.of(logRow));
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));

        ClickConfirmResponse resp = waMeService.confirmClick(senderId, logId);

        assertThat(resp.phoneVerifiedNow()).isFalse();
        assertThat(resp.clickedAt()).isEqualTo(firstClick);
        assertThat(resp.phoneVerifiedAt()).isEqualTo(priorVerification);
        // Idempotent — neither save fires.
        verify(logRepo, never()).save(any());
        verify(phoneRepo, never()).save(any(PhoneNumber.class));
    }

    @Test
    void confirmClick_rejectsCallerWhoIsNotTheSender() {
        UUID senderId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        WhatsappMessageLog logRow = WhatsappMessageLog.builder()
                .id(logId).senderUserId(senderId).recipientPhoneId(UUID.randomUUID())
                .renderedBody("hi").linkGeneratedAt(Instant.now())
                .build();
        when(logRepo.findById(logId)).thenReturn(Optional.of(logRow));

        assertThatThrownBy(() -> waMeService.confirmClick(otherUserId, logId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("link generator");
        verify(phoneRepo, never()).findById(any());
    }

    @Test
    void confirmClick_throwsWhenLogMissing() {
        UUID senderId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        when(logRepo.findById(logId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waMeService.confirmClick(senderId, logId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmClick_throwsWhenRecipientPhoneDisappeared() {
        UUID senderId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        WhatsappMessageLog logRow = WhatsappMessageLog.builder()
                .id(logId).senderUserId(senderId).recipientPhoneId(phoneId)
                .renderedBody("hi").linkGeneratedAt(Instant.now())
                .build();
        when(logRepo.findById(logId)).thenReturn(Optional.of(logRow));
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.empty());
        // First click → linkClickedAt is null → goes to save path before phone fetch.
        when(logRepo.save(any(WhatsappMessageLog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> waMeService.confirmClick(senderId, logId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("disappeared");
    }

    // ============================================================
    // URL & placeholder helpers (package-private)
    // ============================================================

    @Test
    void buildWaMeUrl_urlencodesSpecials() {
        String url = waMeService.buildWaMeUrl("13065551234", "Hi & welcome!");
        assertThat(url).startsWith("https://wa.me/13065551234?text=");
        // "&" must be encoded.
        assertThat(url).contains("%26");
        // "!" is left literal in URL-encoder default behaviour.
        assertThat(url).contains("welcome");
    }

    @Test
    void renderPlaceholders_handlesWhitespaceInsideBraces() {
        String body = "Hi {{ name }}, see you {{when}}";
        String rendered = waMeService.renderPlaceholders(
                body, Map.of("name", "Ada", "when", "tomorrow"));
        assertThat(rendered).isEqualTo("Hi Ada, see you tomorrow");
    }

    @Test
    void renderPlaceholders_protectsAgainstReplacementSpecials() {
        // A "$1" or "\\\\" in the value must not be interpreted as a regex backref.
        String body = "Code: {{code}}";
        String rendered = waMeService.renderPlaceholders(body, Map.of("code", "$1\\foo"));
        assertThat(rendered).isEqualTo("Code: $1\\foo");
    }
}
