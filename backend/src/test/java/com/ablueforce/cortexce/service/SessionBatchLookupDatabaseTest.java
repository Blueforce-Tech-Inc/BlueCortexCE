package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the batched conversation lookups used by {@code POST /api/sdk-sessions/batch}.
 *
 * <p>The endpoint used to issue one query per requested id (a textbook N+1). The batch
 * query must return exactly the rows the single-id lookup returns, in particular:
 * <ol>
 *   <li>a missing id stays absent;</li>
 *   <li>the same external id on another platform is never returned by the Claude batch
 *       (the byte-exact composite identity is the whole point of V20);</li>
 *   <li>the composite and ordering indexes this fix relies on actually exist after
 *       Flyway has migrated the database.</li>
 * </ol>
 *
 * <p>Gated on the dedicated {@code *_flyway_rehearsal} database, like the other
 * database-level tests: mocks cannot demonstrate that a single {@code IN} query
 * produces the same rows as N single-row queries.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SessionManagementService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(
    named = "MEMSCOPE_TEST_DB_URL",
    matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?"
)
class SessionBatchLookupDatabaseTest {

    private static final List<String> HOT_QUERY_INDEXES = List.of(
        "idx_observations_scope_created",
        "idx_observations_type_created",
        "idx_observations_project_created",
        "idx_sessions_project_started"
    );

    @Autowired private SessionManagementService sessionManagementService;
    @Autowired private JdbcTemplate jdbc;

    private final List<UUID> sessionIds = new ArrayList<>();

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> env("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("MEMSCOPE_TEST_DB_PASSWORD", "postgres"));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @BeforeEach
    void requireSchema() {
        Integer present = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.tables
            WHERE table_name = 'mem_sessions'
            """, Integer.class);
        assertThat(present).isEqualTo(1);
    }

    @AfterEach
    void cleanup() {
        sessionIds.forEach(id -> jdbc.update("DELETE FROM mem_sessions WHERE id = ?", id));
        sessionIds.clear();
    }

    private UUID insertSession(String platform, String contentSessionId, String project, long startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO mem_sessions (
                id, content_session_id, project_path, started_at_epoch, status, platform_source
            ) VALUES (?, ?, ?, ?, 'completed', ?)
            """, id, contentSessionId, project, startedAt, platform);
        sessionIds.add(id);
        return id;
    }

    @Test
    @DisplayName("batched legacy lookup matches the single-id lookup and ignores other platforms")
    void batchMatchesSingleLookup() {
        UUID first = insertSession("claude", "batch-a", "/work", 1_000L);
        UUID second = insertSession("claude", "batch-b", "/work", 2_000L);
        // Same external id, different platform: must not leak into the Claude batch.
        insertSession("codex", "batch-a", "/work", 3_000L);
        insertSession("codex", "batch-only-codex", "/work", 4_000L);

        Map<String, SessionEntity> batch = sessionManagementService.findLegacyClaudeBatch(
            List.of("batch-a", "batch-b", "batch-missing"));

        assertThat(batch.keySet()).containsExactlyInAnyOrder("batch-a", "batch-b");
        assertThat(batch.get("batch-a").getId()).isEqualTo(first);
        assertThat(batch.get("batch-b").getId()).isEqualTo(second);
        assertThat(batch.get("batch-a").getPlatformSource()).isEqualTo("claude");
        assertThat(batch).doesNotContainKey("batch-missing");

        // The batch result must agree with what a per-id caller would have seen.
        assertThat(sessionManagementService.findLegacyClaude("batch-a").orElseThrow().getId())
            .isEqualTo(first);
        assertThat(sessionManagementService.findLegacyClaude("batch-missing")).isEmpty();
        assertThat(sessionManagementService.findLegacyClaude("batch-only-codex")).isEmpty();
    }

    @Test
    @DisplayName("batched ref lookup keeps the same external id distinct per platform")
    void refBatchGroupsByPlatform() {
        UUID claude = insertSession("claude", "shared-id", "/work", 1_000L);
        UUID codex = insertSession("codex", "shared-id", "/work", 2_000L);

        Map<ExternalSessionRef, SessionEntity> found = sessionManagementService.findBatch(List.of(
            ExternalSessionRef.of("claude", "shared-id"),
            ExternalSessionRef.of("codex", "shared-id"),
            ExternalSessionRef.of("claude", "shared-missing")));

        assertThat(found).hasSize(2);
        assertThat(found.get(ExternalSessionRef.of("claude", "shared-id")).getId()).isEqualTo(claude);
        assertThat(found.get(ExternalSessionRef.of("codex", "shared-id")).getId()).isEqualTo(codex);
        assertThat(found).doesNotContainKey(ExternalSessionRef.of("claude", "shared-missing"));
    }

    @Test
    @DisplayName("the hot-query indexes are applied by Flyway")
    void hotQueryIndexesExist() {
        for (String index : HOT_QUERY_INDEXES) {
            Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                Integer.class, index);
            assertThat(count).as("index %s", index).isEqualTo(1);
        }

        String scopeIndex = jdbc.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
            String.class, "idx_observations_scope_created");
        assertThat(scopeIndex).contains("memory_scope_id", "created_at_epoch DESC", "id DESC");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for this database test");
        }
        return value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
