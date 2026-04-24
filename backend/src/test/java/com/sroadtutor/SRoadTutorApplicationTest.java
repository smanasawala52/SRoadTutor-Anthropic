package com.sroadtutor;

import com.sroadtutor.config.PostgresTestcontainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Smoke test: the Spring context loads end-to-end. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(PostgresTestcontainerConfig.class)
class SRoadTutorApplicationTest {

    @Test
    void contextLoads() {
        // If the context fails to start, this test fails. That's the whole point.
    }
}
