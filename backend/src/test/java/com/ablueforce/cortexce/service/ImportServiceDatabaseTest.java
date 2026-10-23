package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.event.PendingMessageEventPublisher;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ImportService.class, SessionManagementService.class, MemoryScopeService.class,
    ConversationWriteService.class, StandaloneObservationImportLock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(
    named = "MEMSCOPE_TEST_DB_URL",
    matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?"
)
class ImportServiceDatabaseTest {

    private static final long CREATED_AT = 1_798_070_400_000L;

    @Autowired private ImportService importService;
    @Autowired private SessionManagementService sessionManagementService;
    @Autowired private MemoryScopeService memoryScopeService;
    @Autowired private ObservationRepository observationRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private SSEBroadcaster sseBroadcaster;
    @MockBean private PendingMessageEventPublisher pendingMessageEventPublisher;

    private final List<UUID> createdObservationIds = new ArrayList<>();
    private final List<UUID> createdConversationIds = new ArrayList<>();
    private final List<String> createdProjectPaths = new ArrayList<>();

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> env("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("MEMSCOPE_TEST_DB_PASSWORD", "postgres"));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @BeforeEach
    void requireV20Schema() {
        Integer present = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.tables
            WHERE table_name = 'mem_memory_scopes'
            """, Integer.class);
        assertThat(present).isEqualTo(1);
        String version = jdbc.queryForObject("""
            SELECT version FROM flyway_schema_history
            WHERE success ORDER BY installed_rank DESC LIMIT 1
            """, String.class);
        assertThat(version).isEqualTo("20");
    }

    @AfterEach
    void cleanup() {
        createdObservationIds.forEach(id -> jdbc.update(
            "DELETE FROM mem_observations WHERE id = ?", id));
        createdConversationIds.forEach(id -> jdbc.update(
            "DELETE FROM mem_sessions WHERE id = ?", id));
        createdProjectPaths.forEach(path -> jdbc.update("""
            DELETE FROM mem_memory_scopes
            WHERE scope_type = 'project' AND scope_key = ? COLLATE "C"
            """, path));
        createdObservationIds.clear();
        createdConversationIds.clear();
        createdProjectPaths.clear();
    }

    @Test
    void importsSameExternalIdIndependentlyForDifferentPlatforms() {
        String externalId = "shared-import-" + UUID.randomUUID();
        String projectPath = projectPath();
        SessionEntity claude = createSession("claude", externalId, projectPath);
        SessionEntity codex = createSession("codex", externalId, projectPath);

        var result = importService.importObservations(List.of(
            conversationObservation("claude", externalId, projectPath, "claude row"),
            conversationObservation("codex", externalId, projectPath, "codex row")));

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.duplicates()).isZero();
        assertThat(result.errors()).isZero();
        List<ObservationEntity> rows = result.importedIds().stream()
            .map(UUID::fromString)
            .peek(createdObservationIds::add)
            .map(id -> observationRepository.findById(id).orElseThrow())
            .toList();
        assertThat(rows).extracting(ObservationEntity::getConversationId)
            .containsExactlyInAnyOrder(claude.getId(), codex.getId());
        assertThat(rows).extracting(ObservationEntity::getPlatformSource)
            .containsExactlyInAnyOrder("claude", "codex");
        assertThat(rows).extracting(ObservationEntity::getMemoryScopeId).doesNotContainNull();

        List<ObservationEntity> contextRows = observationRepository.findByTypeAndConcepts(
            projectPath, List.of("discovery"), List.of(), true, 10);
        assertThat(contextRows).hasSize(2);
        assertThat(contextRows).extracting(ObservationEntity::getMemoryScopeType)
            .containsOnly("project");
    }

    @Test
    void importsStandaloneRowsWithoutInventingConversationsAndDeduplicatesOnReimport() {
        var data = standaloneManualObservation();

        var first = importService.importObservation(data);
        var second = importService.importObservations(List.of(data));
        if (first.id() != null) {
            createdObservationIds.add(UUID.fromString(first.id()));
        }

        assertThat(first.imported()).isTrue();
        assertThat(first.isError()).isFalse();
        assertThat(second.duplicates()).isEqualTo(1);
        assertThat(second.errors()).isZero();
        UUID observationId = UUID.fromString(first.id());
        ObservationEntity row = observationRepository.findById(observationId).orElseThrow();
        assertThat(row.getConversationId()).isNull();
        assertThat(row.getStandaloneKind()).isEqualTo("manual");
        assertThat(jdbc.queryForObject("""
            SELECT ms.scope_type FROM mem_memory_scopes ms WHERE ms.id = ?
            """, String.class, row.getMemoryScopeId())).isEqualTo("manual_default");
        assertThat(sessionRepository.findByPlatformSourceAndContentSessionId("claude", "manual-memories"))
            .isEmpty();
    }

    @Test
    void concurrentStandaloneImportsConvergeOnOneObservation() throws Exception {
        var data = standaloneProjectObservation("concurrent-" + UUID.randomUUID());
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ImportService.BulkImportResult>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent import start timed out");
                    }
                    return importService.importObservations(List.of(data));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ImportService.BulkImportResult> results = new ArrayList<>();
            for (Future<ImportService.BulkImportResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            results.stream().flatMap(result -> result.importedIds().stream())
                .map(UUID::fromString).forEach(createdObservationIds::add);

            assertThat(results.stream().mapToInt(ImportService.BulkImportResult::imported).sum())
                .isEqualTo(1);
            assertThat(results.stream().mapToInt(ImportService.BulkImportResult::duplicates).sum())
                .isEqualTo(callers - 1);
            assertThat(results.stream().mapToInt(ImportService.BulkImportResult::errors).sum()).isZero();
            Integer rowCount = jdbc.queryForObject("""
                SELECT count(*) FROM mem_observations
                WHERE standalone_kind = 'manual'
                  AND content_session_id = ?
                  AND title = ?
                  AND created_at_epoch = ?
                """, Integer.class, data.sessionId(), data.title(), data.createdAtEpoch());
            assertThat(rowCount).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void observationIdLookupCanFilterByAuthoritativeScopeWhenDisplayPathDiffers() {
        var result = importService.importObservation(standaloneProjectObservation("scope-filter-" + UUID.randomUUID()));
        assertThat(result.imported()).isTrue();
        UUID observationId = UUID.fromString(result.id());
        createdObservationIds.add(observationId);

        ObservationEntity observation = observationRepository.findById(observationId).orElseThrow();
        UUID ownerScopeId = observation.getMemoryScopeId();
        UUID otherScopeId = memoryScopeService.resolveProjectScope(projectPath());
        jdbc.update("UPDATE mem_observations SET project_path = ? WHERE id = ?", projectPath(), observationId);

        assertThat(observationRepository.findAllByIdInAndMemoryScopeIdIn(
            List.of(observationId), List.of(ownerScopeId)))
            .extracting(ObservationEntity::getId)
            .containsExactly(observationId);
        assertThat(observationRepository.findAllByIdInAndMemoryScopeIdIn(
            List.of(observationId), List.of(otherScopeId))).isEmpty();
    }

    private SessionEntity createSession(String platform, String externalId, String projectPath) {
        SessionEntity session = sessionManagementService.createImported(
            ExternalSessionRef.of(platform, externalId), projectPath, "import test", CREATED_AT,
            null, "completed");
        createdConversationIds.add(session.getId());
        return session;
    }

    private ImportService.ObservationImportData conversationObservation(String platform, String externalId,
                                                                         String projectPath, String title) {
        return new ImportService.ObservationImportData(
            externalId, platform, projectPath, "discovery", title, null, "imported content",
            "[]", "[]", "[]", "[]", 1, CREATED_AT, 0,
            null, null, null, null, null, null, null, null);
    }

    private ImportService.ObservationImportData standaloneManualObservation() {
        return new ImportService.ObservationImportData(
            "manual-memories", "claude", "manual-memories", "discovery", "manual import", null,
            "imported manual content", "[]", "[]", "[]", "[]", 1, CREATED_AT, 0,
            null, null, null, null, "manual", "manual_default", "manual", null);
    }

    private ImportService.ObservationImportData standaloneProjectObservation(String correlationKey) {
        String path = projectPath();
        return new ImportService.ObservationImportData(
            correlationKey, "claude", path, "discovery", "concurrent standalone import", null,
            "imported manual content", "[]", "[]", "[]", "[]", 1, CREATED_AT, 0,
            null, null, null, null, "manual", "project", "manual", null);
    }

    private String projectPath() {
        String path = "/import-db-test/" + UUID.randomUUID();
        createdProjectPaths.add(path);
        return path;
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required for PostgreSQL import tests");
        }
        return value;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
