package com.sroadtutor;

import com.sroadtutor.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point. Spring Boot boots the whole application context from here.
 *
 * <p>Run via IntelliJ (green arrow in the gutter) or {@code ./mvnw spring-boot:run}.
 * The active profile is picked up from the {@code SPRING_PROFILES_ACTIVE} env var —
 * see {@code application.yml}.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SRoadTutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SRoadTutorApplication.class, args);
    }
}
