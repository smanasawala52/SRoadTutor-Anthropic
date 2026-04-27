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
import com.sroadtutor.subscription.dto.UpgradeResponse;
import com.sroadtutor.subscription.model.PlanTier;
import com.sroadtutor.subscription.model.Subscription;
import com.sroadtutor.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription read + upgrade flow. Locked at PR12.5:
 *
 * <p>Source-of-truth (per S6): an active {@link Subscription} row's
 * {@code plan} wins over {@code schools.plan_tier}. Both columns are kept
 * in sync by webhook-driven updates and admin-mode flips.</p>
 *
 * <p>Two upgrade paths:
 * <ul>
 *   <li><b>Stripe Checkout</b> (preferred when configured + price id exists)
 *       — returns a hosted-Checkout URL; plan does NOT flip until the
 *       webhook fires.</li>
 *   <li><b>Admin-mode</b> — flips plan + limits + schools.plan_tier inline.
 *       Used when Stripe isn't configured or the target plan has no Stripe
 *       price id.</li>
 * </ul>
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subRepo;
    private final SchoolRepository schoolRepo;
    private final UserRepository userRepo;
    private final PlanLimitsService plans;
    private final StripeService stripeService;

    public SubscriptionService(SubscriptionRepository subRepo,
                                SchoolRepository schoolRepo,
                                UserRepository userRepo,
                                PlanLimitsService plans,
                                StripeService stripeService) {
        this.subRepo = subRepo;
        this.schoolRepo = schoolRepo;
        this.userRepo = userRepo;
        this.plans = plans;
        this.stripeService = stripeService;
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
    // Upgrade — Stripe-first, admin-mode fallback
    // ============================================================

    @Transactional
    public UpgradeResponse upgrade(Role role, UUID currentUserId, UpgradeRequest req) {
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
        if (!currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("OWNER can only manage their own school's plan");
        }

        PlanTier target = PlanTier.fromString(req.targetPlan());

        // Stripe path: only used when (1) Stripe is configured AND (2) the
        // target tier has a configured Price id. FREE downgrades always go
        // through admin-mode (Stripe doesn't sell FREE).
        boolean stripeEligible = target != PlanTier.FREE
                && stripeService != null
                && stripeService.isConfigured()
                && stripeService.priceFor(target) != null;

        if (stripeEligible) {
            String checkoutUrl = stripeService.createUpgradeCheckoutSession(
                    schoolId, school.getStripeCustomerId(), target);
            // Plan does NOT flip yet — webhook will. Return the URL.
            log.info("Stripe Checkout session created for school={} target={} (plan flip pending webhook)",
                    schoolId, target);
            return new UpgradeResponse(
                    UpgradeResponse.MODE_STRIPE_CHECKOUT,
                    plans.currentPlan(schoolId).name(),
                    checkoutUrl);
        }

        // Admin-mode fallback — flips plan + limits + schools.plan_tier inline.
        applyPlanFlip(school, target, null);
        log.info("School {} plan changed to {} by OWNER {} (admin-mode)",
                schoolId, target.name(), currentUserId);
        return new UpgradeResponse(
                UpgradeResponse.MODE_ADMIN,
                target.name(),
                null);
    }

    // ============================================================
    // Webhook-driven plan flip (called from StripeWebhookController)
    // ============================================================

    /**
     * Called by the Stripe webhook on {@code customer.subscription.created}
     * or {@code customer.subscription.updated} events. Flips the
     * subscription row + schools.plan_tier to match Stripe's state.
     *
     * @param schoolId        school id (read from session metadata or
     *                        customer metadata at the controller layer)
     * @param targetPlan      the new tier
     * @param stripeSubId     Stripe subscription id to record
     * @param currentPeriodEnd renewal date from Stripe (may be null)
     */
    @Transactional
    public void applyStripeUpdate(UUID schoolId, PlanTier targetPlan,
                                    String stripeSubId, Instant currentPeriodEnd) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        applyPlanFlip(school, targetPlan, stripeSubId);
        if (currentPeriodEnd != null) {
            subRepo.findFirstBySchoolIdAndCancelledAtIsNullOrderByCreatedAtDesc(schoolId)
                    .ifPresent(s -> {
                        s.setCurrentPeriodEnd(currentPeriodEnd);
                        subRepo.save(s);
                    });
        }
        log.info("Stripe webhook applied: school={} plan={} stripeSub={} periodEnd={}",
                schoolId, targetPlan, stripeSubId, currentPeriodEnd);
    }

    /**
     * Called on {@code customer.subscription.deleted} — Stripe canceled
     * the subscription. Marks the local row cancelled and falls back to FREE.
     */
    @Transactional
    public void applyStripeCancellation(UUID schoolId) {
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        Subscription sub = subRepo
                .findFirstBySchoolIdAndCancelledAtIsNullOrderByCreatedAtDesc(schoolId)
                .orElse(null);
        if (sub != null) {
            sub.setCancelledAt(Instant.now());
            subRepo.save(sub);
        }
        school.setPlanTier(PlanTier.FREE.name());
        schoolRepo.save(school);
        log.info("Stripe webhook cancelled subscription for school={} — falling back to FREE", schoolId);
    }

    // ============================================================
    // Internal — flip
    // ============================================================

    private void applyPlanFlip(School school, PlanTier target, String stripeSubId) {
        UUID schoolId = school.getId();
        Subscription sub = subRepo
                .findFirstBySchoolIdAndCancelledAtIsNullOrderByCreatedAtDesc(schoolId)
                .orElse(null);
        if (sub == null) {
            sub = Subscription.builder()
                    .schoolId(schoolId)
                    .plan(target.name())
                    .instructorLimit(target.instructorLimit())
                    .studentLimit(target.studentLimit())
                    .stripeSubId(stripeSubId)
                    .build();
        } else {
            sub.setPlan(target.name());
            sub.setInstructorLimit(target.instructorLimit());
            sub.setStudentLimit(target.studentLimit());
            sub.setCancelledAt(null);
            if (stripeSubId != null) sub.setStripeSubId(stripeSubId);
        }
        subRepo.save(sub);

        // Keep schools.plan_tier in sync as the fallback source.
        school.setPlanTier(target.name());
        schoolRepo.save(school);
    }
}
