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

@Service
public class ImportService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Logger getLogger() {
        return log;
    }

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private SummaryRepository summaryRepository;

    @Autowired
    private UserPromptRepository userPromptRepository;

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
        String status
    ) {}

    /**
     * Import observation data.
     */
    public record ObservationImportData(
        String sessionId,       // content_session_id (FK)
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
        String sessionId,       // content_session_id (FK)
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
        String sessionId,       // content_session_id
        Integer promptNumber,
        String promptText,
        Long createdAtEpoch
    ) {}

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    private OffsetDateTime epochToOffsetDateTime(Long epoch) {
        if (epoch == null) return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            logHappyPath("Failed to parse JSON array: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private float[] toFloatArray(List<Double> list) {
        if (list == null || list.isEmpty()) return null;
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i).floatValue();
        }
        return array;
    }

    @Transactional
    public ImportResult importSession(SessionImportData data) {
        if (data.contentSessionId() == null || data.contentSessionId().isBlank()) {
            return ImportResult.error("contentSessionId is required");
        }

        Optional<SessionEntity> existing = sessionRepository.findByContentSessionId(data.contentSessionId());
        if (existing.isPresent()) {
            log.debug("Session already exists: {}", data.contentSessionId());
            return ImportResult.duplicate(existing.get().getId().toString());
        }

        SessionEntity session = new SessionEntity();
        session.setContentSessionId(data.contentSessionId());
        session.setProjectPath(data.projectPath());
        session.setUserPrompt(data.userPrompt());
        session.setStartedAtEpoch(data.startedAtEpoch() != null ? data.startedAtEpoch() : System.currentTimeMillis());
        session.setCompletedAtEpoch(data.completedAtEpoch());
        session.setStatus(data.status() != null ? data.status() : "active");

        session = sessionRepository.save(session);
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

        Optional<ObservationEntity> existing = observationRepository.findBySessionIdAndTitleAndCreatedAtEpoch(
            data.sessionId(), data.title(), createdAtEpoch
        );
        if (existing.isPresent()) {
            log.debug("Observation already exists: {} - {}", data.sessionId(), data.title());
            return ImportResult.duplicate(existing.get().getId().toString());
        }

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

        for (ObservationImportData data : observations) {
            try {
                ImportResult ir = importObservation(data);
                if (ir.imported()) {
                    result = result.addImported(ir.id());
                } else if (ir.message().contains("Duplicate")) {
                    result = result.addDuplicate();
                } else {
                    result = result.addError(ir.message());
                }
            } catch (Exception e) {
                logFailure("Error importing observation: {}", e.getMessage());
                result = result.addError(e.getMessage());
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

        List<SummaryEntity> existing = summaryRepository.findByContentSessionId(data.sessionId());

        if (!existing.isEmpty()) {
            log.debug("Summary already exists for session: {}", data.sessionId());
            return ImportResult.duplicate(existing.get(0).getId().toString());
        }

        long createdAtEpoch = data.createdAtEpoch() != null ? data.createdAtEpoch() : System.currentTimeMillis();

        SummaryEntity summary = new SummaryEntity();
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

        Optional<UserPromptEntity> existing = userPromptRepository.findByContentSessionIdAndPromptNumber(
            data.sessionId(), data.promptNumber()
        );
        if (existing.isPresent()) {
            log.debug("User prompt already exists: {} - #{}", data.sessionId(), data.promptNumber());
            return ImportResult.duplicate(existing.get().getId().toString());
        }

        long createdAtEpoch = data.createdAtEpoch() != null ? data.createdAtEpoch() : System.currentTimeMillis();

        UserPromptEntity prompt = new UserPromptEntity();
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

        for (UserPromptImportData data : prompts) {
            try {
                ImportResult ir = importUserPrompt(data);
                if (ir.imported()) {
                    result = result.addImported(ir.id());
                } else if (ir.message().contains("Duplicate")) {
                    result = result.addDuplicate();
                } else {
                    result = result.addError(ir.message());
                }
            } catch (Exception e) {
                logFailure("Error importing user prompt: {}", e.getMessage());
                result = result.addError(e.getMessage());
            }
        }

        logSuccess("Bulk import user prompts: {} imported, {} duplicates, {} errors",
            result.imported(), result.duplicates(), result.errors());

        return result;
    }
}
