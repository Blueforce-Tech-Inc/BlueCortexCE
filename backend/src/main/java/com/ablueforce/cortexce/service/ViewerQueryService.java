package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.repository.OffsetPageRequest;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.identity.MemoryScopeKey;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read queries behind the viewer REST surface.
 *
 * <p>The viewer endpoints are polling reads over four tables plus the shared
 * conversation filter rules. Moving the reads here keeps the filter precedence
 * (explicit conversation id > external (source, session id) > standalone) in one
 * place instead of inside an HTTP handler.
 *
 * <p>The three paginated lists are returned as JSON-shaped maps rather than JPA
 * entities. The wire format is produced by the application's own {@link ObjectMapper},
 * so every field keeps the exact name and nesting the bundled WebUI already
 * consumes ({@code items}/{@code hasMore}, snake_case entity fields, json strings
 * for {@code facts}/{@code concepts}). This keeps persistence types out of the
 * controller signatures without hand-maintaining a parallel DTO schema.
 */
@Service
public class ViewerQueryService {

    private final ObservationRepository observationRepository;
    private final SummaryRepository summaryRepository;
    private final UserPromptRepository userPromptRepository;
    private final SessionRepository sessionRepository;
    private final SessionManagementService sessionManagementService;
    private final ObjectMapper objectMapper;

    public ViewerQueryService(ObservationRepository observationRepository,
                              SummaryRepository summaryRepository,
                              UserPromptRepository userPromptRepository,
                              SessionRepository sessionRepository,
                              SessionManagementService sessionManagementService,
                              ObjectMapper objectMapper) {
        this.observationRepository = observationRepository;
        this.summaryRepository = summaryRepository;
        this.userPromptRepository = userPromptRepository;
        this.sessionRepository = sessionRepository;
        this.sessionManagementService = sessionManagementService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> findObservations(String project, String platformSource,
                                                      String contentSessionId, String conversationId,
                                                      String standaloneKind, int offset, int limit) {
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        int validatedOffset = Math.max(0, offset);

        boolean manualSentinel = MemoryScopeKey.LEGACY_MANUAL_PROJECT_PATH.equals(contentSessionId);
        if (manualSentinel && standaloneKind != null && !standaloneKind.isBlank()
            && !"manual".equals(standaloneKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "manual-memories requires standaloneKind=manual");
        }
        String effectiveStandaloneKind = manualSentinel ? "manual" : standaloneKind;
        boolean includeManualDefault = manualSentinel
            && (project == null || project.isBlank()
                || MemoryScopeKey.LEGACY_MANUAL_PROJECT_PATH.equals(project));
        UUID convId = resolveConversationFilter(conversationId, platformSource, contentSessionId,
            effectiveStandaloneKind);

        return toWirePage(observationRepository.findAllPaged(project, platformSource,
            convId, contentSessionId, effectiveStandaloneKind, includeManualDefault,
            new OffsetPageRequest(0, validatedLimit, validatedOffset,
                Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> findSummaries(String project, String platformSource,
                                                   String contentSessionId, String conversationId,
                                                   int offset, int limit) {
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        int validatedOffset = Math.max(0, offset);
        UUID ownerConversationId =
            resolveOwnerConversationFilter(conversationId, platformSource, contentSessionId);
        return toWirePage(summaryRepository.findAllPaged(project, platformSource,
            ownerConversationId, null,
            new OffsetPageRequest(0, validatedLimit, validatedOffset,
                Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> findPrompts(String project, String platformSource,
                                                 String contentSessionId, String conversationId,
                                                 int offset, int limit) {
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        int validatedOffset = Math.max(0, offset);
        UUID ownerConversationId =
            resolveOwnerConversationFilter(conversationId, platformSource, contentSessionId);
        return toWirePage(userPromptRepository.findAllPaged(project, platformSource,
            ownerConversationId, null,
            new OffsetPageRequest(0, validatedLimit, validatedOffset,
                Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    /** Database counters shown on the viewer status bar. */
    public record DatabaseStats(long totalObservations, long totalSummaries,
                                long totalSessions, long totalProjects,
                                String projectPath) {
    }

    @Transactional(readOnly = true)
    public DatabaseStats getDatabaseStats(String project) {
        if (project != null && !project.isBlank()) {
            return new DatabaseStats(
                observationRepository.countByProjectPath(project),
                summaryRepository.countByProjectPath(project),
                sessionRepository.countByProjectPath(project),
                1L,
                project);
        }
        return new DatabaseStats(
            observationRepository.count(),
            summaryRepository.count(),
            sessionRepository.count(),
            sessionRepository.countDistinctProjects(),
            null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTimeline(String project, long startEpoch, long endEpoch) {
        List<Object[]> results = observationRepository.findTimelineByDate(project, startEpoch, endEpoch);
        return results.stream().map(row -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("date", row[0].toString());
            entry.put("count", ((Number) row[1]).intValue());
            entry.put("ids", row[2]);
            return entry;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ObservationEntity> findByFile(String project, String normalizedPath, int limit) {
        return observationRepository.findByFolderPath(project, normalizedPath, limit);
    }

    @Transactional(readOnly = true)
    public List<ObservationEntity> findByIds(List<UUID> ids, String project, String orderBy, Integer limit) {
        List<ObservationEntity> observations = observationRepository.findAllById(ids);

        if (project != null && !project.isBlank()) {
            observations = observations.stream()
                .filter(o -> project.equals(o.getProjectPath()))
                .toList();
        }

        if ("created_at_epoch".equals(orderBy) || "createdAtEpoch".equals(orderBy)) {
            observations = observations.stream()
                .sorted((a, b) -> Long.compare(
                    b.getCreatedAtEpoch() != null ? b.getCreatedAtEpoch() : 0,
                    a.getCreatedAtEpoch() != null ? a.getCreatedAtEpoch() : 0
                ))
                .toList();
        }

        if (limit != null && limit > 0 && observations.size() > limit) {
            observations = observations.subList(0, limit);
        }
        return observations;
    }

    /**
     * Project an entity page onto the wire shape without leaving the service layer.
     * Round-tripping through the container's ObjectMapper keeps @JsonProperty,
     * @JsonIgnore and snake_case identical to the previous direct serialisation.
     */
    private <T> Page<Map<String, Object>> toWirePage(Page<T> page) {
        Page<Map<String, Object>> projected = page.map(entity -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> wire = objectMapper.convertValue(entity, Map.class);
            return wire;
        });
        return projected;
    }

    /**
     * Work out which filter a listing request really addresses.
     *
     * <p>Identity precedence (plan §5.3): an explicit {@code conversationId} wins outright.
     * Otherwise a full {@code (platformSource, contentSessionId)} pair is resolved to the
     * conversation UUID. The legacy {@code contentSessionId=manual-memories} sentinel is
     * interpreted as "manual standalone rows", never as a conversation.
     */
    private UUID resolveConversationFilter(String conversationId, String platformSource,
                                           String contentSessionId, String standaloneKind) {
        if (conversationId != null && !conversationId.isBlank()) {
            UUID id;
            try {
                id = UUID.fromString(conversationId);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId must be a UUID");
            }
            if (contentSessionId != null && !contentSessionId.isBlank()) {
                Optional<SessionEntity> byExternal = sessionManagementService.find(
                    ExternalSessionRef.of(platformSource, contentSessionId));
                if (byExternal.isEmpty() || !byExternal.get().getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "conversationId does not match contentSessionId/platformSource");
                }
            }
            if (standaloneKind != null && !standaloneKind.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "standaloneKind cannot be combined with conversationId");
            }
            return id;
        }
        if (contentSessionId != null && !contentSessionId.isBlank()
            && !MemoryScopeKey.LEGACY_MANUAL_PROJECT_PATH.equals(contentSessionId)) {
            return sessionManagementService.find(ExternalSessionRef.of(platformSource, contentSessionId))
                .map(SessionEntity::getId)
                // No such conversation: answer with an empty set rather than every row.
                .orElse(new UUID(0, 0));
        }
        return null;
    }

    private static UUID parseConversationId(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    /**
     * Resolve the owner filter before querying Summary/Prompt rows. Once external IDs are
     * composite identities, those repositories must never filter a child table by the bare
     * content ID. A missing external identity resolves to the zero UUID so the endpoint
     * returns an empty page rather than accidentally listing another platform's rows.
     */
    private UUID resolveOwnerConversationFilter(String conversationId, String platformSource,
                                                String contentSessionId) {
        UUID explicit = parseConversationId(conversationId);
        if (explicit != null) {
            if (contentSessionId != null && !contentSessionId.isBlank()) {
                Optional<SessionEntity> byExternal = sessionManagementService.find(
                    ExternalSessionRef.of(platformSource, contentSessionId));
                if (byExternal.isEmpty() || !explicit.equals(byExternal.get().getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "conversationId does not match contentSessionId/platformSource");
                }
            }
            return explicit;
        }
        if (contentSessionId == null || contentSessionId.isBlank()) {
            return null;
        }
        if (MemoryScopeKey.LEGACY_MANUAL_PROJECT_PATH.equals(contentSessionId)) {
            return new UUID(0, 0);
        }
        return sessionManagementService.find(ExternalSessionRef.of(platformSource, contentSessionId))
            .map(SessionEntity::getId)
            .orElse(new UUID(0, 0));
    }
}
