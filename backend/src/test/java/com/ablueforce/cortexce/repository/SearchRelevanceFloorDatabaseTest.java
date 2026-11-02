package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.service.MemoryScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the relevance floor against a real PostgreSQL + pgvector instance.
 *
 * <p>Invariants:</p>
 * <ol>
 *   <li>An unrelated query vector (cosine 0.0) returns no semantic rows once the floor is 0.5.</li>
 *   <li>A related query vector (cosine 1.0) still returns its row, with the cosine score attached.</li>
 *   <li>The tsvector strategy returns nothing for an unrelated lexical query and its row for a
 *       matching one; the lexical predicate is its floor.</li>
 *   <li>Hybrid and tsvector agree on which rows are relevant for the same text query.</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MemoryScopeService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(
    named = "MEMSCOPE_TEST_DB_URL",
    matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?"
)
class SearchRelevanceFloorDatabaseTest {

    private static final int DIMENSIONS = 1024;

    @Autowired private ObservationRepository repository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private MemoryScopeService scopes;

    private NamedParameterJdbcTemplate jdbc;
    private String projectPath;
    private UUID projectScope;
    private UUID relatedId;
    private UUID unrelatedId;
    private final List<UUID> observationIds = new ArrayList<>();

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> env("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("MEMSCOPE_TEST_DB_PASSWORD", "postgres"));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @BeforeEach
    void seed() {
        jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        projectPath = "/search-floor/" + UUID.randomUUID();
        projectScope = scopes.resolveProjectScope(projectPath);
        relatedId = insert("pagination offset regression", vector(0));
        // A third orthogonal direction, so a query vector aimed at "unrelated" matches
        // neither stored row: cosine 0 against both, not 1.0 against the decoy itself.
        unrelatedId = insert("unrelated telemetry note", vector(2));
    }

    @AfterEach
    void cleanup() {
        if (!observationIds.isEmpty()) {
            jdbc.update("DELETE FROM mem_observations WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", observationIds));
        }
        observationIds.clear();
        if (projectScope != null) {
            jdbc.update("DELETE FROM mem_memory_scopes WHERE id = :id",
                new MapSqlParameterSource("id", projectScope));
        }
    }

    @Test
    @DisplayName("semantic floor drops the unrelated vector and keeps the related one")
    void semanticFloorFiltersUnrelatedRows() {
        List<Object[]> unrelated = repository.hybridSearchScored(
            projectPath, "quantum", vector(1), 0L, 0.5, 20);
        assertThat(unrelated).as("cosine 0.0 must not pass the 0.5 floor").isEmpty();

        List<Object[]> related = repository.hybridSearchScored(
            projectPath, "quantum", vector(0), 0L, 0.5, 20);
        assertThat(ids(related)).containsExactly(relatedId);
        assertThat(((Number) related.get(0)[1]).doubleValue())
            .as("semantic cosine score is returned").isGreaterThanOrEqualTo(0.5);
    }

    @Test
    @DisplayName("tsvector returns no row for an unrelated query and its row for a matching one")
    void textStrategyUsesLexicalFloor() {
        List<Object[]> unrelated = repository.fullTextSearchScored(
            projectPath, "quantum blockchain", 20);
        assertThat(unrelated).isEmpty();

        List<Object[]> related = repository.fullTextSearchScored(projectPath, "pagination", 20);
        assertThat(ids(related)).containsExactly(relatedId);
        assertThat(((Number) related.get(0)[1]).doubleValue())
            .as("ts_rank is returned").isGreaterThan(0.0);
    }

    @Test
    @DisplayName("hybrid and tsvector agree on relevance for the same text query")
    void strategiesAgreeForTextQuery() {
        List<Object[]> hybrid = repository.hybridSearchScored(
            projectPath, "pagination", vector(1), 0L, 0.5, 20);
        List<Object[]> text = repository.fullTextSearchScored(projectPath, "pagination", 20);

        assertThat(ids(hybrid)).as("the text half still contributes a lexically relevant row")
            .containsExactly(relatedId);
        assertThat(ids(hybrid)).isEqualTo(ids(text));
        assertThat(ids(hybrid)).doesNotContain(unrelatedId);
    }

    private UUID insert(String title, String embedding) {
        UUID id = jdbc.queryForObject("""
            INSERT INTO mem_observations (
                content_session_id, platform_source, project_path, type, title, content,
                source, created_at_epoch, memory_scope_id, conversation_id, standalone_kind,
                embedding_1024, embedding_state
            ) VALUES (
                'search-floor', 'claude', :projectPath, 'discovery', :title, :content,
                'manual', 1, :scopeId, NULL, 'manual',
                cast(:embedding as vector), 'ready'
            ) RETURNING id
            """, new MapSqlParameterSource()
                .addValue("projectPath", projectPath)
                .addValue("title", title)
                .addValue("content", title)
                .addValue("scopeId", projectScope)
                .addValue("embedding", embedding), UUID.class);
        observationIds.add(id);
        return id;
    }

    /** 1024-dim unit vector with a single 1.0 at {@code index}. */
    private static String vector(int index) {
        StringBuilder sb = new StringBuilder(DIMENSIONS * 2 + 1).append('[');
        for (int i = 0; i < DIMENSIONS; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(i == index ? "1" : "0");
        }
        return sb.append(']').toString();
    }

    private static List<UUID> ids(List<Object[]> rows) {
        return rows.stream().map(row -> (UUID) row[0]).toList();
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required for search-floor DB tests");
        }
        return value;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
