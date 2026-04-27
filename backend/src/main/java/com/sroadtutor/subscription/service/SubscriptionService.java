package com.sroadtutor.subscription.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.subscription.dto.SubscriptionMeResponse;
import com.sroadtutor.subscription.dto.UpgradeRequest;
import com.sroadtutor.subscription.model.PlanTier;
import com.sroadtutor.subscription.model.Subscription;
import com.sroadtutor.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Subscription read + admin-mode upgrade. Stripe wiring lands in PR12.5.
 *
 * <p>Source-of-truth (per S6): an active {@link Subscription} row's
 * {@code plan} wins over {@code schools.plan_tier}. Both columns are kept
 * in sync by {@link #upgrade}.</p>
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subRepo;
    private final SchoolRepository schoolRepo;
    private final UserRepository userRepo;
    private final PlanLimitsService plans;

    public SubscriptionService(SubscriptionRepository subRepo,
                                SchoolRepository schoolRepo,
                                UserRepository userRepo,
                                PlanLimitsService plans) {
        this.subRepo = subRepo;
        this.schoolRepo = schoolRepo;
        this.userRepo = userRepo;
        this.plans = plans;
    }

    // ============================================================
    // Read — mine
    // ============================================================

    @Transactional(readOnly = true)
    public SubscriptionMeResponse getMine(UUID currentUserId) {
        User caller = userRepo.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));
        if (caller.getSchoolId() == null) {
            throw new ResourceNotFoundException("Caller is not assigned to a school yet");
        }
        UUID schoolId = caller.getSchoolId();

        PlanTier plan = plans.currentPlan(schoolId);
        Optional<Subscription> active = subRepo
                .findFirstBySchoolIdAndCancelledAtIsNullOrderByCreatedAtDesc(schoolId);
        boolean stripeManaged = active.isPresent() && active.get().getStripeSubId() != null;

        return new SubscriptionMeResponse(
                plan.name(),
                plan.monthlyPriceCad(),
                new SubscriptionMeResponse.Limits(
                        plan.instructorLimit(),
                        plan.studentLimit(),
                        plan.phonesPerOwnerLimit(),
                        plan.waMeMonthlyLimit()),
                new SubscriptionMeResponse.Usage(plans.currentWaMeUsage(schoolId)),
                stripeManaged);
    }

    // ============================================================
    // Upgrade (admin-mode stub)
    // ============================================================

    @Transactional
    public Subscription upgrade(Role role, UUID currentUserId, UpgradeRequest req) {
        if (role != Role.OWNER) {
            throw new AccessDeniedException("Only an OWNER can change their plan");
        }
        User owner = userRepo.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));
        if (owner.getSchoolId() == null) {
            throw new BadRequestException(
                    "USER_HAS_NO_SCHOOL",
                    "OWNER must have a school before subscribing");
        }
        UUID schoolId = owner.getSchoolId();
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        if (!schoolId.equals(school.getId()) || !currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("OWNER can only manage their own school's plan");
        }

        PlanTier target = PlanTier.fromString(req.targetPlan());

        // Reuse the existing active row if there is one; otherwise create.
        Subscription sub = subRepo
                .findFirstBySchoolIdAndCancelledAtIsNullOrderByCreatedAtDesc(schoolId)
                .orElse(null);
        if (sub == null) {
            sub = Subscription.builder()
                    .schoolId(schoolId)
                    .plan(target.name())
                    .instructorLimit(target.instructorLimit())
                    .studentLimit(target.studentLimit())
                    .build();
        } else {
            sub.setPlan(target.name());
            sub.setInstructorLimit(target.instructorLimit());
            sub.setStudentLimit(target.studentLimit());
            sub.setCancelledAt(null);
        }
        sub = subRepo.save(sub);

        // Keep schools.plan_tier in sync as the fallback source.
        school.setPlanTier(target.name());
        schoolRepo.save(school);

        log.info("School {} plan changed to {} by OWNER {} (admin-mode stub)",
                schoolId, target.name(), currentUserId);
        return sub;
    }
}
