package com.sroadtutor.subscription.model;

/**
 * Subscription plan tiers + their per-tenant limits.
 *
 * <p>Limits are baked into the enum so plan changes are a code-level
 * decision, not a DB tweak. {@code -1} means "unlimited". The
 * {@link com.sroadtutor.subscription.service.PlanLimitsService} reads
 * these constants directly.</p>
 *
 * <p>Phase 1 catalog:
 * <pre>
 *                 instructors  students  phones/owner  wa.me/month  monthly $
 *   FREE                 1         10          2          50          $0
 *   PRO                  3         50          5         500          $29
 *   GROWTH              10        200          5        2000          $69
 *   ENTERPRISE          50       1000         20       10000         $149
 * </pre>
 * </p>
 */
public enum PlanTier {
    FREE       (1,    10,   2,    50,    "0.00"),
    PRO        (3,    50,   5,   500,   "29.00"),
    GROWTH     (10,  200,   5,  2000,   "69.00"),
    ENTERPRISE (50, 1000,  20, 10000,  "149.00");

    private final int instructorLimit;
    private final int studentLimit;
    private final int phonesPerOwnerLimit;
    private final int waMeMonthlyLimit;
    private final String monthlyPriceCad;

    PlanTier(int instructorLimit, int studentLimit, int phonesPerOwnerLimit,
             int waMeMonthlyLimit, String monthlyPriceCad) {
        this.instructorLimit = instructorLimit;
        this.studentLimit = studentLimit;
        this.phonesPerOwnerLimit = phonesPerOwnerLimit;
        this.waMeMonthlyLimit = waMeMonthlyLimit;
        this.monthlyPriceCad = monthlyPriceCad;
    }

    public int instructorLimit()      { return instructorLimit; }
    public int studentLimit()         { return studentLimit; }
    public int phonesPerOwnerLimit()  { return phonesPerOwnerLimit; }
    public int waMeMonthlyLimit()     { return waMeMonthlyLimit; }
    public String monthlyPriceCad()   { return monthlyPriceCad; }

    /** Parse a plan-tier string (case-insensitive). Defaults to FREE on null/blank/unknown. */
    public static PlanTier fromString(String s) {
        if (s == null || s.isBlank()) return FREE;
        try {
            return PlanTier.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FREE;
        }
    }
}
