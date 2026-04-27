package com.sroadtutor.subscription.service;

import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.subscription.model.PlanTier;
import com.sroadtutor.subscription.model.Subscription;
import com.sroadtutor.subscription.model.SubscriptionUsage;
import com.sroadtutor.subscription.repository.SubscriptionRepository;
import com.sroadtutor.subscription.repository.SubscriptionUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Plan-limit enforcement entry point. Every "create" path that's plan-gated
 * (phone create, instructor attach, student create, wa.me send) calls into
 * this service before persisting.
 *
 * <p>Limit-exceeded behaviour (per S4): hard error with code
 * {@code PLAN_LIMIT_EXCEEDED} carrying the plan name in the message so the
 * SPA can render an upgrade CTA.</p>
 *
 * <p>Grandfathered tenants (per S5): {@link #requirePhoneCapacity} et al
 * compare {@code current} against the limit. A tenant whose current count
 * is already &gt; limit cannot ADD more, but they don't lose what they
 * already have.</p>
 */
@Service
public class PlanLimitsService {

    private static final Logger log = LoggerFactory.getLogger(PlanLimitsService.class);

    private final SchoolRepository schoolRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final SubscriptionUsageRepository usageRepo;

    public PlanLimitsService(SchoolRepository schoolRepo,
                              SubscriptionRepository subscriptionRepo,
                              SubscriptionUsageRepository usageRepo) {
        this.schoolRepo = schoolRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.usageRepo = usageRepo;
    }

    // ============================================================
    // Plan resolution
    // ============================================================

    /**
     * Resolves the effective {@link PlanTier} for a school. Falls back to
     * {@code schools.plan_tier} when no active subscription row exists.
     */
    @Transactional(readOnly = true)
    public PlanTier currentPlan(UUID schoolId) {
        var sub = subscriptionRepo.findFirstBySchoolIdAndCancelledAtIsNullOrderByCreatedAtDesc(schoolId);
        if (sub.isPresent()) {
            return PlanTier.fromString(sub.get().getPlan());
        }
        return schoolRepo.findById(schoolId)
                .map(School::getPlanTier)
                .map(PlanTier::fromString)
                .orElse(PlanTier.FREE);
    }

    // ============================================================
    // Per-action limit checks
    // ============================================================

    public void requirePhoneCapacity(UUID schoolId, int currentCount) {
        PlanTier plan = currentPlan(schoolId);
        int limit = plan.phonesPerOwnerLimit();
        if (limit > 0 && currentCount >= limit) {
            throw planLimitException(plan, "phones", limit);
        }
    }

    public void requireInstructorCapacity(UUID schoolId, int currentCount) {
        PlanTier plan = currentPlan(schoolId);
        int limit = plan.instructorLimit();
        if (limit > 0 && currentCount >= limit) {
            throw planLimitException(plan, "instructors", limit);
        }
    }

    public void requireStudentCapacity(UUID schoolId, int currentCount) {
        PlanTier plan = currentPlan(schoolId);
        int limit = plan.studentLimit();
        if (limit > 0 && currentCount >= limit) {
            throw planLimitException(plan, "students", limit);
        }
    }

    /**
     * Pre-check + atomic increment of the wa.me monthly counter. Throws if
     * the school is at the cap; otherwise increments and returns the new
     * count.
     *
     * <p>Note: the increment happens inside this method's transaction. The
     * caller doesn't need to chain an extra save.</p>
     */
    @Transactional
    public int recordWaMeSendOrThrow(UUID schoolId) {
        PlanTier plan = currentPlan(schoolId);
        int limit = plan.waMeMonthlyLimit();

        LocalDate periodStart = currentPeriodStart();
        SubscriptionUsage usage = usageRepo.findBySchoolIdAndPeriodStart(schoolId, periodStart)
                .orElseGet(() -> SubscriptionUsage.builder()
                        .schoolId(schoolId)
                        .periodStart(periodStart)
                        .waMeCount(0)
                        .build());

        if (limit > 0 && usage.getWaMeCount() >= limit) {
            throw planLimitException(plan, "wa.me messages this month", limit);
        }

        usage.setWaMeCount(usage.getWaMeCount() + 1);
        usage = usageRepo.save(usage);
        return usage.getWaMeCount();
    }

    @Transactional(readOnly = true)
    public int currentWaMeUsage(UUID schoolId) {
        return usageRepo.findBySchoolIdAndPeriodStart(schoolId, currentPeriodStart())
                .map(SubscriptionUsage::getWaMeCount)
                .orElse(0);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static LocalDate currentPeriodStart() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return today.withDayOfMonth(1);
    }

    private static BadRequestException planLimitException(PlanTier plan, String resource, int limit) {
        log.info("Plan limit hit on {}: plan={} limit={}", resource, plan, limit);
        return new BadRequestException(
                "PLAN_LIMIT_EXCEEDED",
                "Your " + plan + " plan allows up to " + limit + " " + resource
                        + ". Upgrade your plan to add more.");
    }

    /** Convenience for callers that want the limit value without the exception path. */
    @Transactional(readOnly = true)
    public int phoneLimit(UUID schoolId) { return currentPlan(schoolId).phonesPerOwnerLimit(); }

    @Transactional(readOnly = true)
    public int instructorLimit(UUID schoolId) { return currentPlan(schoolId).instructorLimit(); }

    @Transactional(readOnly = true)
    public int studentLimit(UUID schoolId) { return currentPlan(schoolId).studentLimit(); }

    @Transactional(readOnly = true)
    public int waMeMonthlyLimit(UUID schoolId) { return currentPlan(schoolId).waMeMonthlyLimit(); }

    /**
     * Convenience: throw if the resource cap has been hit. Used by callers
     * that want a generic guard without computing current counts.
     */
    @SuppressWarnings("unused")
    private static void check(int currentCount, int limit, PlanTier plan, String resource) {
        if (limit > 0 && currentCount >= limit) {
            throw planLimitException(plan, resource, limit);
        }
    }

    /** Defensive: surface "school not found" when used standalone. */
    @Transactional(readOnly = true)
    public void requireSchoolExists(UUID schoolId) {
        if (schoolRepo.findById(schoolId).isEmpty()) {
            throw new ResourceNotFoundException("School not found: " + schoolId);
        }
    }
}
