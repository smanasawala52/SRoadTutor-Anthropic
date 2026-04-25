package com.sroadtutor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Smoke test: the Spring context loads end-to-end. */
@SpringBootTest
@ActiveProfiles("test")
class SRoadTutorApplicationTest {


    void contextLoads() {
        // If the context fails to start, this test fails. That's the whole point.
    }
}
