package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.entity.UserPromptEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import com.ablueforce.cortexce.common.LogHelper;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.identity.MemoryScopeKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ImportService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> STANDALONE_KINDS = Set.of("manual", "extraction", "dlq");
    private static final String MANUAL_SENTINEL = MemoryScopeKey.LEGACY_MANUAL_PROJECT_PATH;

    @Override
    public Logger getLogger() {
        return log;
    }

    private final SessionRepository sessionRepository;
    private final ObservationRepository observationRepository;
    private final SummaryRepository summaryRepository;
    private final UserPromptRepository userPromptRepository;
    private final SessionManagementService sessionManagementService;
    private final MemoryScopeService memoryScopeService;
    private final ConversationWriteService conversationWriteService;
    private final StandaloneObservationImportLock standaloneObservationImportLock;

    public ImportService(SessionRepository sessionRepository,
                        ObservationRepository observationRepository,
                        SummaryRepository summaryRepository,
                        UserPromptRepository userPromptRepository,
                        SessionManagementService sessionManagementService,
                        MemoryScopeService memoryScopeService,
                        ConversationWriteService conversationWriteService,
                        StandaloneObservationImportLock standaloneObservationImportLock) {
        this.sessionRepository = sessionRepository;
        this.observationRepository = observationRepository;
        this.summaryRepository = summaryRepository;
        this.userPromptRepository = userPromptRepository;
        this.sessionManagementService = sessionManagementService;
        this.memoryScopeService = memoryScopeService;
        this.conversationWriteService = conversationWriteService;
        this.standaloneObservationImportLock = standaloneObservationImportLock;
    }

    /**
     * Result of an import operation.
     */
    public record ImportResult(boolean imported, String id, String message) {
        public static ImportResult imported(String id) {
            return new ImportResult(true, id, "Imported successfully");
        }

        public static ImportResult duplicate(String id) {
            return new ImportResult(false, id, "Duplicate found, skipped");
        }

        public static ImportResult error(String message) {
            return new ImportResult(false, null, message);
        }

        /**
         * True when this result is a validation failure rather than a skip.
         *
         * <p>{@link #imported()} alone is not enough to tell the three cases apart:
         * both {@code duplicate} and {@code error} report {@code imported == false}, so
         * callers that branch on it alone silently counted validation failures as
         * duplicates and answered {@code {"success":true,"skipped":1,"errors":0}} for
         * records that were dropped on the floor. Only {@link #error()} leaves
         * {@code id} null, which is what distinguishes it from a real duplicate that
         * resolved to an existing row.
         *
         * <p>Note the asymmetry this exists to correct: a failure <em>thrown</em> from
         * the service was always reported through the caller's error list, while the
         * same failure <em>returned</em> was not. Both paths now land in the error
         * count.
         */
        public boolean isError() {
            return !imported && id == null;
        }
    }

    /**
     * Bulk import result summary.
     */
    public record BulkImportResult(
        int imported,
        int duplicates,
        int errors,
        List<String> importedIds,
        List<String> errorMessages
    ) {
        public BulkImportResult() {
            this(0, 0, 0, new ArrayList<>(), new ArrayList<>());
        }

        public BulkImportResult addImported(String id) {
            List<String> newIds = new ArrayList<>(this.importedIds);
            newIds.add(id);
            return new BulkImportResult(
                this.imported + 1, this.duplicates, this.errors,
                newIds, this.errorMessages
            );
        }

        public BulkImportResult addDuplicate() {
            return new BulkImportResult(
                this.imported, this.duplicates + 1, this.errors,
                this.importedIds, this.errorMessages
            );
        }

        public BulkImportResult addError(String message) {
            List<String> newErrors = new ArrayList<>(this.errorMessages);
            newErrors.add(message);
            return new BulkImportResult(
                this.imported, this.duplicates, this.errors + 1,
                this.importedIds, newErrors
            );
        }
    }

    /**
     * Import session data.
     */
    public record SessionImportData(
        String contentSessionId,
        @JsonAlias("project")
        String projectPath,
        String userPrompt,
        Long startedAtEpoch,
        Long completedAtEpoch,
        String status,
        String platformSource
    ) {}

    /**
     * Import observation data.
     */
    public record ObservationImportData(
        @JsonAlias({"content_session_id", "session_id"})
        String sessionId,       // external session id (opaque, platform-scoped)
        String platformSource,  // owning platform; null means the Claude compatibility default
        @JsonAlias("project")
        String projectPath,
        String type,
        String title,
        String subtitle,
        @JsonAlias("narrative")
        String content,         // narrative
        @JsonAlias("facts")
        String factsJson,       // JSON array string
        @JsonAlias("concepts")
        String conceptsJson,    // JSON array string
        @JsonAlias("files_read")
        String filesReadJson,   // JSON array string
        @JsonAlias("files_modified")
        String filesModifiedJson, // JSON array string
        Integer promptNumber,
        Long createdAtEpoch,
        Integer discoveryTokens, // token count for discovery
        // Embedding vectors (as JSON arrays)
        List<Double> embedding768,
        List<Double> embedding1024,
        List<Double> embedding1536,
        String embeddingModelId,
        @JsonAlias("standalone_kind")
        String standaloneKind,
        @JsonAlias("memory_scope_type")
        String memoryScopeType,
        String source,
        @JsonAlias("extractedData")
        Map<String, Object> extractedData
    ) {}

    /**
     * Import summary data.
     */
    public record SummaryImportData(
        String sessionId,       // external session id
        String platformSource,
        @JsonAlias("project")
        String projectPath,
        String request,
        String investigated,
        String learned,
        String completed,
        String nextSteps,
        String filesRead,
        String filesEdited,
        String notes,
        Integer promptNumber,
        Long createdAtEpoch
    ) {}

    /**
     * Import user prompt data.
     */
    public record UserPromptImportData(
        @JsonAlias({"content_session_id", "session_id"})
        String sessionId,       // external session id
        String platformSource,
        Integer promptNumber,
        String promptText,
        Long createdAtEpoch
    ) {}

    private record ObservationImportKey(UUID conversationId, UUID memoryScopeId, String standaloneKind,
                                        String platformSource, String correlationKey, String title,
                                        long createdAtEpoch) {
        String advisoryLockKey() {
            if (conversationId != null || memoryScopeId == null || standaloneKind == null) {
                throw new IllegalStateException("advisory lock requested for a non-standalone import key");
            }
            return "standalone-observation-import-v1"
                + encode(memoryScopeId.toString())
                + encode(standaloneKind)
                + encode(platformSource)
                + encode(correlationKey)
                + encode(title)
                + encode(Long.toString(createdAtEpoch));
        }

        private static String encode(String value) {
            return value.length() + ":" + value;
        }
    }

    private record PromptImportKey(UUID conversationId, Integer promptNumber) {}

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    /**
     * Resolve (or create) the local conversation an imported child row belongs to.
     *
     * <p>Import never trusts a UUID from the source database: the internal id is rebuilt
     * locally from the external identity, so a round-trip between two deployments cannot
     * alias unrelated conversations.
     */
    private SessionEntity resolveImportParent(String contentSessionId, String platformSource) {
        if (contentSessionId == null || contentSessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        ExternalSessionRef ref = ExternalSessionRef.of(platformSource, contentSessionId);
        return sessionRepository.findByPlatformSourceAndContentSessionId(
                ref.platformSource(), ref.contentSessionId())
            .orElseThrow(() -> new IllegalArgumentException(
                "unknown session for import: " + ref.logKey()
                    + " (import the session before its children)"));
    }

    private OffsetDateTime epochToOffsetDateTime(Long epoch) {
        if (epoch == null) return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    private float[] toFloatArray(List<Double> list) {
        if (list == null || list.isEmpty()) return null;
        float[] array = new float[list.size()];
        int count = 0;
        for (Double d : list) {
            if (d != null) {
                array[count++] = d.floatValue();
            }
        }
        if (count == 0) return null;
        return count == list.size() ? array : java.util.Arrays.copyOf(array, count);
    }

    @Transactional
    public ImportResult importSession(SessionImportData data) {
        if (data.contentSessionId() == null || data.contentSessionId().isBlank()) {
            return ImportResult.error("contentSessionId is required");
        }
        // mem_sessions.project_path is NOT NULL. Without this check the save fails at
        // commit time and the caller is told "Could not commit JPA transaction", which
        // names neither the field nor the constraint. Verified live: omitting
        // project_path produced exactly that opaque message.
        if (data.projectPath() == null || data.projectPath().isBlank()) {
            return ImportResult.error("projectPath is required");
        }

        // Import identity is (platform, external id): the same external id on two platforms
        // is two independent conversations and must not be merged.
        ExternalSessionRef ref;
        try {
            ref = ExternalSessionRef.of(data.platformSource(), data.contentSessionId());
        } catch (IllegalArgumentException e) {
            return ImportResult.error(e.getMessage());
        }
        Optional<SessionEntity> existing = sessionRepository.findByPlatformSourceAndContentSessionId(
            ref.platformSource(), ref.contentSessionId());
        if (existing.isPresent()) {
            log.debug("Session already exists: {}", ref.logKey());
            return ImportResult.duplicate(existing.get().getId().toString());
        }

        SessionEntity session = sessionManagementService.createImported(
            ref, data.projectPath(), data.userPrompt(), data.startedAtEpoch(),
            data.completedAtEpoch(), data.status());
        logSuccess("Imported session: {} -> {}", data.contentSessionId(), session.getId());

        return ImportResult.imported(session.getId().toString());
    }

    @Transactional
    public ImportResult importObservation(ObservationImportData data) {
        if (data.sessionId() == null || data.sessionId().isBlank()) {
            return ImportResult.error("sessionId is required");
        }
        if (data.title() == null || data.title().isBlank()) {
            return ImportResult.error("title is required");
        }

        long createdAtEpoch = data.createdAtEpoch() != null ? data.createdAtEpoch() : System.currentTimeMillis();

        ExternalSessionRef ref;
        try {
            ref = ExternalSessionRef.of(data.platformSource(), data.sessionId());
        } catch (IllegalArgumentException e) {
            return ImportResult.error(e.getMessage());
        }
        boolean standalone = data.standaloneKind() != null;
        SessionEntity parent = null;
        UUID memoryScopeId;
        if (standalone) {
            try {
                memoryScopeId = resolveStandaloneScope(data);
            } catch (IllegalArgumentException e) {
                return ImportResult.error(e.getMessage());
            }
            ObservationImportKey importKey = standaloneObservationImportKey(
                memoryScopeId, ref, data.standaloneKind(), data.title(), createdAtEpoch);
            standaloneObservationImportLock.lock(List.of(importKey.advisoryLockKey()));
        } else {
            try {
                parent = resolveImportParent(ref.contentSessionId(), ref.platformSource());
            } catch (IllegalArgumentException e) {
                return ImportResult.error(e.getMessage());
            }
            conversationWriteService.lockForChildMutation(parent.getId());
            String projectPath = data.projectPath() != null && !data.projectPath().isBlank()
                ? data.projectPath() : parent.getProjectPath();
            if (projectPath == null || projectPath.isBlank()) {
                return ImportResult.error("projectPath is required");
            }
            if ("manual_default".equals(data.memoryScopeType())) {
                return ImportResult.error("conversation observations cannot use manual_default scope");
            }
            memoryScopeId = memoryScopeService.resolveProjectScope(projectPath);
        }
        String projectPath = data.projectPath() != null && !data.projectPath().isBlank()
            ? data.projectPath() : parent == null ? null : parent.getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            return ImportResult.error("projectPath is required");
        }
        Optional<ObservationEntity> existing = standalone
            ? observationRepository.findByMemoryScopeIdAndStandaloneKindAndPlatformSourceAndContentSessionIdAndTitleAndCreatedAtEpoch(
                memoryScopeId, data.standaloneKind(), ref.platformSource(), ref.contentSessionId(), data.title(), createdAtEpoch)
            : observationRepository.findBySessionIdAndTitleAndCreatedAtEpoch(parent.getId(), data.title(), createdAtEpoch);
        if (existing.isPresent()) {
            log.debug("Observation already exists: {} - {}", data.sessionId(), data.title());
            return ImportResult.duplicate(existing.get().getId().toString());
        }

        ObservationEntity obs = toObservation(data, ref, parent, projectPath, memoryScopeId, createdAtEpoch);

        obs = observationRepository.save(obs);
        logSuccess("Imported observation: {} - {}", data.sessionId(), data.title());

        return ImportResult.imported(obs.getId().toString());
    }

    @Transactional
    public BulkImportResult importObservations(List<ObservationImportData> observations) {
        BulkImportResult result = new BulkImportResult();

        Map<ExternalSessionRef, SessionEntity> parentByRef = new HashMap<>();
        Map<ObservationImportData, UUID> standaloneScopeByData = new IdentityHashMap<>();
        Map<ObservationImportData, Long> createdAtByData = new IdentityHashMap<>();
        for (ObservationImportData data : observations) {
            if (data.sessionId() == null || data.sessionId().isBlank()
                || data.title() == null || data.title().isBlank()) {
                continue;
            }
            try {
                createdAtByData.put(data, data.createdAtEpoch() != null
                    ? data.createdAtEpoch() : System.currentTimeMillis());
                ExternalSessionRef ref = ExternalSessionRef.of(data.platformSource(), data.sessionId());
                if (data.standaloneKind() != null) {
                    standaloneScopeByData.put(data, resolveStandaloneScope(data));
                } else {
                    parentByRef.put(ref, resolveImportParent(ref.contentSessionId(), ref.platformSource()));
                }
            } catch (IllegalArgumentException ignored) {
                // The record is reported as an import error in the validation pass below.
            }
        }
        Set<UUID> conversationIds = parentByRef.values().stream()
            .map(SessionEntity::getId)
            .collect(java.util.stream.Collectors.toSet());
        conversationWriteService.lockForChildMutations(conversationIds);

        Set<String> standaloneLockKeys = new HashSet<>();
        standaloneScopeByData.forEach((data, scopeId) -> {
            ExternalSessionRef ref = ExternalSessionRef.of(data.platformSource(), data.sessionId());
            long createdAtEpoch = createdAtByData.get(data);
            standaloneLockKeys.add(standaloneObservationImportKey(
                scopeId, ref, data.standaloneKind(), data.title(), createdAtEpoch).advisoryLockKey());
        });
        standaloneObservationImportLock.lock(standaloneLockKeys);

        List<ObservationEntity> existingConversationObs = conversationIds.isEmpty()
            ? List.of()
            : observationRepository.findByConversationIdIn(new java.util.ArrayList<>(conversationIds));
        List<UUID> standaloneScopeIds = standaloneScopeByData.values().stream().distinct().toList();
        List<String> standaloneKinds = standaloneScopeByData.keySet().stream()
            .map(ObservationImportData::standaloneKind).distinct().toList();
        List<ObservationEntity> existingStandaloneObs = standaloneScopeIds.isEmpty()
            ? List.of()
            : observationRepository.findByMemoryScopeIdInAndStandaloneKindIn(standaloneScopeIds, standaloneKinds);

        Set<ObservationImportKey> duplicateKeys = java.util.stream.Stream
            .concat(existingConversationObs.stream(), existingStandaloneObs.stream())
            .map(ImportService::observationImportKey)
            .collect(java.util.stream.Collectors.toSet());

        // Step 4: Separate new observations from validation errors
        List<ObservationEntity> toSave = new java.util.ArrayList<>();
        for (ObservationImportData data : observations) {
            try {
                // Validate
                if (data.sessionId() == null || data.sessionId().isBlank()) {
                    result = result.addError("sessionId is required");
                    continue;
                }
                if (data.title() == null || data.title().isBlank()) {
                    result = result.addError("title is required");
                    continue;
                }

                ExternalSessionRef ref = ExternalSessionRef.of(data.platformSource(), data.sessionId());
                boolean standalone = data.standaloneKind() != null;
                SessionEntity parent = standalone ? null : parentByRef.get(ref);
                if (!standalone && parent == null) {
                    result = result.addError("unknown session for import: " + ref.logKey());
                    continue;
                }

                long createdAtEpoch = createdAtByData.computeIfAbsent(data,
                    ignored -> data.createdAtEpoch() != null ? data.createdAtEpoch() : System.currentTimeMillis());

                String projectPath = data.projectPath() != null && !data.projectPath().isBlank()
                    ? data.projectPath() : parent == null ? null : parent.getProjectPath();
                if (projectPath == null || projectPath.isBlank()) {
                    result = result.addError("projectPath is required");
                    continue;
                }

                UUID memoryScopeId;
                if (standalone) {
                    memoryScopeId = standaloneScopeByData.get(data);
                    if (memoryScopeId == null) {
                        memoryScopeId = resolveStandaloneScope(data);
                    }
                } else {
                    if ("manual_default".equals(data.memoryScopeType())) {
                        result = result.addError("conversation observations cannot use manual_default scope");
                        continue;
                    }
                    memoryScopeId = memoryScopeService.resolveProjectScope(projectPath);
                }

                ObservationEntity candidate = toObservation(
                    data, ref, parent, projectPath, memoryScopeId, createdAtEpoch);
                ObservationImportKey key = observationImportKey(candidate);
                if (duplicateKeys.contains(key)) {
                    result = result.addDuplicate();
                    continue;
                }

                duplicateKeys.add(key);
                toSave.add(candidate);
            } catch (Exception e) {
                logFailure("Error preparing observation: {}", e.getClass().getSimpleName());
                result = result.addError("observation could not be prepared ("
                    + e.getClass().getSimpleName() + ")");
            }
        }

        // Step 5: Bulk save all new observations (single DB round trip)
        if (!toSave.isEmpty()) {
            List<ObservationEntity> saved = observationRepository.saveAll(toSave);
            for (ObservationEntity savedObs : saved) {
                result = result.addImported(savedObs.getId().toString());
                logSuccess("Imported observation: {} - {}", savedObs.getContentSessionId(), savedObs.getTitle());
            }
        }

        logSuccess("Bulk import observations: {} imported, {} duplicates, {} errors",
            result.imported(), result.duplicates(), result.errors());

        return result;
    }

    private UUID resolveStandaloneScope(ObservationImportData data) {
        String kind = data.standaloneKind();
        if (kind == null || kind.isBlank() || !STANDALONE_KINDS.contains(kind)) {
            throw new IllegalArgumentException("standaloneKind must be manual, extraction, or dlq");
        }
        if (data.projectPath() == null || data.projectPath().isBlank()) {
            throw new IllegalArgumentException("projectPath is required for standalone observations");
        }
        String expectedSource = switch (kind) {
            case "manual" -> "manual";
            case "extraction" -> "llm_extraction";
            case "dlq" -> "dlq";
            default -> throw new IllegalArgumentException("unsupported standaloneKind");
        };
        if (data.source() != null && !expectedSource.equals(data.source())) {
            throw new IllegalArgumentException("source does not match standaloneKind");
        }
        if ("extraction".equals(kind) && (data.type() == null || !data.type().startsWith("extracted_"))) {
            throw new IllegalArgumentException("extraction observations require an extracted_ type");
        }
        if ("dlq".equals(kind) && (data.type() == null || !data.type().startsWith("dlq_"))) {
            throw new IllegalArgumentException("DLQ observations require a dlq_ type");
        }

        String scopeType = data.memoryScopeType();
        if (scopeType == null || scopeType.isBlank()) {
            scopeType = "manual".equals(kind) && MANUAL_SENTINEL.equals(data.projectPath())
                ? "manual_default" : "project";
        }
        if ("manual_default".equals(scopeType)) {
            if (!"manual".equals(kind) || !MANUAL_SENTINEL.equals(data.projectPath())) {
                throw new IllegalArgumentException("manual_default scope requires a manual sentinel observation");
            }
            return memoryScopeService.resolveManualDefault();
        }
        if (!"project".equals(scopeType)) {
            throw new IllegalArgumentException("memoryScopeType must be project or manual_default");
        }
        return memoryScopeService.resolveProjectScope(data.projectPath());
    }

    private ObservationEntity toObservation(ObservationImportData data, ExternalSessionRef ref,
                                            SessionEntity parent, String projectPath, UUID memoryScopeId,
                                            long createdAtEpoch) {
        String kind = data.standaloneKind();
        ObservationEntity observation = new ObservationEntity();
        observation.setConversationId(parent == null ? null : parent.getId());
        observation.setStandaloneKind(kind);
        observation.setPlatformSource(parent == null ? ref.platformSource() : parent.getPlatformSource());
        observation.setContentSessionId(ref.contentSessionId());
        observation.setProjectPath(projectPath);
        observation.setMemoryScopeId(memoryScopeId);
        observation.setType(data.type() != null ? data.type() : "unknown");
        observation.setTitle(data.title());
        observation.setSubtitle(data.subtitle());
        observation.setContent(data.content());
        observation.setFacts(parseJsonArray(data.factsJson()));
        observation.setConcepts(parseJsonArray(data.conceptsJson()));
        observation.setFilesRead(parseJsonArray(data.filesReadJson()));
        observation.setFilesModified(parseJsonArray(data.filesModifiedJson()));
        observation.setPromptNumber(data.promptNumber() != null ? data.promptNumber() : 1);
        observation.setDiscoveryTokens(data.discoveryTokens() != null ? data.discoveryTokens() : 0);
        observation.setEmbedding768(toFloatArray(data.embedding768()));
        observation.setEmbedding1024(toFloatArray(data.embedding1024()));
        observation.setEmbedding1536(toFloatArray(data.embedding1536()));
        observation.setEmbeddingModelId(data.embeddingModelId());
        observation.setSource(data.source() != null ? data.source() : sourceForStandaloneKind(kind));
        observation.setExtractedData(data.extractedData());
        observation.setCreatedAtEpoch(createdAtEpoch);
        observation.setCreatedAt(epochToOffsetDateTime(createdAtEpoch));
        return observation;
    }

    private static String sourceForStandaloneKind(String kind) {
        if (kind == null) return null;
        return switch (kind) {
            case "manual" -> "manual";
            case "extraction" -> "llm_extraction";
            case "dlq" -> "dlq";
            default -> null;
        };
    }

    private static ObservationImportKey observationImportKey(ObservationEntity observation) {
        if (observation.getConversationId() != null) {
            return new ObservationImportKey(observation.getConversationId(), null, null, null, null,
                observation.getTitle(), observation.getCreatedAtEpoch());
        }
        return new ObservationImportKey(null, observation.getMemoryScopeId(), observation.getStandaloneKind(),
            observation.getPlatformSource(), observation.getContentSessionId(), observation.getTitle(),
            observation.getCreatedAtEpoch());
    }

    private static ObservationImportKey standaloneObservationImportKey(UUID memoryScopeId,
                                                                        ExternalSessionRef ref,
                                                                        String standaloneKind,
                                                                        String title,
                                                                        long createdAtEpoch) {
        return new ObservationImportKey(null, memoryScopeId, standaloneKind, ref.platformSource(),
            ref.contentSessionId(), title, createdAtEpoch);
    }

    @Transactional
    public ImportResult importSummary(SummaryImportData data) {
        if (data.sessionId() == null || data.sessionId().isBlank()) {
            return ImportResult.error("sessionId is required");
        }
        // mem_summaries.project_path is NOT NULL. Same reason as importSession: without
        // this check the caller gets a raw PostgreSQL constraint-violation dump instead
        // of a message naming the field.
        if (data.projectPath() == null || data.projectPath().isBlank()) {
            return ImportResult.error("projectPath is required");
        }

        SessionEntity summaryParent;
        try {
            summaryParent = resolveImportParent(data.sessionId(), data.platformSource());
        } catch (IllegalArgumentException e) {
            return ImportResult.error(e.getMessage());
        }
        conversationWriteService.lockForChildMutation(summaryParent.getId());
        List<SummaryEntity> existing = summaryRepository.findByConversationId(summaryParent.getId());

        if (!existing.isEmpty()) {
            log.debug("Summary already exists for session: {}", data.sessionId());
            return ImportResult.duplicate(existing.get(0).getId().toString());
        }

        long createdAtEpoch = data.createdAtEpoch() != null ? data.createdAtEpoch() : System.currentTimeMillis();

        SummaryEntity summary = new SummaryEntity();
        summary.setConversationId(summaryParent.getId());
        summary.setPlatformSource(summaryParent.getPlatformSource());
        summary.setContentSessionId(data.sessionId());
        summary.setProjectPath(data.projectPath());
        summary.setRequest(data.request());
        summary.setInvestigated(data.investigated());
        summary.setLearned(data.learned());
        summary.setCompleted(data.completed());
        summary.setNextSteps(data.nextSteps());
        summary.setFilesRead(data.filesRead());
        summary.setFilesEdited(data.filesEdited());
        summary.setNotes(data.notes());
        summary.setPromptNumber(data.promptNumber() != null ? data.promptNumber() : 1);
        summary.setCreatedAtEpoch(createdAtEpoch);
        summary.setCreatedAt(epochToOffsetDateTime(createdAtEpoch));

        summary = summaryRepository.save(summary);
        logSuccess("Imported summary for session: {}", data.sessionId());

        return ImportResult.imported(summary.getId().toString());
    }

    @Transactional
    public ImportResult importUserPrompt(UserPromptImportData data) {
        if (data.sessionId() == null || data.sessionId().isBlank()) {
            return ImportResult.error("sessionId is required");
        }
        if (data.promptNumber() == null) {
            return ImportResult.error("promptNumber is required");
        }

        SessionEntity promptParent;
        try {
            promptParent = resolveImportParent(data.sessionId(), data.platformSource());
        } catch (IllegalArgumentException e) {
            return ImportResult.error(e.getMessage());
        }
        conversationWriteService.lockForChildMutation(promptParent.getId());
        Optional<UserPromptEntity> existing = userPromptRepository.findByConversationIdAndPromptNumber(
            promptParent.getId(), data.promptNumber()
        );
        if (existing.isPresent()) {
            log.debug("User prompt already exists: {} - #{}", data.sessionId(), data.promptNumber());
            return ImportResult.duplicate(existing.get().getId().toString());
        }

        long createdAtEpoch = data.createdAtEpoch() != null ? data.createdAtEpoch() : System.currentTimeMillis();

        UserPromptEntity prompt = new UserPromptEntity();
        prompt.setConversationId(promptParent.getId());
        prompt.setPlatformSource(promptParent.getPlatformSource());
        prompt.setContentSessionId(data.sessionId());
        prompt.setPromptNumber(data.promptNumber());
        prompt.setPromptText(data.promptText());
        prompt.setCreatedAtEpoch(createdAtEpoch);
        prompt.setCreatedAt(epochToOffsetDateTime(createdAtEpoch));

        prompt = userPromptRepository.save(prompt);
        logSuccess("Imported user prompt: {} #{}", data.sessionId(), data.promptNumber());

        return ImportResult.imported(prompt.getId().toString());
    }

    /**
     * Bulk import user prompts.
     */
    @Transactional
    public BulkImportResult importUserPrompts(List<UserPromptImportData> prompts) {
        BulkImportResult result = new BulkImportResult();

        Map<ExternalSessionRef, SessionEntity> parentByRef = new HashMap<>();
        for (UserPromptImportData data : prompts) {
            if (data.sessionId() == null || data.sessionId().isBlank()) {
                continue;
            }
            try {
                ExternalSessionRef ref = ExternalSessionRef.of(data.platformSource(), data.sessionId());
                parentByRef.put(ref, resolveImportParent(data.sessionId(), data.platformSource()));
            } catch (IllegalArgumentException ignored) {
                // The record is reported as an import error in the validation pass below.
            }
        }
        Set<UUID> conversationIds = parentByRef.values().stream()
            .map(SessionEntity::getId)
            .collect(java.util.stream.Collectors.toSet());
        conversationWriteService.lockForChildMutations(conversationIds);

        List<UserPromptEntity> existingPrompts = conversationIds.isEmpty()
            ? List.of()
            : userPromptRepository.findByConversationIdIn(new java.util.ArrayList<>(conversationIds));

        Set<PromptImportKey> duplicateKeys = existingPrompts.stream()
            .map(p -> new PromptImportKey(p.getConversationId(), p.getPromptNumber()))
            .collect(java.util.stream.Collectors.toSet());

        // Step 4: Separate new prompts from validation errors
        List<UserPromptEntity> toSave = new java.util.ArrayList<>();
        for (UserPromptImportData data : prompts) {
            try {
                // Validate
                if (data.sessionId() == null || data.sessionId().isBlank()) {
                    result = result.addError("sessionId is required");
                    continue;
                }
                if (data.promptNumber() == null) {
                    result = result.addError("promptNumber is required");
                    continue;
                }

                ExternalSessionRef ref = ExternalSessionRef.of(data.platformSource(), data.sessionId());
                SessionEntity parent = parentByRef.get(ref);
                if (parent == null) {
                    result = result.addError("unknown session for import: " + ref.logKey());
                    continue;
                }

                PromptImportKey key = new PromptImportKey(parent.getId(), data.promptNumber());
                if (duplicateKeys.contains(key)) {
                    result = result.addDuplicate();
                    continue;
                }

                long createdAtEpoch = data.createdAtEpoch() != null ? data.createdAtEpoch() : System.currentTimeMillis();

                UserPromptEntity prompt = new UserPromptEntity();
                prompt.setConversationId(parent.getId());
                prompt.setPlatformSource(parent.getPlatformSource());
                prompt.setContentSessionId(data.sessionId());
                prompt.setPromptNumber(data.promptNumber());
                prompt.setPromptText(data.promptText());
                prompt.setCreatedAtEpoch(createdAtEpoch);
                prompt.setCreatedAt(epochToOffsetDateTime(createdAtEpoch));

                duplicateKeys.add(key);
                toSave.add(prompt);
            } catch (Exception e) {
                logFailure("Error preparing user prompt: {}", e.getClass().getSimpleName());
                result = result.addError("user prompt could not be prepared ("
                    + e.getClass().getSimpleName() + ")");
            }
        }

        // Step 5: Bulk save all new prompts (single DB round trip)
        if (!toSave.isEmpty()) {
            List<UserPromptEntity> saved = userPromptRepository.saveAll(toSave);
            for (UserPromptEntity savedPrompt : saved) {
                result = result.addImported(savedPrompt.getId().toString());
                logSuccess("Imported user prompt: {} #{}", savedPrompt.getContentSessionId(), savedPrompt.getPromptNumber());
            }
        }

        logSuccess("Bulk import user prompts: {} imported, {} duplicates, {} errors",
            result.imported(), result.duplicates(), result.errors());

        return result;
    }
}
