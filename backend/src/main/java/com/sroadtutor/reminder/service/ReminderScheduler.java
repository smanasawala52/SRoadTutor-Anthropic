package com.sroadtutor.reminder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron-driven entry point for {@link ReminderService#sweepUpcomingSessions}.
 * Per R4: every 15 minutes. Initial delay (60s) gives the application
 * context time to settle before the first sweep.
 *
 * <p>Errors are caught and logged — a failed sweep should never crash the
 * scheduler thread (otherwise subsequent sweeps would silently stop).</p>
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService service;

    public ReminderScheduler(ReminderService service) {
        this.service = service;
    }

    @Scheduled(initialDelay = 60_000L, fixedDelay = 15 * 60_000L)
    public void runSweep() {
        try {
            int created = service.sweepUpcomingSessions();
            if (created > 0) {
                log.info("Reminder sweep tick — created {} new PENDING rows", created);
            }
        } catch (RuntimeException ex) {
            log.error("Reminder sweep failed — will retry on next tick", ex);
        }
    }
}
