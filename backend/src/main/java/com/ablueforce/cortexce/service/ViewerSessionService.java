package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.identity.PlatformSource;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

@Service
public class ViewerSessionService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SessionRepository sessionRepository;
    private final ObservationRepository observationRepository;
    private final SummaryRepository summaryRepository;
    private final UserPromptRepository userPromptRepository;
    private final AgentService agentService;
    private final SummaryGenerationService summaryGenerationService;
    private final SSEBroadcaster sseBroadcaster;
    private final PendingMessageRepository pendingMessageRepository;

    public ViewerSessionService(NamedParameterJdbcTemplate jdbcTemplate,
                                SessionRepository sessionRepository,
                                ObservationRepository observationRepository,
                                SummaryRepository summaryRepository,
                                UserPromptRepository userPromptRepository,
                                AgentService agentService,
                                SummaryGenerationService summaryGenerationService,
                                PendingMessageRepository pendingMessageRepository,
                                SSEBroadcaster sseBroadcaster) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionRepository = sessionRepository;
        this.observationRepository = observationRepository;
        this.summaryRepository = summaryRepository;
        this.userPromptRepository = userPromptRepository;
        this.agentService = agentService;
        this.summaryGenerationService = summaryGenerationService;
        this.pendingMessageRepository = pendingMessageRepository;
        this.sseBroadcaster = sseBroadcaster;
    }

    @Transactional(readOnly = true)
    public CatalogPage getSessions(String project, String platformSource, int offset, int limit) {
        int pageSize = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("project", optionalFilter(project))
            .addValue("platformSource", optionalFilter(platformSource))
            .addValue("limit", pageSize + 1)
            .addValue("offset", Math.max(0, offset));

        // Item counts are per conversation UUID. Counting by the external id would fold two
        // platforms' rows together as soon as they share one.
        List<CatalogEntry> sessions = jdbcTemplate.query("""
            SELECT s.id,
                   s.content_session_id,
                   s.project_path,
                   COALESCE(s.platform_source, 'claude') AS platform_source,
                   s.started_at_epoch,
                   (SELECT COUNT(*) FROM mem_observations o WHERE o.conversation_id = s.id)
                   + (SELECT COUNT(*) FROM mem_summaries sm WHERE sm.conversation_id = s.id)
                   + (SELECT COUNT(*) FROM mem_user_prompts p WHERE p.conversation_id = s.id) AS item_count
            FROM mem_sessions s
            WHERE s.project_path <> ''
              AND (CAST(:project AS text) IS NULL OR s.project_path = :project)
              AND (CAST(:platformSource AS text) IS NULL
                   OR COALESCE(s.platform_source, 'claude') = :platformSource)
            ORDER BY s.started_at_epoch DESC NULLS LAST, s.id DESC
            LIMIT :limit OFFSET :offset
            """, parameters, (row, rowNumber) -> new CatalogEntry(
                row.getString("content_session_id"),
                row.getString("project_path"),
                row.getString("platform_source"),
                null,
                row.getLong("started_at_epoch"),
                row.getLong("item_count")
            ));

        return new CatalogPage(
            sessions.stream().limit(pageSize).toList(),
            sessions.size() > pageSize
        );
    }

    /**
     * Delete one Conversation and everything it owns.
     *
     * <p>Scope of the deletion is the Conversation UUID. Scope rows, standalone observations
     * (manual/extraction/dlq) and other Conversations are never touched — including another
     * platform's Conversation that happens to share the external id.
     *
     * <p>Ordering follows the schema: the parent row is locked first so a concurrent enqueue
     * cannot slip work in after the "is it busy" check, then the conversation-owned children
     * are removed. Feedback rows that merely *reference* this conversation as their trigger
     * are preserved and have that reference cleared, because they describe an observation
     * that still exists elsewhere.
     */
    @Transactional
    public void deleteSession(String platformSource, String contentSessionId) {
        String source = PlatformSource.normalize(platformSource);

        // Resolve through the (platform, external id) pair, then lock by UUID.
        SessionEntity session = sessionRepository.findByPlatformSourceAndContentSessionId(
                source, contentSessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        UUID conversationId = session.getId();

        Optional<UUID> locked = sessionRepository.lockSessionForUpdate(conversationId);
        if (locked.isEmpty()) {
            // Deleted between resolution and locking; treat as already gone.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }

        // Re-read inside the lock: the state check must describe the row we are deleting.
        SessionEntity current = sessionRepository.findById(conversationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        String status = current.getStatus() == null ? "" : current.getStatus().toLowerCase();
        if (List.of("active", "queued", "processing", "summarizing").contains(status)
            || agentService.isSessionProcessing(conversationId)
            || summaryGenerationService.isSessionSummarizing(conversationId.toString())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Session is still active or has queued work"
            );
        }

        // Preserve feedback that merely names this conversation as its trigger.
        List<Map<String, Object>> cleared = jdbcTemplate.queryForList("""
            UPDATE observation_feedback
               SET session_db_id = NULL
             WHERE session_db_id = :conversationId
            RETURNING id
            """, new MapSqlParameterSource("conversationId", conversationId));
        if (!cleared.isEmpty()) {
            log.info("Cleared {} feedback trigger reference(s) for deleted conversation {}",
                cleared.size(), conversationId);
        }

        // Conversation-owned children only. Standalone rows have conversation_id = NULL and
        // therefore cannot match, which is the guarantee that manual memories survive.
        int observations = observationRepository.deleteByConversationId(conversationId);
        int summaries = summaryRepository.deleteByConversationId(conversationId);
        int prompts = userPromptRepository.deleteByConversationId(conversationId);

        // Terminal queue rows belong to the conversation and go with it.
        int pending = pendingMessageRepository.deleteBySessionDbId(conversationId);

        sessionRepository.delete(current);
        sessionRepository.flush();

        log.info("Deleted conversation {}: {} observations, {} summaries, {} prompts, {} queue rows",
            conversationId, observations, summaries, prompts, pending);

        broadcastAfterCommit(Map.of(
            "type", "session_deleted",
            "platformSource", current.getPlatformSource() == null ? source : current.getPlatformSource(),
            "contentSessionId", current.getContentSessionId()
        ));
    }

    @Transactional
    public void deleteObservation(UUID id) {
        if (!observationRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Observation not found");
        }
        observationRepository.deleteByIdForViewer(id);
        broadcastAfterCommit(Map.of(
            "type", "item_deleted",
            "itemType", "observation",
            "id", id.toString()
        ));
    }

    @Transactional
    public void deleteSummary(UUID id) {
        if (!summaryRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Summary not found");
        }
        summaryRepository.deleteByIdForViewer(id);
        broadcastAfterCommit(Map.of(
            "type", "item_deleted",
            "itemType", "summary",
            "id", id.toString()
        ));
    }

    private void broadcastAfterCommit(Map<String, Object> event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseBroadcaster.broadcast(event, String.valueOf(event.get("type")));
            }
        });
    }

    private String optionalFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(ViewerSessionService.class);

    public record CatalogPage(
        List<CatalogEntry> sessions,
        @JsonProperty("hasMore") boolean hasMore
    ) {}

    public record CatalogEntry(
        @JsonProperty("content_session_id") String contentSessionId,
        @JsonProperty("project") String project,
        @JsonProperty("platform_source") String platformSource,
        @JsonProperty("custom_title") String customTitle,
        @JsonProperty("started_at_epoch") long startedAtEpoch,
        @JsonProperty("item_count") long itemCount
    ) {}
}
