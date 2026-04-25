package com.sroadtutor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev-profile-only Flyway overrides.
 *
 * <h2>Why this exists</h2>
 * During active development we rewrite migrations in place all the time
 * (fixing column names, re-seeding, tweaking comments).  Flyway's default
 * behaviour is to validate every previously-applied migration by content
 * hash at startup, so any edit — even a whitespace change or a Windows
 * LF→CRLF conversion — triggers:
 *
 * <pre>
 *   Migration checksum mismatch for migration version N
 *   -> Applied to database : ...
 *   -> Resolved locally    : ...
 *   Either revert the changes to the migration, or run repair ...
 * </pre>
 *
 * Running {@code flyway:repair} through Maven works but is awkward for a
 * beginner.  This bean just calls {@code flyway.repair()} before
 * {@code flyway.migrate()} on every startup, which rewrites the stored
 * checksum to match the current file content — no SQL, no Maven plugin.
 *
 * <h2>Why it's dev-only</h2>
 * Auto-repair silently forgives any change to an already-applied
 * migration.  In QA / prod we WANT Flyway to shout if history and code
 * disagree, because that usually means a deploy went sideways or someone
 * edited history they shouldn't have.  The {@code @Profile("dev")} guard
 * keeps this strictly to developer machines.
 */
@Configuration
@Profile("dev")
public class FlywayDevConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayDevConfig.class);

    /**
     * Replaces Spring Boot's default Flyway migration strategy
     * (just {@code flyway.migrate()}) with repair-then-migrate.
     */
    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            log.info("Dev profile: running Flyway repair before migrate (auto-heal checksum drift).");
            flyway.repair();
            flyway.migrate();
        };
    }
}
