package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.entity.SessionEntity;
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

    public ViewerSessionService(NamedParameterJdbcTemplate jdbcTemplate,
                                SessionRepository sessionRepository,
                                ObservationRepository observationRepository,
                                SummaryRepository summaryRepository,
                                UserPromptRepository userPromptRepository,
                                AgentService agentService,
                                SummaryGenerationService summaryGenerationService,
                                SSEBroadcaster sseBroadcaster) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionRepository = sessionRepository;
        this.observationRepository = observationRepository;
        this.summaryRepository = summaryRepository;
        this.userPromptRepository = userPromptRepository;
        this.agentService = agentService;
        this.summaryGenerationService = summaryGenerationService;
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

        List<CatalogEntry> sessions = jdbcTemplate.query("""
            SELECT s.content_session_id,
                   s.project_path,
                   COALESCE(s.platform_source, 'claude') AS platform_source,
                   s.started_at_epoch,
                   (SELECT COUNT(*) FROM mem_observations o
                    WHERE o.content_session_id = s.content_session_id)
                   + (SELECT COUNT(*) FROM mem_summaries sm
                      WHERE sm.content_session_id = s.content_session_id)
                   + (SELECT COUNT(*) FROM mem_user_prompts p
                      WHERE p.content_session_id = s.content_session_id) AS item_count
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

    @Transactional
    public void deleteSession(String platformSource, String contentSessionId) {
        String source = optionalFilter(platformSource);
        if (source == null) {
            source = "claude";
        }

        SessionEntity session = sessionRepository.findForViewerDeletion(contentSessionId, source)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        String status = session.getStatus() == null ? "" : session.getStatus().toLowerCase();
        if (List.of("active", "queued", "processing", "summarizing").contains(status)
            || agentService.isSessionProcessing(contentSessionId)
            || summaryGenerationService.isSessionSummarizing(contentSessionId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Session is still active or has queued work"
            );
        }

        String sessionId = session.getContentSessionId();
        observationRepository.deleteByContentSessionId(sessionId);
        summaryRepository.deleteByContentSessionId(sessionId);
        userPromptRepository.deleteByContentSessionId(sessionId);
        sessionRepository.delete(session);
        sessionRepository.flush();

        broadcastAfterCommit(Map.of(
            "type", "session_deleted",
            "platformSource", source,
            "contentSessionId", sessionId
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
