package com.ablueforce.cortexce.offline;

import com.ablueforce.cortexce.config.SpringAiConfig;
import com.ablueforce.cortexce.controller.ViewerController;
import com.ablueforce.cortexce.dto.ApiResponses;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.event.MemoryRefineEventPublisher;
import com.ablueforce.cortexce.event.PendingMessageEventPublisher;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import com.ablueforce.cortexce.service.AgentService;
import com.ablueforce.cortexce.service.ProcessingHeartbeat;
import com.ablueforce.cortexce.service.ContextCacheService;
import com.ablueforce.cortexce.service.ConversationWriteService;
import com.ablueforce.cortexce.service.EmbeddingService;
import com.ablueforce.cortexce.service.EmbeddingBackfillService;
import com.ablueforce.cortexce.service.ExtractionValidationService;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins how the real observation and summary workers behave when the model provider
 * fails: 401/403, 429, timeout, unusable content, and a blank answer.
 *
 * <p>What "correct" means here, and why these assertions exist:</p>
 * <ol>
 *   <li><b>Observable.</b> A failure must leave a durable trace — the queue row reaches
 *       {@code failed}, carries {@code retry_count} and {@code failed_at_epoch}. It must
 *       never look {@code processed}.</li>
 *   <li><b>Bounded.</b> Transient failures (429/timeout) get the configured budget and
 *       then stop. Auth failures and deterministic unusable answers fail on the first
 *       attempt instead of burning three identical retries.</li>
 *   <li><b>No pollution.</b> A failed row must not block the next normal ingest: the
 *       dedup query excludes {@code failed}, and the conversation stays {@code active}.</li>
 * </ol>
 *
 * <p>None of the tests sleep or await a scheduler: the worker entry points are called
 * synchronously, so the queue state is asserted immediately after each attempt.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    ControllableAiTestModels.class,
    SpringAiConfig.class,
    AgentService.class,
    ProcessingHeartbeat.class,
    SessionManagementService.class,
    MemoryScopeService.class,
    ConversationWriteService.class,
    ExtractionValidationService.class,
    TemplateService.class,
    TokenService.class,
    ModeService.class,
    LlmService.class,
    EmbeddingService.class,
    EmbeddingBackfillService.class,
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
class LlmProviderFailureTest {

    private static final int CONFIGURED_MAX_RETRIES = 3;

    @Autowired private ControllableAiTestModels.MutableChatModel chatModel;
    @Autowired private ControllableAiTestModels.MutableEmbeddingModel embeddingModel;
    @Autowired private AgentService agentService;
    @Autowired private SessionManagementService sessionManagementService;
    @Autowired private SummaryJobService summaryJobService;
    @Autowired private SummaryGenerationService summaryGenerationService;
    @Autowired private SearchService searchService;
    @Autowired private EmbeddingService embeddingService;
    @Autowired private EmbeddingBackfillService embeddingBackfillService;
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
        registry.add("claudemem.stale-message.recovery-interval-ms", () -> "86400000");
        registry.add("app.memory.refine-enabled", () -> "false");
        registry.add("claudemem.max-retries", () -> String.valueOf(CONFIGURED_MAX_RETRIES));
        registry.add("claudemem.embedding.backfill-interval-ms", () -> "86400000");
    }

    @BeforeEach
    void resetDoubles() {
        chatModel.respondWith(OfflineAiTestModels.ScriptedChatModel.observationXml());
        embeddingModel.succeed();
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

    // ------------------------------------------------------------------
    // Observation chain
    // ------------------------------------------------------------------

    static Stream<Arguments> terminalProviderFailures() {
        return Stream.of(
            Arguments.of("401 invalid key", new IllegalStateException(
                "401 Unauthorized: invalid_api_key supplied")),
            Arguments.of("403 forbidden", new IllegalStateException(
                "403 Forbidden: PERMISSION_DENIED")));
    }

    @ParameterizedTest(name = "observation: {0} is terminal, recorded, and not retried")
    @MethodSource("terminalProviderFailures")
    void terminalProviderFailureMarksObservationFailedWithoutRetry(
        String label, RuntimeException failure) {

        Fixture fixture = newConversation("terminal");
        chatModel.failWith(failure);
        UUID pendingId = enqueue(fixture, "echo terminal");

        agentService.processPendingMessage(pendingId);

        PendingMessageEntity row = pendingMessageRepository.findById(pendingId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo("failed");
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getFailedAtEpoch()).isNotNull();
        assertThat(row.getCompletedAtEpoch()).isNotNull();
        assertThat(row.getStartedProcessingAtEpoch()).isNull();
        assertThat(observationRepository.findByConversationIdOrderByCreatedAtEpochAsc(
            fixture.session().getId())).isEmpty();
        assertThat(sessionRepository.findById(fixture.session().getId()).orElseThrow().getStatus())
            .isEqualTo("active");
    }

    static Stream<Arguments> transientProviderFailures() {
        return Stream.of(
            Arguments.of("429 rate limit", new IllegalStateException(
                "429 Too Many Requests: rate limit exceeded")),
            // Real HTTP clients surface a timeout wrapped in their own runtime exception,
            // so the cause chain — not the wrapper's class name — has to drive the classification.
            Arguments.of("socket timeout", new RuntimeException(
                "I/O error on POST request", new SocketTimeoutException("read timed out")))
        );
    }

    @ParameterizedTest(name = "observation: {0} is retried to the configured bound, then fails")
    @MethodSource("transientProviderFailures")
    void transientProviderFailureIsRetriedUpToConfiguredBound(
        String label, RuntimeException failure) {

        Fixture fixture = newConversation("transient");
        chatModel.failWith(failure);
        UUID pendingId = enqueue(fixture, "echo transient");

        agentService.processPendingMessage(pendingId);
        PendingMessageEntity afterFirst = pendingMessageRepository.findById(pendingId).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo("pending");
        assertThat(afterFirst.getRetryCount()).isEqualTo(1);
        assertThat(afterFirst.getFailedAtEpoch()).isNull();

        agentService.processPendingMessage(pendingId);
        PendingMessageEntity afterSecond = pendingMessageRepository.findById(pendingId).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo("pending");
        assertThat(afterSecond.getRetryCount()).isEqualTo(2);

        agentService.processPendingMessage(pendingId);
        PendingMessageEntity afterThird = pendingMessageRepository.findById(pendingId).orElseThrow();
        assertThat(afterThird.getStatus()).isEqualTo("failed");
        assertThat(afterThird.getRetryCount()).isEqualTo(CONFIGURED_MAX_RETRIES);
        assertThat(afterThird.getFailedAtEpoch()).isNotNull();
        assertThat(afterThird.getCompletedAtEpoch()).isNotNull();

        // The retry loop stops here: a fourth call must not claim the terminal row.
        agentService.processPendingMessage(pendingId);
        PendingMessageEntity afterFourth = pendingMessageRepository.findById(pendingId).orElseThrow();
        assertThat(afterFourth.getStatus()).isEqualTo("failed");
        assertThat(afterFourth.getRetryCount()).isEqualTo(CONFIGURED_MAX_RETRIES);
        assertThat(observationRepository.findByConversationIdOrderByCreatedAtEpochAsc(
            fixture.session().getId())).isEmpty();
    }

    @Test
    @DisplayName("observation: unparseable answer is terminal and recorded")
    void unparseableAnswerIsTerminal() {
        Fixture fixture = newConversation("unparseable");
        chatModel.respondWith("I cannot help with that. No XML here.");

        UUID pendingId = enqueue(fixture, "echo unparseable");
        agentService.processPendingMessage(pendingId);

        PendingMessageEntity row = pendingMessageRepository.findById(pendingId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo("failed");
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getFailedAtEpoch()).isNotNull();
        assertThat(observationRepository.findByConversationIdOrderByCreatedAtEpochAsc(
            fixture.session().getId())).isEmpty();
    }

    @Test
    @DisplayName("observation: blank answer is terminal and recorded")
    void blankAnswerIsTerminal() {
        Fixture fixture = newConversation("blank");
        chatModel.respondWith("");

        UUID pendingId = enqueue(fixture, "echo blank");
        agentService.processPendingMessage(pendingId);

        PendingMessageEntity row = pendingMessageRepository.findById(pendingId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo("failed");
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getFailedAtEpoch()).isNotNull();
        assertThat(observationRepository.findByConversationIdOrderByCreatedAtEpochAsc(
            fixture.session().getId())).isEmpty();
    }

    @Test
    @DisplayName("a failed row does not block a later normal ingest of the same tool call")
    void failedObservationDoesNotBlockLaterIngest() {
        Fixture fixture = newConversation("recovery");
        chatModel.failWith(new IllegalStateException("401 Unauthorized: invalid_api_key"));

        UUID failedId = enqueue(fixture, "echo recover");
        agentService.processPendingMessage(failedId);
        assertThat(pendingMessageRepository.findById(failedId).orElseThrow().getStatus())
            .isEqualTo("failed");

        // Same tool + same input: the dedup query excludes failed rows, so this must be
        // admitted rather than silently ignored.
        chatModel.respondWith(OfflineAiTestModels.ScriptedChatModel.observationXml());
        UUID secondId = enqueue(fixture, "echo recover");
        agentService.processPendingMessage(secondId);

        PendingMessageEntity second = pendingMessageRepository.findById(secondId).orElseThrow();
        assertThat(second.getStatus()).isEqualTo("processed");
        assertThat(second.getRetryCount()).isEqualTo(0);
        assertThat(observationRepository.findByConversationIdOrderByCreatedAtEpochAsc(
            fixture.session().getId()))
            .extracting(ObservationEntity::getTitle)
            .containsExactly(OfflineAiTestModels.OBSERVATION_TITLE);
        assertThat(sessionRepository.findById(fixture.session().getId()).orElseThrow().getStatus())
            .isEqualTo("active");
    }

    // ------------------------------------------------------------------
    // Summary chain
    // ------------------------------------------------------------------

    @Test
    @DisplayName("summary: 401 is terminal, leaves the job failed, and completes the session")
    void summaryAuthFailureIsTerminal() {
        Fixture fixture = newConversation("summary-401");
        createObservation(fixture);
        chatModel.failWith(new IllegalStateException("401 Unauthorized: invalid_api_key"));

        PendingMessageEntity job = summaryJobService.enqueueSessionSummaryJob(
            fixture.session().getId(), "The session ended.");
        summaryGenerationService.processSummaryJob(job.getId());

        PendingMessageEntity failed = pendingMessageRepository.findById(job.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(SummaryJobService.STATUS_FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getFailedAtEpoch()).isNotNull();
        assertThat(failed.getResultSummaryId()).isNull();
        assertThat(summaryRepository.findByConversationId(fixture.session().getId())).isEmpty();
        assertThat(sessionRepository.findById(fixture.session().getId()).orElseThrow().getStatus())
            .isEqualTo("completed");

        // A terminal job must not be re-claimed by a later worker event.
        summaryGenerationService.processSummaryJob(job.getId());
        assertThat(pendingMessageRepository.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(SummaryJobService.STATUS_FAILED);
    }

    @Test
    @DisplayName("summary: 429 is retried exactly three times, then fails")
    void summaryRateLimitIsRetriedBounded() {
        Fixture fixture = newConversation("summary-429");
        createObservation(fixture);
        chatModel.failWith(new IllegalStateException(
            "429 Too Many Requests: rate limit exceeded"));

        PendingMessageEntity job = summaryJobService.enqueueSessionSummaryJob(
            fixture.session().getId(), "The session ended.");

        summaryGenerationService.processSummaryJob(job.getId());
        assertThat(pendingMessageRepository.findById(job.getId()).orElseThrow())
            .satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo(SummaryJobService.STATUS_PENDING);
                assertThat(row.getRetryCount()).isEqualTo(1);
                assertThat(row.getFailedAtEpoch()).isNull();
            });

        summaryGenerationService.processSummaryJob(job.getId());
        assertThat(pendingMessageRepository.findById(job.getId()).orElseThrow())
            .satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo(SummaryJobService.STATUS_PENDING);
                assertThat(row.getRetryCount()).isEqualTo(2);
            });

        summaryGenerationService.processSummaryJob(job.getId());
        PendingMessageEntity failed = pendingMessageRepository.findById(job.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(SummaryJobService.STATUS_FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(CONFIGURED_MAX_RETRIES);
        assertThat(failed.getFailedAtEpoch()).isNotNull();
        assertThat(summaryRepository.findByConversationId(fixture.session().getId())).isEmpty();
        assertThat(sessionRepository.findById(fixture.session().getId()).orElseThrow().getStatus())
            .isEqualTo("completed");
    }

    static Stream<Arguments> unusableSummaryAnswers() {
        return Stream.of(
            Arguments.of("unparseable", "This answer contains no summary block."),
            Arguments.of("blank", "")
        );
    }

    @ParameterizedTest(name = "summary: {0} answer is terminal, not retried")
    @MethodSource("unusableSummaryAnswers")
    void unusableSummaryAnswerIsTerminal(String label, String answer) {
        Fixture fixture = newConversation("summary-" + label);
        createObservation(fixture);
        chatModel.respondWith(answer);

        PendingMessageEntity job = summaryJobService.enqueueSessionSummaryJob(
            fixture.session().getId(), "The session ended.");
        summaryGenerationService.processSummaryJob(job.getId());

        PendingMessageEntity failed = pendingMessageRepository.findById(job.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(SummaryJobService.STATUS_FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getFailedAtEpoch()).isNotNull();
        assertThat(summaryRepository.findByConversationId(fixture.session().getId())).isEmpty();
        assertThat(sessionRepository.findById(fixture.session().getId()).orElseThrow().getStatus())
            .isEqualTo("completed");
    }

    // ------------------------------------------------------------------
    // Embedding / search chain
    // ------------------------------------------------------------------

    @Test
    @DisplayName("embedding 401 falls back to text search without a 500, then recovers")
    void embeddingFailureFallsBackToTextSearchAndRecovers() {
        Fixture fixture = newConversation("search-401");
        createObservation(fixture);

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

        embeddingModel.failWith(new IllegalStateException("401 Unauthorized: invalid_api_key"));
        ResponseEntity<?> degraded = controller.search(
            fixture.projectPath(), "qzxvbnmpplkjh", null, null, null, 20, 0, null);
        ApiResponses.SearchResponse degradedBody = (ApiResponses.SearchResponse) degraded.getBody();

        assertThat(degraded.getStatusCode().value()).isEqualTo(200);
        assertThat(degradedBody).isNotNull();
        assertThat(degradedBody.strategy()).isEqualTo("tsvector");
        // A token that exists nowhere: the text half cannot find it, so an empty
        // result proves the request degraded instead of pretending to succeed.
        assertThat(degradedBody.observations()).isEmpty();

        embeddingModel.succeed();
        ResponseEntity<?> recovered = controller.search(
            fixture.projectPath(), "qzxvbnmpplkjh", null, null, null, 20, 0, null);
        ApiResponses.SearchResponse recoveredBody = (ApiResponses.SearchResponse) recovered.getBody();

        assertThat(recovered.getStatusCode().value()).isEqualTo(200);
        assertThat(recoveredBody).isNotNull();
        assertThat(recoveredBody.strategy()).isEqualTo("hybrid");
        assertThat(recoveredBody.fellBack()).isFalse();
        // The similarity floor still applies on the hybrid path: a token that exists nowhere
        // must not resurrect the only row in the project just because it is the only candidate.
        assertThat(recoveredBody.observations()).isEmpty();

        // A related query still returns the row through the text half of the hybrid search.
        ResponseEntity<?> related = controller.search(
            fixture.projectPath(), "offline deterministic observation", null, null, null, 20, 0, null);
        ApiResponses.SearchResponse relatedBody = (ApiResponses.SearchResponse) related.getBody();
        assertThat(relatedBody).isNotNull();
        assertThat(relatedBody.observations()).extracting(ObservationEntity::getTitle)
            .contains(OfflineAiTestModels.OBSERVATION_TITLE);
    }

    @Test
    @DisplayName("embedding failure is visible on the row and repaired by the backfill sweep")
    void embeddingFailureIsRecordedAndRepairedByBackfill() {
        Fixture fixture = newConversation("embed-backfill");
        embeddingModel.failWith(new IllegalStateException("503 Service Unavailable"));

        UUID pendingId = enqueue(fixture, "echo embed");
        agentService.processPendingMessage(pendingId);

        // The observation itself still lands: an embedding outage must not lose the memory.
        assertThat(pendingMessageRepository.findById(pendingId).orElseThrow().getStatus())
            .isEqualTo("processed");
        ObservationEntity observation = observationRepository
            .findByConversationIdOrderByCreatedAtEpochAsc(fixture.session().getId()).get(0);

        // ...but the outage is now on the row instead of only in a log line.
        assertThat(observation.getEmbeddingState()).isEqualTo(EmbeddingService.STATE_FAILED);
        assertThat(observation.getEmbeddingError()).isNotBlank();
        assertThat(observation.getEmbedding1024()).isNull();
        assertThat(observation.getEmbeddingModelId()).isNull();

        embeddingModel.succeed();
        assertThat(embeddingBackfillService.backfill(10))
            .as("the sweep must find at least the row that just failed")
            .isGreaterThanOrEqualTo(1);

        ObservationEntity repaired = observationRepository.findById(observation.getId()).orElseThrow();
        assertThat(repaired.getEmbeddingState()).isEqualTo(EmbeddingService.STATE_READY);
        assertThat(repaired.getEmbeddingError()).isNull();
        assertThat(repaired.getEmbedding1024()).isNotNull().hasSize(1024);
        assertThat(repaired.getEmbeddingModelId()).isNotBlank();
    }

    @Test
    @DisplayName("a row with no text to embed is marked skipped and never re-scanned")
    void unembeddableRowIsSkippedNotRetriedForever() {
        Fixture fixture = newConversation("embed-skip");
        createObservation(fixture);
        ObservationEntity observation = observationRepository
            .findByConversationIdOrderByCreatedAtEpochAsc(fixture.session().getId()).get(0);

        // Strip everything the embedder would consume and forget the previous vector.
        observation.setTitle(" ");
        observation.setContent(null);
        observation.setFacts(List.of());
        observation.setEmbedding768(null);
        observation.setEmbedding1024(null);
        observation.setEmbedding1536(null);
        observation.setEmbeddingModelId(null);
        observation.setEmbeddingState(null);
        observationRepository.saveAndFlush(observation);

        assertThat(embeddingBackfillService.repair(observation)).isFalse();
        assertThat(observation.getEmbeddingState()).isEqualTo(EmbeddingService.STATE_SKIPPED);

        assertThat(observationRepository.findMissingEmbeddings(10))
            .as("skipped rows carry no text, so they must leave the backlog")
            .noneMatch(row -> row.getId().equals(observation.getId()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private UUID enqueue(Fixture fixture, String toolInput) {
        return agentService.enqueueToolUse(
            fixture.session().getId(), fixture.session().getContentSessionId(),
            // The offline stand-in emits facts "fact-one"/"fact-two"; quoting them in the trace
            // keeps the trace-grounding guard satisfied on the success paths of this test.
            "Bash", toolInput + " fact-one fact-two", "fact-one fact-two offline-output",
            fixture.projectPath(), 1).orElseThrow();
    }

    private void createObservation(Fixture fixture) {
        UUID pendingId = enqueue(fixture, "echo seed observation");
        agentService.processPendingMessage(pendingId);
        assertThat(observationRepository.findByConversationIdOrderByCreatedAtEpochAsc(
            fixture.session().getId())).hasSize(1);
    }

    private Fixture newConversation(String label) {
        String contentSessionId = "llm-failure-" + label + "-" + UUID.randomUUID();
        String projectPath = "/offline-ai/" + UUID.randomUUID();
        SessionEntity session = sessionManagementService.getOrCreate(
            ExternalSessionRef.of("claude", contentSessionId), projectPath,
            "llm provider failure test");
        conversationIds.add(session.getId());
        projectPaths.add(projectPath);
        return new Fixture(session, projectPath);
    }

    private record Fixture(SessionEntity session, String projectPath) {}

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required for LLM failure tests");
        }
        return value;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
