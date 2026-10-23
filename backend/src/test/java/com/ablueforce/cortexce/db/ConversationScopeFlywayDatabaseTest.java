package com.ablueforce.cortexce.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConversationScopeFlywayDatabaseTest {

    @Test
    void flywayRunsV20ThroughItsJdbcMigrationPath() {
        String url = System.getenv("MEMSCOPE_FLYWAY_TEST_URL");
        assumeTrue(url != null && !url.isBlank(),
            "set MEMSCOPE_FLYWAY_TEST_URL to a disposable *_flyway_rehearsal database");
        if (!url.matches("jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")) {
            throw new IllegalArgumentException(
                "refusing Flyway smoke test unless database name ends with _flyway_rehearsal");
        }

        String user = env("MEMSCOPE_FLYWAY_TEST_USER", "postgres");
        String password = env("MEMSCOPE_FLYWAY_TEST_PASSWORD", "postgres");
        Flyway flyway = Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .load();

        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("20");
        assertThat(flyway.info().pending()).isEmpty();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM mem_memory_scopes
            WHERE scope_type = 'manual_default' AND scope_key = 'manual-default-v1'
            """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '20' AND success", Integer.class))
            .isEqualTo(1);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
