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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
 *   <li>The lexical strategy returns nothing for an unrelated query and its row for a
 *       matching one; the {@code @@} join is its floor.</li>
 *   <li>A Chinese query matches a Chinese observation — the V24 tokenizer regression guard.</li>
 *   <li>Lexical search works on rows with no embedding, which is the degraded deployment.</li>
 *   <li>Hybrid and lexical agree on which rows are relevant for the same text query.</li>
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
    /** Production default weight of the lexical half (`cortexce.search.lexical-weight`). */
    private static final double LEXICAL_WEIGHT = 2.0;
    /** Production default recency half-life (`cortexce.search.recency-half-life-days`). */
    private static final double RECENCY_HALF_LIFE_DAYS = 7.0;

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
            projectPath, "quantum", vector(1), 0L, 0.5, LEXICAL_WEIGHT,
            RECENCY_HALF_LIFE_DAYS, 20);
        assertThat(unrelated).as("cosine 0.0 must not pass the 0.5 floor").isEmpty();

        List<Object[]> related = repository.hybridSearchScored(
            projectPath, "quantum", vector(0), 0L, 0.5, LEXICAL_WEIGHT,
            RECENCY_HALF_LIFE_DAYS, 20);
        assertThat(ids(related)).containsExactly(relatedId);
        assertThat(((Number) related.get(0)[1]).doubleValue())
            .as("the fused rank score is returned").isGreaterThan(0.0);
    }

    @Test
    @DisplayName("lexical search returns no row for an unrelated query and its row for a matching one")
    void textStrategyUsesLexicalFloor() {
        List<Object[]> unrelated = repository.fullTextSearchScored(
            projectPath, "quantum blockchain", 20);
        assertThat(unrelated).isEmpty();

        List<Object[]> related = repository.fullTextSearchScored(projectPath, "pagination", 20);
        assertThat(ids(related)).containsExactly(relatedId);
        assertThat(((Number) related.get(0)[1]).doubleValue())
            .as("the matched-token count is returned").isGreaterThan(0.0);
    }

    @Test
    @DisplayName("a Chinese query returns at least one row instead of the empty result of the English analyzer")
    void chineseQueryMatchesChineseObservation() {
        UUID chineseId = insertNoEmbedding("中文全文检索修复", "我们把中文按字符二元组切分后重建了索引，中文查询不再恒空。");

        List<Object[]> rows = repository.fullTextSearchScored(projectPath, "中文全文检索", 20);

        assertThat(rows).as("a Chinese query must not be empty for a Chinese observation")
            .isNotEmpty();
        assertThat(ids(rows)).contains(chineseId);
        assertThat(ids(rows).get(0)).as("the Chinese row outranks the English-only row")
            .isEqualTo(chineseId);
    }

    @Test
    @DisplayName("lexical search works with no embedding at all")
    void lexicalSearchWithoutEmbedding() {
        UUID unembedded = insertNoEmbedding("中文降级检索", "没有配置 embedding 时仍然要能按中文命中。");

        List<Object[]> rows = repository.fullTextSearchScored(projectPath, "中文降级检索", 20);

        assertThat(rows).as("the degraded path must not depend on embeddings").isNotEmpty();
        assertThat(ids(rows)).contains(unembedded);
    }

    @Test
    @DisplayName("hybrid and lexical agree on relevance for the same text query")
    void strategiesAgreeForTextQuery() {
        List<Object[]> hybrid = repository.hybridSearchScored(
            projectPath, "pagination", vector(1), 0L, 0.5, LEXICAL_WEIGHT,
            RECENCY_HALF_LIFE_DAYS, 20);
        List<Object[]> text = repository.fullTextSearchScored(projectPath, "pagination", 20);

        assertThat(ids(hybrid)).as("the text half still contributes a lexically relevant row")
            .containsExactly(relatedId);
        assertThat(ids(hybrid)).isEqualTo(ids(text));
        assertThat(ids(hybrid)).doesNotContain(unrelatedId);
    }

    @Test
    @DisplayName("the scope+type index exists and the type-filtered page is index-driven, not a sort over a scan")
    void typeFilterPageUsesTheScopeTypeIndex() throws Exception {
        String definition = jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'mem_observations' "
                + "AND indexname = 'idx_observations_scope_type_created'",
            String.class);
        assertThat(definition)
            .as("V25 must ship a scope-leading composite index")
            .contains("memory_scope_id", "type", "created_at_epoch DESC");

        String plan = explainWithSeqScanDisabled(String.format("""
            SELECT o.* FROM mem_observations o
            WHERE (
                o.memory_scope_id = (
                    SELECT id FROM mem_memory_scopes
                    WHERE scope_type = 'project' AND scope_key = '%1$s' COLLATE "C"
                    LIMIT 1)
                OR ('%1$s' COLLATE "C" = 'manual-memories' COLLATE "C"
                    AND o.memory_scope_id = (
                        SELECT id FROM mem_memory_scopes
                        WHERE scope_type = 'manual_default' AND scope_key = 'manual-default-v1'
                        LIMIT 1))
            )
            AND o.type = 'discovery'
            ORDER BY o.created_at_epoch DESC
            LIMIT 40
            """, projectPath));

        assertThat(plan)
            .as("the type-filtered page must be satisfied by an index, never a Seq Scan + Sort")
            .contains("Index")
            .doesNotContain("Seq Scan");
    }

    @Test
    @DisplayName("the recency weighting ranks the newer duplicate ahead of the older copy")
    void recencyWeightingRanksNewerDuplicateFirst() {
        long now = System.currentTimeMillis();
        UUID olderId = insert("retention window tuning", vector(3), now - 30L * 86_400_000L);
        UUID newerId = insert("retention window tuning", vector(3), now);

        List<Object[]> rows = repository.hybridSearchScored(
            projectPath, "retention window tuning", vector(3), 0L, 0.5, LEXICAL_WEIGHT,
            RECENCY_HALF_LIFE_DAYS, 20);

        assertThat(ids(rows)).as("the newest copy wins the tie")
            .containsExactly(newerId, olderId);
        assertThat(score(rows, newerId))
            .as("30 days of age at a 7-day half-life must cost the older copy its rank")
            .isGreaterThan(score(rows, olderId));
    }

    private static double score(List<Object[]> rows, UUID id) {
        return rows.stream()
            .filter(row -> id.equals(row[0]))
            .map(row -> ((Number) row[1]).doubleValue())
            .findFirst()
            .orElseThrow();
    }

    /**
     * Run {@code EXPLAIN} with sequential scans disabled so the assertion is about
     * "an index can serve this predicate", not about the planner's cost choice on a
     * two-row fixture. The setting is local to a transaction that is rolled back.
     */
    private String explainWithSeqScanDisabled(String sql) throws Exception {
        StringBuilder plan = new StringBuilder();
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL enable_seqscan = off");
                try (ResultSet rows = statement.executeQuery("EXPLAIN " + sql)) {
                    while (rows.next()) {
                        plan.append(rows.getString(1)).append('\n');
                    }
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
        return plan.toString();
    }

    /** Insert a row with text and a zero vector at {@code index} (semantic tests). */
    private UUID insert(String title, String embedding) {
        return insert(title, embedding, System.currentTimeMillis());
    }

    /** Insert a row observed at {@code epoch}; recency reranking reads that timestamp. */
    private UUID insert(String title, String embedding, long epoch) {
        UUID id = jdbc.queryForObject("""
            INSERT INTO mem_observations (
                content_session_id, platform_source, project_path, type, title, content,
                source, created_at_epoch, memory_scope_id, conversation_id, standalone_kind,
                embedding_1024, embedding_state
            ) VALUES (
                'search-floor', 'claude', :projectPath, 'discovery', :title, :content,
                'manual', :epoch, :scopeId, NULL, 'manual',
                cast(:embedding as vector), 'ready'
            ) RETURNING id
            """, new MapSqlParameterSource()
                .addValue("projectPath", projectPath)
                .addValue("title", title)
                .addValue("content", title)
                .addValue("scopeId", projectScope)
                .addValue("epoch", epoch)
                .addValue("embedding", embedding), UUID.class);
        observationIds.add(id);
        return id;
    }

    /** Insert a text-only row, the shape a deployment without an embedding provider writes. */
    private UUID insertNoEmbedding(String title, String content) {
        UUID id = jdbc.queryForObject("""
            INSERT INTO mem_observations (
                content_session_id, platform_source, project_path, type, title, content,
                source, created_at_epoch, memory_scope_id, conversation_id, standalone_kind,
                embedding_state
            ) VALUES (
                'search-floor', 'claude', :projectPath, 'discovery', :title, :content,
                'manual', :epoch, :scopeId, NULL, 'manual', 'skipped'
            ) RETURNING id
            """, new MapSqlParameterSource()
                .addValue("projectPath", projectPath)
                .addValue("title", title)
                .addValue("content", content)
                .addValue("epoch", System.currentTimeMillis())
                .addValue("scopeId", projectScope), UUID.class);
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
