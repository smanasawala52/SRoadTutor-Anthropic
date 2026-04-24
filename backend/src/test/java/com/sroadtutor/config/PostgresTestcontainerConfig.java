package com.sroadtutor.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers-managed Postgres for {@code *IntegrationTest.java}.
 * Spring Boot 3.1+ auto-wires the JDBC URL / user / password via
 * {@link ServiceConnection}, so the tests don't need {@code @DynamicPropertySource}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("sroadtutor_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
    }
}
