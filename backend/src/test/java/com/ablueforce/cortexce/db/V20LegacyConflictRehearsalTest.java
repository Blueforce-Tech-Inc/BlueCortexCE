package com.ablueforce.cortexce.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Rehearses the two conflicts V20 can hit on a real legacy-shaped database, and proves
 * that the documented recovery loop works: read the diagnostic code, follow the
 * documented cleanup, re-run the migration.
 *
 * <ol>
 *   <li>A {@code manual-memories} row with the right shape but no manual markers is a
 *       technical bucket conflict ({@code TECHNICAL_BUCKET_CONFLICT}). Deleting the empty
 *       bucket is the documented cleanup.</li>
 *   <li>An observation with a blank project path cannot be given a memory scope
 *       ({@code OBSERVATION_WITHOUT_SCOPE}), because scope rows are only created for
 *       non-blank paths. Deleting (or re-homing) that row is the documented cleanup.</li>
 * </ol>
 *
 * <p>Each failed run must leave no partial schema behind, and each diagnostic must name
 * the offending value so an operator can act on it without reading the migration source.
 *
 * <p>Gated on {@code MEMSCOPE_FLYWAY_TEST_URL}; the test creates a sibling disposable
 * database so it never touches the rehearsal database the rest of the suite uses.
 */
class V20LegacyConflictRehearsalTest {

    private static final Pattern JDBC_URL =
        Pattern.compile("jdbc:postgresql://([^/]+)/([^?]+)(\\?.*)?");

    @Test
    void legacyConflictsAreDiagnosableAndRecoverable() throws Exception {
        String url = System.getenv("MEMSCOPE_FLYWAY_TEST_URL");
        assumeTrue(url != null && !url.isBlank(),
            "set MEMSCOPE_FLYWAY_TEST_URL to a disposable *_flyway_rehearsal database");
        Matcher matcher = JDBC_URL.matcher(url);
        assumeTrue(matcher.matches(),
            "MEMSCOPE_FLYWAY_TEST_URL must be a jdbc:postgresql://host/database URL");
        assumeTrue(matcher.group(2).endsWith("_flyway_rehearsal"),
            "refusing to create rehearsal databases unless the configured name ends with _flyway_rehearsal");

        String hostAndPort = matcher.group(1);
        String baseName = matcher.group(2).replaceFirst("_flyway_rehearsal$", "");
        String conflictDb = baseName + "_v20legacy_flyway_rehearsal";
        assertThat(conflictDb).matches("[a-z0-9_]+");

        String adminUrl = "jdbc:postgresql://" + hostAndPort + "/postgres";
        String conflictUrl = "jdbc:postgresql://" + hostAndPort + "/" + conflictDb;
        String user = env("MEMSCOPE_FLYWAY_TEST_USER", "postgres");
        String password = env("MEMSCOPE_FLYWAY_TEST_PASSWORD", "postgres");

        try {
            recreateDatabase(adminUrl, user, password, conflictDb);
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(conflictUrl, user, password));

            migrateTo(conflictUrl, user, password, "19");
            seedLegacyShape(jdbc);

            // 1. The empty manual bucket is a hard conflict, and the message names it.
            assertThatThrownBy(() -> migrateTo(conflictUrl, user, password, null))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("TECHNICAL_BUCKET_CONFLICT")
                .hasMessageContaining("manual-memories");
            assertThat(jdbc.queryForObject("SELECT to_regclass('public.mem_memory_scopes')",
                String.class)).isNull();

            // 2. Documented cleanup for that diagnostic: remove the empty legacy bucket.
            jdbc.update("DELETE FROM mem_sessions WHERE content_session_id = 'manual-memories'");

            // 3. The next blocker is the scopeless observation, again with a readable code.
            assertThatThrownBy(() -> migrateTo(conflictUrl, user, password, null))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("OBSERVATION_WITHOUT_SCOPE")
                .hasMessageContaining("1 observation(s)");
            assertThat(jdbc.queryForObject("SELECT to_regclass('public.mem_memory_scopes')",
                String.class)).isNull();

            // 4. Documented cleanup for that diagnostic: drop the row whose path was never
            //    a real project (a re-home is the alternative if the data matters).
            jdbc.update("""
                DELETE FROM mem_observations
                WHERE content_session_id = 'legacy-conv' AND project_path = ''
                """);

            // 5. Re-running the migration now succeeds, and the surviving valid row is
            //    scoped exactly as the migration promises.
            migrateTo(conflictUrl, user, password, null);
            assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM mem_observations
                WHERE content_session_id = 'legacy-conv'
                  AND conversation_id IS NOT NULL AND memory_scope_id IS NOT NULL
                """, Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM mem_memory_scopes
                WHERE scope_type = 'project' AND scope_key = '/legacy/project'
                """, Integer.class)).isEqualTo(1);
        } finally {
            dropDatabaseQuietly(adminUrl, user, password, conflictDb);
        }
    }

    private static void seedLegacyShape(JdbcTemplate jdbc) {
        // Empty legacy technical bucket: the shape says "manual" but there is no manual
        // marker row, which is exactly the conflict an operator must resolve by hand.
        jdbc.update("""
            INSERT INTO mem_sessions
                (content_session_id, project_path, started_at_epoch, status, user_prompt, last_assistant_message)
            VALUES ('manual-memories', 'manual-memories', 1000, 'completed', NULL, NULL)
            """);
        jdbc.update("""
            INSERT INTO mem_sessions
                (content_session_id, project_path, started_at_epoch, status, user_prompt, last_assistant_message)
            VALUES ('legacy-conv', '/legacy/project', 2000, 'completed', 'prompt', 'assistant')
            """);
        // A healthy row (proves normal data migrates) and a legacy row whose project path
        // was never set, which cannot be scoped without an operator decision.
        jdbc.update("""
            INSERT INTO mem_observations
                (content_session_id, project_path, type, title, content, created_at_epoch)
            VALUES ('legacy-conv', '/legacy/project', 'discovery', 'valid', 'body', 3000)
            """);
        jdbc.update("""
            INSERT INTO mem_observations
                (content_session_id, project_path, type, title, content, created_at_epoch)
            VALUES ('legacy-conv', '', 'discovery', 'blank path', 'body', 4000)
            """);
    }

    private static void migrateTo(String url, String user, String password, String target) {
        var config = Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .cleanDisabled(true);
        if (target != null) {
            config.target(MigrationVersion.fromVersion(target));
        }
        Flyway flyway = config.load();
        flyway.migrate();
        if (target == null) {
            assertThat(flyway.info().pending()).isEmpty();
        }
    }

    private static void recreateDatabase(String adminUrl, String user, String password, String dbName)
        throws Exception {
        try (Connection connection = DriverManager.getConnection(adminUrl, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + dbName);
        }
        try (Connection connection = DriverManager.getConnection(
                adminUrl.replaceFirst("/postgres$", "/" + dbName), user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
        }
    }

    private static void dropDatabaseQuietly(String adminUrl, String user, String password, String dbName) {
        try (Connection connection = DriverManager.getConnection(adminUrl, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        } catch (Exception ignored) {
            // Cleanup must not mask the assertion that already failed.
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
