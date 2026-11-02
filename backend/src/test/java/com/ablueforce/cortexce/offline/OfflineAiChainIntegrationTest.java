package com.ablueforce.cortexce.offline;

import com.ablueforce.cortexce.controller.ViewerController;
import com.ablueforce.cortexce.dto.ApiResponses;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.event.MemoryRefineEventPublisher;
import com.ablueforce.cortexce.event.PendingMessageEventPublisher;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import com.ablueforce.cortexce.service.AgentService;
import com.ablueforce.cortexce.service.ConversationWriteService;
import com.ablueforce.cortexce.service.ContextCacheService;
import com.ablueforce.cortexce.service.EmbeddingService;
import com.ablueforce.cortexce.service.LlmService;
import com.ablueforce.cortexce.service.MemoryScopeService;
import com.ablueforce.cortexce.service.ModeService;
import com.ablueforce.cortexce.service.QualityScorer;
import com.ablueforce.cortexce.service.SSEBroadcaster;
import com.ablueforce.cortexce.service.SearchService;
import com.ablueforce.cortexce.service.SessionManagementService;
import com.ablueforce.cortexce.service.SettingsService;
import com.ablueforce.cortexce.service.SummaryGenerationService;
import com.ablueforce.cortexce.service.SummaryJobService;
import com.ablueforce.cortexce.service.SummaryResultPersistenceService;
import com.ablueforce.cortexce.service.TemplateService;
import com.ablueforce.cortexce.service.TimelineService;
import com.ablueforce.cortexce.service.TokenService;
import com.ablueforce.cortexce.config.SpringAiConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves the three LLM-dependent chains end to end without an API key and
 * without network access.
 *
 * <ol>
 *   <li><b>Observation extraction</b>: {@code enqueueToolUse} →
 *       {@code processPendingMessage} → one {@code mem_observations} row whose
 *       fields and 1024-dim vector come from the offline stand-ins.</li>
 *   <li><b>Session summary</b>: the observation chain → {@code enqueueSessionSummaryJob}
 *       → {@code processSummaryJob} → one {@code mem_summaries} row and a durable
 *       job in its terminal {@code processed} state.</li>
 *   <li><b>Semantic search</b>: {@code ViewerController.search} embeds the query
 *       through the offline {@code EmbeddingModel} and the search reports
 *       {@code hybrid}/{@code fell_back=false}; the same query with no vector
 *       falls back to {@code tsvector} and finds nothing, so the result can only
 *       have come from the vector branch.</li>
 * </ol>
 *
 * <p><b>Determinism.</b> None of these tests sleep or await an async listener.
 * The production {@code @Async} listener is deliberately bypassed: the test calls
 * the two worker entry points ({@code processPendingMessage},
 * {@code processSummaryJob}) synchronously. The queue state is then asserted
 * explicitly ({@code pending → processing → processed}), so there is no
 * "eventually" window to race against. The fake models are pure functions of
 * their inputs, so every run produces byte-identical data.</p>
 *
 * <p>Runs only against the dedicated rehearsal database, exactly like the other
 * DB integration tests; the shared gate in {@code scripts/check-test-results.py}
 * turns a skip into a CI failure.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    OfflineAiTestModels.class,
    SpringAiConfig.class,
    AgentService.class,
    SessionManagementService.class,
    MemoryScopeService.class,
    ConversationWriteService.class,
    TemplateService.class,
    TokenService.class,
    ModeService.class,
    LlmService.class,
    EmbeddingService.class,
    SummaryJobService.class,
    SummaryResultPersistenceService.class,
    SummaryGenerationService.class,
    SearchService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(
    named = "MEMSCOPE_TEST_DB_URL",
    matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?"
)
class OfflineAiChainIntegrationTest {

    private static final String NEVER_PRESENT_TOKEN = "qzxvbnmpplkjh";

    @Autowired private AgentService agentService;
    @Autowired private SessionManagementService sessionManagementService;
    @Autowired private SummaryJobService summaryJobService;
    @Autowired private SummaryGenerationService summaryGenerationService;
    @Autowired private SearchService searchService;
    @Autowired private EmbeddingService embeddingService;
    @Autowired private ObservationRepository observationRepository;
    @Autowired private SummaryRepository summaryRepository;
    @Autowired private PendingMessageRepository pendingMessageRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private UserPromptRepository userPromptRepository;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private SSEBroadcaster sseBroadcaster;
    @MockBean private PendingMessageEventPublisher pendingMessageEventPublisher;
    @MockBean private ContextCacheService contextCacheService;
    @MockBean private MemoryRefineEventPublisher memoryRefineEventPublisher;
    @MockBean private QualityScorer qualityScorer;

    private final List<UUID> conversationIds = new ArrayList<>();
    private final List<String> projectPaths = new ArrayList<>();

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> env("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("MEMSCOPE_TEST_DB_PASSWORD", "postgres"));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // Background maintenance must not touch rows between assertion steps.
        registry.add("claudemem.stale-message.recovery-interval-ms", () -> "86400000");
        registry.add("app.memory.refine-enabled", () -> "false");
    }

    @AfterEach
    void cleanup() {
        for (UUID conversationId : conversationIds) {
            jdbc.update("DELETE FROM mem_summaries WHERE conversation_id = ?", conversationId);
            jdbc.update("DELETE FROM mem_observations WHERE conversation_id = ?", conversationId);
            jdbc.update("DELETE FROM mem_pending_messages WHERE session_db_id = ?", conversationId);
            jdbc.update("DELETE FROM mem_sessions WHERE id = ?", conversationId);
        }
        for (String projectPath : projectPaths) {
            jdbc.update("""
                DELETE FROM mem_memory_scopes
                WHERE scope_type = 'project' AND scope_key = ? COLLATE "C"
                """, projectPath);
        }
        conversationIds.clear();
        projectPaths.clear();
    }

    @Test
    @DisplayName("ingest produces an observation with a deterministic offline embedding")
    void ingestProducesObservation() {
        Fixture fixture = newConversation("observation");
        UUID pendingId = agentService.enqueueToolUse(
            fixture.session().getId(), fixture.session().getContentSessionId(),
            "Bash", "echo offline-observation", "offline-output",
            fixture.projectPath(), 1).orElseThrow();

        agentService.processPendingMessage(pendingId);

        List<ObservationEntity> observations = observationRepository
            .findByConversationIdOrderByCreatedAtEpochAsc(fixture.session().getId());
        assertThat(observations).hasSize(1);
        ObservationEntity observation = observations.get(0);
        assertThat(observation.getTitle()).isEqualTo(OfflineAiTestModels.OBSERVATION_TITLE);
        assertThat(observation.getContent()).isEqualTo(OfflineAiTestModels.OBSERVATION_NARRATIVE);
        assertThat(observation.getFacts()).containsExactly("fact-one", "fact-two");
        assertThat(observation.getConcepts()).containsExactly("offline-testing");
        assertThat(observation.getType()).isEqualTo("discovery");
        assertThat(observation.getEmbedding1024()).hasSize(OfflineAiTestModels.EMBEDDING_DIMENSIONS);
        assertThat(observation.getEmbeddingModelId()).isEqualTo("HashEmbeddingModel");

        PendingMessageEntity queueRow = pendingMessageRepository.findById(pendingId).orElseThrow();
        assertThat(queueRow.getStatus()).isEqualTo("processed");
    }

    @Test
    @DisplayName("session end produces a summary and leaves the durable job processed")
    void sessionEndProducesSummary() {
        Fixture fixture = newConversation("summary");
        UUID pendingId = agentService.enqueueToolUse(
            fixture.session().getId(), fixture.session().getContentSessionId(),
            "Bash", "echo offline-summary", "offline-output",
            fixture.projectPath(), 1).orElseThrow();
        agentService.processPendingMessage(pendingId);

        PendingMessageEntity job = summaryJobService.enqueueSessionSummaryJob(
            fixture.session().getId(), "The offline run completed.");
        summaryGenerationService.processSummaryJob(job.getId());

        List<SummaryEntity> summaries = summaryRepository.findByConversationId(fixture.session().getId());
        assertThat(summaries).hasSize(1);
        SummaryEntity summary = summaries.get(0);
        assertThat(summary.getRequest()).isEqualTo("Offline deterministic summary");
        assertThat(summary.getLearned()).isEqualTo(OfflineAiTestModels.SUMMARY_LEARNED);
        assertThat(summary.getSummaryJobKey())
            .isEqualTo(SummaryJobService.JOB_KEY_SESSION_SUMMARY);

        PendingMessageEntity persistedJob = pendingMessageRepository.findById(job.getId()).orElseThrow();
        assertThat(persistedJob.getStatus()).isEqualTo(SummaryJobService.STATUS_PROCESSED);
        assertThat(persistedJob.getResultSummaryId()).isEqualTo(summary.getId());
        assertThat(persistedJob.getSessionDbId()).isEqualTo(fixture.session().getId());

        SessionEntity completed = sessionRepository.findById(fixture.session().getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("completed");
    }

    @Test
    @DisplayName("query search takes the vector branch instead of the full-text fallback")
    void embeddingAvailableSearchUsesVectorBranch() {
        Fixture fixture = newConversation("search");
        UUID pendingId = agentService.enqueueToolUse(
            fixture.session().getId(), fixture.session().getContentSessionId(),
            "Bash", "echo offline-search", "offline-output",
            fixture.projectPath(), 1).orElseThrow();
        agentService.processPendingMessage(pendingId);
        assertThat(embeddingService.isAvailable()).isTrue();

        ViewerController controller = new ViewerController(
            observationRepository,
            summaryRepository,
            userPromptRepository,
            sessionRepository,
            mock(AgentService.class),
            embeddingService,
            searchService,
            mock(ModeService.class),
            mock(SettingsService.class),
            mock(TimelineService.class),
            sessionManagementService);

        ResponseEntity<ApiResponses.SearchResponse> response = controller.search(
            fixture.projectPath(), NEVER_PRESENT_TOKEN, null, null, null, 20, 0, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ApiResponses.SearchResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.strategy()).isEqualTo("hybrid");
        assertThat(body.fellBack()).isFalse();
        assertThat(body.observations()).extracting(ObservationEntity::getTitle)
            .containsExactly(OfflineAiTestModels.OBSERVATION_TITLE);

        // The same query without a vector has no lexical hit at all. Without this
        // assertion, "found something" could also mean the tsvector branch matched.
        SearchService.SearchResult textOnly = searchService.search(new SearchService.SearchRequest(
            fixture.projectPath(), NEVER_PRESENT_TOKEN, null,
            null, null, null, null, null, 20, 0, null));
        assertThat(textOnly.strategy()).isEqualTo("tsvector");
        assertThat(textOnly.observations()).isEmpty();
    }

    private Fixture newConversation(String label) {
        String contentSessionId = "offline-" + label + "-" + UUID.randomUUID();
        String projectPath = "/offline-ai/" + UUID.randomUUID();
        SessionEntity session = sessionManagementService.getOrCreate(
            ExternalSessionRef.of("claude", contentSessionId), projectPath,
            "offline ai chain test");
        conversationIds.add(session.getId());
        projectPaths.add(projectPath);
        return new Fixture(session, projectPath);
    }

    private record Fixture(SessionEntity session, String projectPath) {}

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required for offline AI chain tests");
        }
        return value;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
