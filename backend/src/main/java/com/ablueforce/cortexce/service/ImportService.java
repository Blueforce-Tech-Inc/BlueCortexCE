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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ImportService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Logger getLogger() {
        return log;
    }

    private final SessionRepository sessionRepository;
    private final ObservationRepository observationRepository;
    private final SummaryRepository summaryRepository;
    private final UserPromptRepository userPromptRepository;
    private final SessionManagementService sessionManagementService;

    public ImportService(SessionRepository sessionRepository,
                        ObservationRepository observationRepository,
                        SummaryRepository summaryRepository,
                        UserPromptRepository userPromptRepository,
                        SessionManagementService sessionManagementService) {
        this.sessionRepository = sessionRepository;
        this.observationRepository = observationRepository;
        this.summaryRepository = summaryRepository;
        this.userPromptRepository = userPromptRepository;
        this.sessionManagementService = sessionManagementService;
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
        String sessionId,       // external session id (opaque, platform-scoped)
        String platformSource,  // owning platform; null means the Claude compatibility default
        String projectPath,
        String type,
        String title,
        String subtitle,
        String content,         // narrative
        String factsJson,       // JSON array string
        String conceptsJson,    // JSON array string
        String filesReadJson,   // JSON array string
        String filesModifiedJson, // JSON array string
        Integer promptNumber,
        Long createdAtEpoch,
        Integer discoveryTokens, // token count for discovery
        // Embedding vectors (as JSON arrays)
        List<Double> embedding768,
        List<Double> embedding1024,
        List<Double> embedding1536,
        String embeddingModelId
    ) {}

    /**
     * Import summary data.
     */
    public record SummaryImportData(
        String sessionId,       // external session id
        String platformSource,
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
        String sessionId,       // external session id
        String platformSource,
        Integer promptNumber,
        String promptText,
        Long createdAtEpoch
    ) {}

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
        com.ablueforce.cortexce.identity.ExternalSessionRef ref =
            com.ablueforce.cortexce.identity.ExternalSessionRef.of(platformSource, contentSessionId);
        return sessionRepository.findByPlatformSourceAndContentSessionId(
                ref.platformSource(), ref.contentSessionId())
            .orElseThrow(() -> new IllegalArgumentException(
                "unknown session for import: " + ref.logKey()
                    + " (import the session before its children)"));
    }

    /** Same as {@link #resolveImportParent} but returns null instead of throwing (bulk scan). */
    private SessionEntity resolveImportParentQuietly(String contentSessionId, String platformSource) {
        try {
            return resolveImportParent(contentSessionId, platformSource);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
            log.warn("Failed to parse JSON array: {}", e.getMessage());
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
        com.ablueforce.cortexce.identity.ExternalSessionRef ref;
        try {
            ref = com.ablueforce.cortexce.identity.ExternalSessionRef.of(
                data.platformSource(), data.contentSessionId());
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

        SessionEntity parent;
        try {
            parent = resolveImportParent(data.sessionId(), data.platformSource());
        } catch (IllegalArgumentException e) {
            return ImportResult.error(e.getMessage());
        }
        Optional<ObservationEntity> existing = observationRepository.findBySessionIdAndTitleAndCreatedAtEpoch(
            parent.getId(), data.title(), createdAtEpoch
        );
        if (existing.isPresent()) {
            log.debug("Observation already exists: {} - {}", data.sessionId(), data.title());
            return ImportResult.duplicate(existing.get().getId().toString());
        }

        ObservationEntity obs = new ObservationEntity();
        obs.setConversationId(parent.getId());
        obs.setPlatformSource(parent.getPlatformSource());
        obs.setContentSessionId(data.sessionId());
        obs.setProjectPath(data.projectPath());
        obs.setType(data.type() != null ? data.type() : "unknown");
        obs.setTitle(data.title());
        obs.setSubtitle(data.subtitle());
        obs.setContent(data.content());
        obs.setFacts(parseJsonArray(data.factsJson()));
        obs.setConcepts(parseJsonArray(data.conceptsJson()));
        obs.setFilesRead(parseJsonArray(data.filesReadJson()));
        obs.setFilesModified(parseJsonArray(data.filesModifiedJson()));
        obs.setPromptNumber(data.promptNumber() != null ? data.promptNumber() : 1);
        obs.setDiscoveryTokens(data.discoveryTokens() != null ? data.discoveryTokens() : 0);
        // Embedding vectors
        obs.setEmbedding768(toFloatArray(data.embedding768()));
        obs.setEmbedding1024(toFloatArray(data.embedding1024()));
        obs.setEmbedding1536(toFloatArray(data.embedding1536()));
        obs.setEmbeddingModelId(data.embeddingModelId());
        obs.setCreatedAtEpoch(createdAtEpoch);
        obs.setCreatedAt(epochToOffsetDateTime(createdAtEpoch));

        obs = observationRepository.save(obs);
        logSuccess("Imported observation: {} - {}", data.sessionId(), data.title());

        return ImportResult.imported(obs.getId().toString());
    }

    @Transactional
    public BulkImportResult importObservations(List<ObservationImportData> observations) {
        BulkImportResult result = new BulkImportResult();

        // Duplicate detection is scoped per conversation UUID. Grouping by the external id
        // would deduplicate two platforms against each other.
        Set<java.util.UUID> conversationIds = observations.stream()
            .map(o -> resolveImportParentQuietly(o.sessionId(), o.platformSource()))
            .filter(java.util.Objects::nonNull)
            .map(SessionEntity::getId)
            .collect(java.util.stream.Collectors.toSet());

        List<ObservationEntity> existingObs = conversationIds.isEmpty()
            ? List.of()
            : observationRepository.findByConversationIdIn(new java.util.ArrayList<>(conversationIds));

        Set<String> duplicateKeys = existingObs.stream()
            .map(o -> o.getConversationId() + "|" + o.getTitle() + "|" + o.getCreatedAtEpoch())
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

                long createdAtEpoch = data.createdAtEpoch() != null ? data.createdAtEpoch() : System.currentTimeMillis();
                String key = data.sessionId() + "|" + data.title() + "|" + createdAtEpoch;

                if (duplicateKeys.contains(key)) {
                    result = result.addDuplicate();
                    continue;
                }
                duplicateKeys.add(key); // prevent duplicates within the same batch

                ObservationEntity obs = new ObservationEntity();
                obs.setContentSessionId(data.sessionId());
                obs.setProjectPath(data.projectPath());
                obs.setType(data.type() != null ? data.type() : "unknown");
                obs.setTitle(data.title());
                obs.setSubtitle(data.subtitle());
                obs.setContent(data.content());
                obs.setFacts(parseJsonArray(data.factsJson()));
                obs.setConcepts(parseJsonArray(data.conceptsJson()));
                obs.setFilesRead(parseJsonArray(data.filesReadJson()));
                obs.setFilesModified(parseJsonArray(data.filesModifiedJson()));
                obs.setPromptNumber(data.promptNumber() != null ? data.promptNumber() : 1);
                obs.setDiscoveryTokens(data.discoveryTokens() != null ? data.discoveryTokens() : 0);
                obs.setEmbedding768(toFloatArray(data.embedding768()));
                obs.setEmbedding1024(toFloatArray(data.embedding1024()));
                obs.setEmbedding1536(toFloatArray(data.embedding1536()));
                obs.setEmbeddingModelId(data.embeddingModelId());
                obs.setCreatedAtEpoch(createdAtEpoch);
                obs.setCreatedAt(epochToOffsetDateTime(createdAtEpoch));

                toSave.add(obs);
            } catch (Exception e) {
                logFailure("Error preparing observation: {}", e.getMessage());
                result = result.addError(e.getMessage());
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

        Set<java.util.UUID> conversationIds = prompts.stream()
            .map(p -> resolveImportParentQuietly(p.sessionId(), p.platformSource()))
            .filter(java.util.Objects::nonNull)
            .map(SessionEntity::getId)
            .collect(java.util.stream.Collectors.toSet());

        List<UserPromptEntity> existingPrompts = conversationIds.isEmpty()
            ? List.of()
            : userPromptRepository.findByConversationIdIn(new java.util.ArrayList<>(conversationIds));

        Set<String> duplicateKeys = existingPrompts.stream()
            .map(p -> p.getConversationId() + "|" + p.getPromptNumber())
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

                String key = data.sessionId() + "|" + data.promptNumber();

                if (duplicateKeys.contains(key)) {
                    result = result.addDuplicate();
                    continue;
                }
                duplicateKeys.add(key); // prevent duplicates within the same batch

                long createdAtEpoch = data.createdAtEpoch() != null ? data.createdAtEpoch() : System.currentTimeMillis();

                UserPromptEntity prompt = new UserPromptEntity();
                prompt.setContentSessionId(data.sessionId());
                prompt.setPromptNumber(data.promptNumber());
                prompt.setPromptText(data.promptText());
                prompt.setCreatedAtEpoch(createdAtEpoch);
                prompt.setCreatedAt(epochToOffsetDateTime(createdAtEpoch));

                toSave.add(prompt);
            } catch (Exception e) {
                logFailure("Error preparing user prompt: {}", e.getMessage());
                result = result.addError(e.getMessage());
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
