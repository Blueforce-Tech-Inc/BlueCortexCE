package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.offline.ControllableAiTestModels;
import com.ablueforce.cortexce.service.EmbeddingBackfillService;
import com.ablueforce.cortexce.service.EmbeddingService;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Adversarial checks for the embedding repair sweep against a real PostgreSQL instance.
 *
 * <p>The sweep and a worker can touch the same row, and two sweeps can overlap. The
 * invariants this class pins:</p>
 * <ol>
 *   <li>A repair writes only the vector columns: a body edit committed between the
 *       backlog read and the repair survives (the old blanket {@code save()} reverted it).</li>
 *   <li>Whenever another writer already moved the row out of the missing state — the
 *       worker embedded it, or an earlier sweep did — the repair writes nothing, spends
 *       no provider call, and keeps the winner's vector.</li>
 *   <li>A repeated repair is idempotent: the second call returns {@code false} and does
 *       not call the provider again.</li>
 *   <li>The conditional UPDATE itself is the claim: two writers holding stale copies,
 *       both past the pre-check, produce exactly one stored vector.</li>
 *   <li>The skip outcome is sticky and never overwrites a completed row.</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ControllableAiTestModels.class, EmbeddingService.class, MemoryScopeService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(
    named = "MEMSCOPE_TEST_DB_URL",
    matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?"
)
class EmbeddingBackfillConcurrencyDatabaseTest {

    private static final int DIMENSIONS = 1024;

    @Autowired private ObservationRepository repository;
    @Autowired private EmbeddingService embeddingService;
    @Autowired private ControllableAiTestModels.MutableEmbeddingModel embeddingModel;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MemoryScopeService scopes;

    private NamedParameterJdbcTemplate jdbc;
    private EmbeddingBackfillService backfillService;
    private String projectPath;
    private UUID projectScope;
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
    void setUp() {
        jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        backfillService = new EmbeddingBackfillService(repository, embeddingService, true);
        projectPath = "/embedding-claim/" + UUID.randomUUID();
        projectScope = scopes.resolveProjectScope(projectPath);
        embeddingModel.succeed();
        embeddingModel.resetEmbedCalls();
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
    @DisplayName("a worker body edit committed after the backlog read survives the repair")
    void repairDoesNotRevertConcurrentWorkerUpdate() {
        UUID id = insertFailed("stale title", "stale body");
        ObservationEntity stale = repository.findMissingEmbeddings(10).stream()
            .filter(row -> row.getId().equals(id))
            .findFirst()
            .orElseThrow();

        // The worker edits the same row between the sweep's read and its write.
        jdbc.update("""
            UPDATE mem_observations
            SET content = :content, facts = cast(:facts as jsonb)
            WHERE id = :id
            """, new MapSqlParameterSource()
            .addValue("content", "worker body")
            .addValue("facts", "[\"worker fact\"]")
            .addValue("id", id));

        assertThat(backfillService.repair(stale)).isTrue();

        Row row = read(id);
        assertThat(row.content()).as("the repair must not write the body it read earlier")
            .isEqualTo("worker body");
        assertThat(row.facts()).contains("worker fact");
        assertThat(row.state()).isEqualTo(EmbeddingService.STATE_READY);
        assertThat(row.embedding()).isNotNull();
    }

    @Test
    @DisplayName("a worker that embedded the row first wins: the repair writes nothing and skips the provider")
    void repairLosesToWorkerWhoAlreadyEmbedded() {
        UUID id = insertFailed("worker title", "worker body");
        ObservationEntity stale = repository.findMissingEmbeddings(10).stream()
            .filter(row -> row.getId().equals(id))
            .findFirst()
            .orElseThrow();

        // The worker completed the embedding itself while the sweep was still working.
        jdbc.update("""
            UPDATE mem_observations
            SET embedding_1024 = cast(:vector as vector),
                embedding_state = 'ready', embedding_error = NULL,
                embedding_model_id = 'worker-model'
            WHERE id = :id
            """, new MapSqlParameterSource()
            .addValue("vector", unitVector(0))
            .addValue("id", id));

        assertThat(backfillService.repair(stale)).isFalse();

        Row row = read(id);
        assertThat(row.state()).isEqualTo(EmbeddingService.STATE_READY);
        assertThat(row.modelId()).isEqualTo("worker-model");
        assertThat(vectorEquals(id, unitVector(0)))
            .as("the worker's vector is kept")
            .isTrue();
        assertThat(embeddingModel.embedCalls())
            .as("losing the claim must not spend a provider call")
            .isZero();
    }

    @Test
    @DisplayName("repairing twice writes once and calls the provider once")
    void repairIsIdempotent() {
        UUID id = insertFailed("idempotent title", "idempotent body");
        ObservationEntity first = repository.findMissingEmbeddings(10).stream()
            .filter(row -> row.getId().equals(id))
            .findFirst()
            .orElseThrow();
        ObservationEntity secondCopy = repository.findMissingEmbeddings(10).stream()
            .filter(row -> row.getId().equals(id))
            .findFirst()
            .orElseThrow();

        assertThat(backfillService.repair(first)).isTrue();
        assertThat(backfillService.repair(secondCopy)).isFalse();

        Row row = read(id);
        assertThat(row.state()).isEqualTo(EmbeddingService.STATE_READY);
        assertThat(row.embedding()).isNotEmpty();
        assertThat(embeddingModel.embedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("two writers holding stale copies produce exactly one vector: the first claim wins")
    void conditionalUpdateIsTheClaim() {
        UUID id = insertFailed("race title", "race body");
        String first = unitVector(0);
        String second = unitVector(1);

        int firstWrite = repository.applyEmbedding1024IfMissing(id, first, "sweep-a");

        // The second writer passed the same pre-check before the first committed, so it
        // reaches the UPDATE with a different vector. It must lose without overwriting.
        int secondWrite = repository.applyEmbedding1024IfMissing(id, second, "sweep-b");

        assertThat(firstWrite).isEqualTo(1);
        assertThat(secondWrite).isZero();
        Row row = read(id);
        assertThat(vectorEquals(id, first)).isTrue();
        assertThat(vectorEquals(id, second)).isFalse();
        assertThat(row.modelId()).isEqualTo("sweep-a");
    }

    @Test
    @DisplayName("skip is sticky and never overwrites a ready row")
    void skipIsSticky() {
        UUID id = insertFailed("skip title", "skip body");

        assertThat(repository.markEmbeddingOutcomeIfMissing(
            id, EmbeddingService.STATE_SKIPPED, null)).isEqualTo(1);
        assertThat(repository.markEmbeddingOutcomeIfMissing(
            id, EmbeddingService.STATE_SKIPPED, null))
            .as("a second skip is a no-op")
            .isZero();
        assertThat(repository.findMissingEmbeddings(10))
            .noneMatch(row -> row.getId().equals(id));

        UUID ready = insertFailed("ready title", "ready body");
        repository.applyEmbedding1024IfMissing(ready, unitVector(0), "model");
        assertThat(repository.markEmbeddingOutcomeIfMissing(
            ready, EmbeddingService.STATE_SKIPPED, null))
            .as("a skip must not touch an embedded row")
            .isZero();
    }

    @Test
    @DisplayName("a failed repair records only state and error, then the next sweep can retry")
    void failureIsRecordedWithoutTouchingTheBody() {
        UUID id = insertFailed("failure title", "failure body");
        embeddingModel.failWith(new IllegalStateException("503 Service Unavailable"));
        ObservationEntity stale = repository.findMissingEmbeddings(10).stream()
            .filter(row -> row.getId().equals(id))
            .findFirst()
            .orElseThrow();

        assertThat(backfillService.repair(stale)).isFalse();

        Row failed = read(id);
        assertThat(failed.state()).isEqualTo(EmbeddingService.STATE_FAILED);
        assertThat(failed.error()).isNotBlank();
        assertThat(failed.content()).isEqualTo("failure body");
        assertThat(failed.embedding()).isNull();
        assertThat(repository.countEmbeddingMissingById(id)).isEqualTo(1);

        embeddingModel.succeed();
        assertThat(backfillService.repair(stale)).isTrue();
        assertThat(read(id).state()).isEqualTo(EmbeddingService.STATE_READY);
        assertThat(embeddingModel.embedCalls()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private UUID insertFailed(String title, String content) {
        UUID id = jdbc.queryForObject("""
            INSERT INTO mem_observations (
                content_session_id, platform_source, project_path, type, title, content,
                facts, source, created_at_epoch, memory_scope_id, conversation_id,
                standalone_kind, embedding_state
            ) VALUES (
                'embedding-claim', 'claude', :projectPath, 'discovery', :title, :content,
                cast('[]' as jsonb), 'manual', 1, :scopeId, NULL, 'manual', 'failed'
            ) RETURNING id
            """, new MapSqlParameterSource()
            .addValue("projectPath", projectPath)
            .addValue("title", title)
            .addValue("content", content)
            .addValue("scopeId", projectScope), UUID.class);
        observationIds.add(id);
        return id;
    }

    private Row read(UUID id) {
        return jdbc.queryForObject("""
            SELECT content, facts::text AS facts, embedding_state, embedding_error,
                   embedding_model_id, embedding_1024::text AS embedding
            FROM mem_observations WHERE id = :id
            """, new MapSqlParameterSource("id", id), (rs, rowNum) -> new Row(
            rs.getString("content"),
            rs.getString("facts"),
            rs.getString("embedding_state"),
            rs.getString("embedding_error"),
            rs.getString("embedding_model_id"),
            rs.getString("embedding")));
    }

    /** Compare the stored vector to a literal without depending on pgvector's text format. */
    private boolean vectorEquals(UUID id, String literal) {
        Boolean equal = jdbc.queryForObject("""
            SELECT embedding_1024 = cast(:literal as vector)
            FROM mem_observations WHERE id = :id
            """, new MapSqlParameterSource()
            .addValue("literal", literal)
            .addValue("id", id), Boolean.class);
        return Boolean.TRUE.equals(equal);
    }

    /** 1024-dim unit vector with a single 1.0 at {@code index}. */
    private static String unitVector(int index) {
        StringBuilder sb = new StringBuilder(DIMENSIONS * 2 + 1).append('[');
        for (int i = 0; i < DIMENSIONS; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(i == index ? "1" : "0");
        }
        return sb.append(']').toString();
    }

    private record Row(String content, String facts, String state, String error,
                       String modelId, String embedding) {
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required for embedding claim DB tests");
        }
        return value;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
