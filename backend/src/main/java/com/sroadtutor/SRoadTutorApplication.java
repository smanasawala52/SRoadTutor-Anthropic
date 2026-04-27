package com.sroadtutor;

import com.sroadtutor.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. Spring Boot boots the whole application context from here.
 *
 * <p>Run via IntelliJ (green arrow in the gutter) or {@code ./mvnw spring-boot:run}.
 * The active profile is picked up from the {@code SPRING_PROFILES_ACTIVE} env var —
 * see {@code application.yml}.</p>
 *
 * <p>{@code @EnableScheduling} powers the PR10 reminder sweep (see
 * {@link com.sroadtutor.reminder.service.ReminderScheduler}). Spring's
 * default scheduler runs all {@code @Scheduled} methods on a single
 * thread; for V1 traffic that's fine — revisit when we have multiple
 * cron-driven jobs.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class SRoadTutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SRoadTutorApplication.class, args);
    }
}
