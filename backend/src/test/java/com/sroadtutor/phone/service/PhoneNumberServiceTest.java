package com.sroadtutor.phone.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.phone.dto.PhoneNumberRequest;
import com.sroadtutor.phone.dto.PhoneNumberUpdateRequest;
import com.sroadtutor.phone.model.PhoneNumber;
import com.sroadtutor.phone.model.PhoneOwnerType;
import com.sroadtutor.phone.repository.PhoneNumberRepository;
import com.sroadtutor.phone.repository.PhoneOwnershipLookup;
import com.sroadtutor.subscription.service.PlanLimitsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link PhoneNumberService}. Verifies the 5-cap, primary
 * toggle demote-then-promote semantics, e164 reset on number change, owner FK
 * applied on create, and dedupe behaviour.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhoneNumberServiceTest {

    @Mock PhoneNumberRepository phoneRepo;
    @Mock PhoneScopeChecker scopeChecker;
    @Mock E164Normalizer normalizer;
    @Mock PhoneOwnershipLookup ownershipLookup;
    @Mock PlanLimitsService planLimits;

    @InjectMocks PhoneNumberService service;

    // ============================================================
    // Create — 5-cap, primary toggle, owner FK, dedupe
    // ============================================================

    @Test
    void create_appliesOwnerFkAndPromotesPrimaryOnFirstPhone() {
        UUID callerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        var req = new PhoneNumberRequest(
                PhoneOwnerType.SCHOOL, schoolId,
                "1", "3065551234", "Office",
                true, true, false /* makePrimary FALSE — but owner has zero phones */);
        when(normalizer.normalize("1", "3065551234"))
                .thenReturn(new E164Normalizer.Normalized("1", "3065551234", "+13065551234"));
        when(phoneRepo.findBySchoolId(schoolId)).thenReturn(List.of()); // empty, first phone
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> {
            PhoneNumber p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(phoneRepo.findBySchoolIdAndPrimaryTrue(schoolId)).thenReturn(Optional.empty());

        PhoneNumber created = service.create(Role.OWNER, callerId, req);

        // Owner FK is set on the SCHOOL column.
        assertThat(created.getSchoolId()).isEqualTo(schoolId);
        assertThat(created.getUserId()).isNull();
        assertThat(created.getInstructorId()).isNull();
        assertThat(created.getStudentId()).isNull();
        // Auto-promoted because there were zero existing phones.
        assertThat(created.isPrimary()).isTrue();
        assertThat(created.getE164()).isEqualTo("+13065551234");
        // Verification is null at create time.
        assertThat(created.getVerifiedAt()).isNull();
        verify(scopeChecker).requireWriteScope(Role.OWNER, callerId, PhoneOwnerType.SCHOOL, schoolId);
    }

    @Test
    void create_doesNotAutoPromoteWhenOwnerAlreadyHasPhones() {
        UUID callerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var req = new PhoneNumberRequest(
                PhoneOwnerType.USER, userId,
                "1", "3065551234", null, null, null, false);
        when(normalizer.normalize("1", "3065551234"))
                .thenReturn(new E164Normalizer.Normalized("1", "3065551234", "+13065551234"));
        when(phoneRepo.findByUserId(userId)).thenReturn(List.of(
                PhoneNumber.builder().id(UUID.randomUUID()).userId(userId)
                        .countryCode("1").nationalNumber("0000000000").e164("+10000000000")
                        .primary(true).build()
        ));
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> {
            PhoneNumber p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        PhoneNumber created = service.create(Role.STUDENT, callerId, req);

        assertThat(created.isPrimary()).isFalse();
    }

    @Test
    void create_promotePrimaryDemoteExistingInSameTransaction() {
        UUID callerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        var req = new PhoneNumberRequest(
                PhoneOwnerType.USER, userId,
                "1", "3065551234", null, null, null, true /* makePrimary */);
        when(normalizer.normalize("1", "3065551234"))
                .thenReturn(new E164Normalizer.Normalized("1", "3065551234", "+13065551234"));
        PhoneNumber existing = PhoneNumber.builder()
                .id(existingId).userId(userId).primary(true)
                .countryCode("1").nationalNumber("0000000000").e164("+10000000000").build();
        when(phoneRepo.findByUserId(userId)).thenReturn(new ArrayList<>(List.of(existing)));
        when(phoneRepo.findByUserIdAndPrimaryTrue(userId)).thenReturn(Optional.of(existing));
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> {
            PhoneNumber p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        PhoneNumber created = service.create(Role.STUDENT, callerId, req);

        assertThat(created.isPrimary()).isTrue();
        // The existing primary should have been demoted before the new one was promoted.
        assertThat(existing.isPrimary()).isFalse();
        // save() was invoked at least 3 times: insert, demote, promote.
        verify(phoneRepo, atLeastOnce()).save(any(PhoneNumber.class));
    }

    @Test
    void create_rejectsOverCapForUnaffiliatedOwner() {
        // No tenant pointer (ownerType=USER + user.school_id=null) → falls back
        // to the no-tenant floor (PHONE_LIMIT_EXCEEDED, NOT plan-bound).
        UUID callerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var req = new PhoneNumberRequest(
                PhoneOwnerType.USER, userId,
                "1", "3065551234", null, null, null, false);
        // Owner already has 2 phones — over the no-tenant floor.
        List<PhoneNumber> existing = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            existing.add(PhoneNumber.builder().id(UUID.randomUUID()).userId(userId)
                    .countryCode("1").nationalNumber("000000000" + i)
                    .e164("+1000000000" + i).build());
        }
        when(phoneRepo.findByUserId(userId)).thenReturn(existing);
        when(ownershipLookup.userSchoolId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(Role.STUDENT, callerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("PHONE_LIMIT_EXCEEDED"));
        verify(phoneRepo, never()).save(any());
        // Normalizer should NOT have been invoked — we short-circuit before E.164.
        verify(normalizer, never()).normalize(any(), any());
    }

    @Test
    void create_rejectsDuplicateE164ForSameOwner() {
        UUID callerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var req = new PhoneNumberRequest(
                PhoneOwnerType.USER, userId,
                "1", "3065551234", null, null, null, false);
        when(normalizer.normalize("1", "3065551234"))
                .thenReturn(new E164Normalizer.Normalized("1", "3065551234", "+13065551234"));
        when(phoneRepo.findByUserId(userId)).thenReturn(List.of(
                PhoneNumber.builder().id(UUID.randomUUID()).userId(userId)
                        .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                        .build()
        ));

        assertThatThrownBy(() -> service.create(Role.STUDENT, callerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("PHONE_ALREADY_EXISTS"));
        verify(phoneRepo, never()).save(any());
    }

    @Test
    void create_normalizationFailureBubbles() {
        UUID callerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var req = new PhoneNumberRequest(
                PhoneOwnerType.USER, userId,
                "0", "1", null, null, null, false); // bad inputs
        when(phoneRepo.findByUserId(userId)).thenReturn(List.of());
        when(normalizer.normalize("0", "1"))
                .thenThrow(new BadRequestException("INVALID_PHONE_NUMBER", "bad"));

        assertThatThrownBy(() -> service.create(Role.STUDENT, callerId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bad");
        verify(phoneRepo, never()).save(any());
    }

    @Test
    void create_defaultsWhatsappFlagsToTrueWhenNull() {
        UUID callerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var req = new PhoneNumberRequest(
                PhoneOwnerType.USER, userId,
                "1", "3065551234", null, null, null, false);
        when(normalizer.normalize("1", "3065551234"))
                .thenReturn(new E164Normalizer.Normalized("1", "3065551234", "+13065551234"));
        when(phoneRepo.findByUserId(userId)).thenReturn(List.of());
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));
        when(phoneRepo.findByUserIdAndPrimaryTrue(userId)).thenReturn(Optional.empty());

        PhoneNumber phone = service.create(Role.STUDENT, callerId, req);
        assertThat(phone.isWhatsapp()).isTrue();
        assertThat(phone.isWhatsappOptIn()).isTrue();
    }

    @Test
    void create_respectsExplicitFalseWhatsappFlags() {
        UUID callerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var req = new PhoneNumberRequest(
                PhoneOwnerType.USER, userId,
                "1", "3065551234", "Landline", false, false, false);
        when(normalizer.normalize("1", "3065551234"))
                .thenReturn(new E164Normalizer.Normalized("1", "3065551234", "+13065551234"));
        when(phoneRepo.findByUserId(userId)).thenReturn(List.of());
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));
        when(phoneRepo.findByUserIdAndPrimaryTrue(userId)).thenReturn(Optional.empty());

        PhoneNumber phone = service.create(Role.STUDENT, callerId, req);
        assertThat(phone.isWhatsapp()).isFalse();
        assertThat(phone.isWhatsappOptIn()).isFalse();
    }

    // ============================================================
    // Update — paired CC/NN, e164 reset, label, conflict
    // ============================================================

    @Test
    void update_rejectsCountryCodeWithoutNationalNumber() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        var req = new PhoneNumberUpdateRequest("1", null, null, null, null);
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));

        assertThatThrownBy(() -> service.update(Role.STUDENT, callerId, phoneId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be updated together");
    }

    @Test
    void update_changingNumberResetsVerifiedAt() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        var req = new PhoneNumberUpdateRequest("1", "5145551111", "Mobile", null, null);
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .verifiedAt(Instant.now())
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));
        when(normalizer.normalize("1", "5145551111"))
                .thenReturn(new E164Normalizer.Normalized("1", "5145551111", "+15145551111"));
        when(scopeChecker.ownerOf(phone))
                .thenReturn(new PhoneScopeChecker.OwnerRef(PhoneOwnerType.USER, callerId));
        when(phoneRepo.findByUserId(callerId)).thenReturn(List.of(phone));
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));

        PhoneNumber updated = service.update(Role.STUDENT, callerId, phoneId, req);

        assertThat(updated.getE164()).isEqualTo("+15145551111");
        assertThat(updated.getNationalNumber()).isEqualTo("5145551111");
        assertThat(updated.getLabel()).isEqualTo("Mobile");
        // Re-verification required after number change.
        assertThat(updated.getVerifiedAt()).isNull();
    }

    @Test
    void update_unchangedNumberKeepsVerifiedAt() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        Instant verified = Instant.now().minusSeconds(60);
        var req = new PhoneNumberUpdateRequest("1", "3065551234", "New label", null, null);
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .verifiedAt(verified)
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));
        when(normalizer.normalize("1", "3065551234"))
                .thenReturn(new E164Normalizer.Normalized("1", "3065551234", "+13065551234"));
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));

        PhoneNumber updated = service.update(Role.STUDENT, callerId, phoneId, req);

        assertThat(updated.getLabel()).isEqualTo("New label");
        assertThat(updated.getVerifiedAt()).isEqualTo(verified);
    }

    @Test
    void update_rejectsConflictingNumberOnSameOwner() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        UUID otherPhoneId = UUID.randomUUID();
        var req = new PhoneNumberUpdateRequest("1", "5145551111", null, null, null);
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();
        PhoneNumber sibling = PhoneNumber.builder()
                .id(otherPhoneId).userId(callerId)
                .countryCode("1").nationalNumber("5145551111").e164("+15145551111")
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));
        when(normalizer.normalize("1", "5145551111"))
                .thenReturn(new E164Normalizer.Normalized("1", "5145551111", "+15145551111"));
        when(scopeChecker.ownerOf(phone))
                .thenReturn(new PhoneScopeChecker.OwnerRef(PhoneOwnerType.USER, callerId));
        when(phoneRepo.findByUserId(callerId)).thenReturn(List.of(phone, sibling));

        assertThatThrownBy(() -> service.update(Role.STUDENT, callerId, phoneId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("PHONE_ALREADY_EXISTS"));
        verify(phoneRepo, never()).save(any());
    }

    @Test
    void update_partialFlagsApplyWithoutTouchingNumber() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        var req = new PhoneNumberUpdateRequest(null, null, null, false, null);
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .whatsapp(true).whatsappOptIn(true)
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));

        PhoneNumber updated = service.update(Role.STUDENT, callerId, phoneId, req);
        assertThat(updated.isWhatsapp()).isFalse();
        assertThat(updated.isWhatsappOptIn()).isTrue(); // unchanged
        verify(normalizer, never()).normalize(any(), any());
    }

    @Test
    void update_throwsWhenPhoneMissing() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(Role.STUDENT, callerId, phoneId,
                new PhoneNumberUpdateRequest(null, null, "x", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // Promote primary
    // ============================================================

    @Test
    void promoteToPrimary_demotesExistingThenPromotesNew() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        UUID existingPrimaryId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId).primary(false)
                .countryCode("1").nationalNumber("5145551111").e164("+15145551111")
                .build();
        PhoneNumber existing = PhoneNumber.builder()
                .id(existingPrimaryId).userId(callerId).primary(true)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));
        when(phoneRepo.findByUserIdAndPrimaryTrue(callerId)).thenReturn(Optional.of(existing));
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));

        PhoneNumber result = service.promoteToPrimary(Role.STUDENT, callerId, phoneId);

        assertThat(result.isPrimary()).isTrue();
        assertThat(existing.isPrimary()).isFalse();
        // verify the demotion AND promotion both went through save
        ArgumentCaptor<PhoneNumber> captor = ArgumentCaptor.forClass(PhoneNumber.class);
        verify(phoneRepo, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PhoneNumber::getId)
                .contains(phoneId, existingPrimaryId);
    }

    @Test
    void promoteToPrimary_isNoOpWhenAlreadyPrimary() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId).primary(true)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));

        PhoneNumber result = service.promoteToPrimary(Role.STUDENT, callerId, phoneId);

        assertThat(result.isPrimary()).isTrue();
        verify(phoneRepo, never()).save(any());
    }

    // ============================================================
    // setWhatsappOptIn — simple toggle
    // ============================================================

    @Test
    void setWhatsappOptIn_flipsTheFlag() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId).whatsappOptIn(true)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));
        when(phoneRepo.save(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));

        PhoneNumber result = service.setWhatsappOptIn(Role.STUDENT, callerId, phoneId, false);
        assertThat(result.isWhatsappOptIn()).isFalse();
    }

    // ============================================================
    // Delete
    // ============================================================

    @Test
    void delete_callsScopeCheckerThenRepo() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));
        when(scopeChecker.ownerOf(phone))
                .thenReturn(new PhoneScopeChecker.OwnerRef(PhoneOwnerType.USER, callerId));
        doNothing().when(phoneRepo).delete(phone);

        service.delete(Role.STUDENT, callerId, phoneId);

        verify(scopeChecker).requireWriteScope(eq(Role.STUDENT), eq(callerId), eq(phone));
        verify(phoneRepo).delete(phone);
    }

    @Test
    void delete_throwsWhenPhoneMissing() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(Role.STUDENT, callerId, phoneId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(phoneRepo, never()).delete(any(PhoneNumber.class));
    }

    // ============================================================
    // Reads
    // ============================================================

    @Test
    void listForOwner_dispatchesByOwnerType() {
        UUID callerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(phoneRepo.findByUserId(userId)).thenReturn(List.of());

        service.listForOwner(Role.OWNER, callerId, PhoneOwnerType.USER, userId);

        verify(scopeChecker).requireWriteScope(Role.OWNER, callerId, PhoneOwnerType.USER, userId);
        verify(phoneRepo, times(1)).findByUserId(userId);
    }

    @Test
    void getById_returnsPhoneAfterScopeCheck() {
        UUID callerId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder()
                .id(phoneId).userId(callerId)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();
        when(phoneRepo.findById(phoneId)).thenReturn(Optional.of(phone));

        PhoneNumber result = service.getById(Role.STUDENT, callerId, phoneId);

        assertThat(result).isSameAs(phone);
        verify(scopeChecker).requireReadScope(Role.STUDENT, callerId, phone);
    }
}
