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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The single entry point for phone-number CRUD. Every public method runs the
 * caller through {@link PhoneScopeChecker} first; the service trusts no input
 * once it gets here.
 *
 * <p>Locked at PR4 kickoff:
 * <ul>
 *   <li><b>5-phone cap per owner</b> — service-layer count check; over-cap
 *       writes throw {@code PHONE_LIMIT_EXCEEDED} (400).</li>
 *   <li><b>Primary toggle is exclusive</b> — promoting one phone demotes any
 *       existing primary on the same owner in the same transaction so the
 *       partial unique index is never violated.</li>
 *   <li><b>Verification</b> — phones are inserted with {@code verifiedAt = null};
 *       {@link com.sroadtutor.whatsapp.service.WaMeService#confirmClick} flips
 *       it on first click-confirm.</li>
 * </ul>
 */
@Service
public class PhoneNumberService {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberService.class);

    /**
     * Floor cap, used as a fallback when the owner can't be resolved to a
     * tenant (e.g. a USER with {@code school_id = null}). PR12 introduced
     * plan-tier-aware limits via {@link PlanLimitsService} — they win when
     * the owner resolves to a school.
     */
    static final int MAX_PHONES_PER_OWNER_NO_TENANT = 2;

    private final PhoneNumberRepository phoneRepo;
    private final PhoneScopeChecker scopeChecker;
    private final E164Normalizer normalizer;
    private final PhoneOwnershipLookup ownershipLookup;
    private final PlanLimitsService planLimits;

    public PhoneNumberService(PhoneNumberRepository phoneRepo,
                              PhoneScopeChecker scopeChecker,
                              E164Normalizer normalizer,
                              PhoneOwnershipLookup ownershipLookup,
                              PlanLimitsService planLimits) {
        this.phoneRepo = phoneRepo;
        this.scopeChecker = scopeChecker;
        this.normalizer = normalizer;
        this.ownershipLookup = ownershipLookup;
        this.planLimits = planLimits;
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public List<PhoneNumber> listForOwner(Role role, UUID currentUserId,
                                          PhoneOwnerType ownerType, UUID ownerId) {
        scopeChecker.requireWriteScope(role, currentUserId, ownerType, ownerId);
        return ownerPhones(ownerType, ownerId);
    }

    @Transactional(readOnly = true)
    public PhoneNumber getById(Role role, UUID currentUserId, UUID phoneId) {
        PhoneNumber phone = phoneRepo.findById(phoneId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Phone number not found: " + phoneId));
        scopeChecker.requireReadScope(role, currentUserId, phone);
        return phone;
    }

    // ============================================================
    // Create
    // ============================================================

    @Transactional
    public PhoneNumber create(Role role, UUID currentUserId, PhoneNumberRequest req) {
        scopeChecker.requireWriteScope(role, currentUserId, req.ownerType(), req.ownerId());

        // PR12 — plan-tier-aware cap. Resolves the owner's school first and
        // delegates to PlanLimitsService; falls back to the no-tenant floor
        // (2 phones) when the owner is unaffiliated. The legacy
        // PHONE_LIMIT_EXCEEDED code is preserved for owners with no tenant
        // pointer, while plan-bound owners get the more informative
        // PLAN_LIMIT_EXCEEDED code.
        int existing = ownerPhones(req.ownerType(), req.ownerId()).size();
        Optional<UUID> ownerSchool = resolveOwnerSchoolId(req.ownerType(), req.ownerId());
        if (ownerSchool.isPresent()) {
            planLimits.requirePhoneCapacity(ownerSchool.get(), existing);
        } else if (existing >= MAX_PHONES_PER_OWNER_NO_TENANT) {
            throw new BadRequestException(
                    "PHONE_LIMIT_EXCEEDED",
                    "An unaffiliated owner cannot have more than "
                            + MAX_PHONES_PER_OWNER_NO_TENANT + " phone numbers");
        }

        E164Normalizer.Normalized n = normalizer.normalize(req.countryCode(), req.nationalNumber());

        // Same e164 + same owner is forbidden by partial unique indexes; surface
        // a friendly error before hitting the DB, but the constraint is still
        // the source of truth (race-safe).
        if (ownerPhones(req.ownerType(), req.ownerId()).stream()
                .anyMatch(p -> p.getE164().equals(n.e164()))) {
            throw new BadRequestException(
                    "PHONE_ALREADY_EXISTS",
                    "Owner already has a phone number with E.164 " + n.e164());
        }

        PhoneNumber phone = PhoneNumber.builder()
                .countryCode(n.countryCode())
                .nationalNumber(n.nationalNumber())
                .e164(n.e164())
                .label(req.label())
                .whatsapp(req.isWhatsapp() == null || req.isWhatsapp())          // default TRUE
                .whatsappOptIn(req.whatsappOptIn() == null || req.whatsappOptIn()) // default TRUE
                .primary(false) // promotePrimary handles this in the same Tx
                .build();
        applyOwner(phone, req.ownerType(), req.ownerId());

        phone = phoneRepo.save(phone);

        // Make this the primary if requested OR if it's the first one for the
        // owner (otherwise the owner has no primary, which is awkward for
        // every downstream consumer that needs "the number to call").
        boolean shouldBePrimary = Boolean.TRUE.equals(req.makePrimary()) || existing == 0;
        if (shouldBePrimary) {
            phone = promoteToPrimaryInternal(phone);
        }

        log.info("Created phone {} for {}={} (primary={}, verified=false)",
                phone.getId(), req.ownerType(), req.ownerId(), phone.isPrimary());
        return phone;
    }

    // ============================================================
    // Update
    // ============================================================

    @Transactional
    public PhoneNumber update(Role role, UUID currentUserId, UUID phoneId,
                              PhoneNumberUpdateRequest req) {
        PhoneNumber phone = phoneRepo.findById(phoneId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Phone number not found: " + phoneId));
        scopeChecker.requireWriteScope(role, currentUserId, phone);

        // countryCode + nationalNumber must travel together. Splitting them
        // would let a partial update silently leave a corrupt e164.
        boolean ccProvided = req.countryCode() != null;
        boolean nnProvided = req.nationalNumber() != null;
        if (ccProvided != nnProvided) {
            throw new BadRequestException(
                    "INVALID_PHONE_NUMBER",
                    "countryCode and nationalNumber must be updated together");
        }
        if (ccProvided) {
            E164Normalizer.Normalized n = normalizer.normalize(req.countryCode(), req.nationalNumber());

            // If the e164 actually changed, re-check the same-owner uniqueness.
            if (!n.e164().equals(phone.getE164())) {
                boolean conflict = ownerPhones(scopeChecker.ownerOf(phone).type(),
                                                scopeChecker.ownerOf(phone).id())
                        .stream()
                        .filter(p -> !p.getId().equals(phone.getId()))
                        .anyMatch(p -> p.getE164().equals(n.e164()));
                if (conflict) {
                    throw new BadRequestException(
                            "PHONE_ALREADY_EXISTS",
                            "Owner already has a phone number with E.164 " + n.e164());
                }
                phone.setCountryCode(n.countryCode());
                phone.setNationalNumber(n.nationalNumber());
                phone.setE164(n.e164());
                // Number changed → previous verification is stale.
                phone.setVerifiedAt(null);
            }
        }

        if (req.label() != null) phone.setLabel(req.label());
        if (req.isWhatsapp() != null) phone.setWhatsapp(req.isWhatsapp());
        if (req.whatsappOptIn() != null) phone.setWhatsappOptIn(req.whatsappOptIn());

        return phoneRepo.save(phone);
    }

    // ============================================================
    // Primary toggle
    // ============================================================

    @Transactional
    public PhoneNumber promoteToPrimary(Role role, UUID currentUserId, UUID phoneId) {
        PhoneNumber phone = phoneRepo.findById(phoneId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Phone number not found: " + phoneId));
        scopeChecker.requireWriteScope(role, currentUserId, phone);
        return promoteToPrimaryInternal(phone);
    }

    /**
     * Demote any current primary on the same owner, then promote {@code phone}.
     * The two saves run in a single transaction so the partial unique index
     * never sees two primaries simultaneously.
     */
    private PhoneNumber promoteToPrimaryInternal(PhoneNumber phone) {
        if (phone.isPrimary()) {
            return phone;
        }
        Optional<PhoneNumber> existingPrimary = findPrimaryForOwner(phone);
        existingPrimary.ifPresent(p -> {
            if (!p.getId().equals(phone.getId())) {
                p.setPrimary(false);
                phoneRepo.save(p);
            }
        });
        phone.setPrimary(true);
        return phoneRepo.save(phone);
    }

    // ============================================================
    // WhatsApp opt-in toggle
    // ============================================================

    @Transactional
    public PhoneNumber setWhatsappOptIn(Role role, UUID currentUserId, UUID phoneId, boolean optIn) {
        PhoneNumber phone = phoneRepo.findById(phoneId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Phone number not found: " + phoneId));
        scopeChecker.requireWriteScope(role, currentUserId, phone);
        phone.setWhatsappOptIn(optIn);
        return phoneRepo.save(phone);
    }

    // ============================================================
    // Delete
    // ============================================================

    @Transactional
    public void delete(Role role, UUID currentUserId, UUID phoneId) {
        PhoneNumber phone = phoneRepo.findById(phoneId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Phone number not found: " + phoneId));
        scopeChecker.requireWriteScope(role, currentUserId, phone);
        phoneRepo.delete(phone);
        log.info("Deleted phone {} owned by {}", phone.getId(), scopeChecker.ownerOf(phone));
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** All phones for the given owner. */
    public List<PhoneNumber> ownerPhones(PhoneOwnerType type, UUID ownerId) {
        return switch (type) {
            case USER       -> phoneRepo.findByUserId(ownerId);
            case SCHOOL     -> phoneRepo.findBySchoolId(ownerId);
            case INSTRUCTOR -> phoneRepo.findByInstructorId(ownerId);
            case STUDENT    -> phoneRepo.findByStudentId(ownerId);
        };
    }

    private Optional<PhoneNumber> findPrimaryForOwner(PhoneNumber phone) {
        if (phone.getUserId() != null)       return phoneRepo.findByUserIdAndPrimaryTrue(phone.getUserId());
        if (phone.getSchoolId() != null)     return phoneRepo.findBySchoolIdAndPrimaryTrue(phone.getSchoolId());
        if (phone.getInstructorId() != null) return phoneRepo.findByInstructorIdAndPrimaryTrue(phone.getInstructorId());
        if (phone.getStudentId() != null)    return phoneRepo.findByStudentIdAndPrimaryTrue(phone.getStudentId());
        return Optional.empty();
    }

    private void applyOwner(PhoneNumber phone, PhoneOwnerType type, UUID ownerId) {
        switch (type) {
            case USER       -> phone.setUserId(ownerId);
            case SCHOOL     -> phone.setSchoolId(ownerId);
            case INSTRUCTOR -> phone.setInstructorId(ownerId);
            case STUDENT    -> phone.setStudentId(ownerId);
        }
    }

    /**
     * Resolves the school that "owns" this phone via its owner pointer. The
     * SCHOOL case is trivial (the school is the owner). USER/INSTRUCTOR/STUDENT
     * cases delegate to {@link PhoneOwnershipLookup}.
     */
    private Optional<UUID> resolveOwnerSchoolId(PhoneOwnerType type, UUID ownerId) {
        return switch (type) {
            case SCHOOL     -> Optional.of(ownerId);
            case USER       -> ownershipLookup.userSchoolId(ownerId);
            case INSTRUCTOR -> ownershipLookup.instructorSchoolId(ownerId);
            case STUDENT    -> ownershipLookup.studentSchoolId(ownerId);
        };
    }
}
