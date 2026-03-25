package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Import Controller - Bulk data import with duplicate checking.
 * <p>
 * Aligned with TS src/services/worker/http/routes/DataRoutes.ts
 * <p>
 * Provides REST API endpoints for:
 * - POST /api/import - Bulk import all data types
 * - POST /api/import/sessions - Import sessions only
 * - POST /api/import/observations - Import observations only
 * - POST /api/import/summaries - Import summaries only
 * - POST /api/import/prompts - Import user prompts only
 * <p>
 * All imports include duplicate checking to prevent data duplication.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    @Autowired
    private ImportService importService;

    // ==========================================================================
    // Request/Response DTOs
    // ==========================================================================

    /**
     * Bulk import request containing all data types.
     */
    public record BulkImportRequest(
        List<ImportService.SessionImportData> sessions,
        List<ImportService.ObservationImportData> observations,
        List<ImportService.SummaryImportData> summaries,
        List<ImportService.UserPromptImportData> prompts
    ) {
        public BulkImportRequest {
            if (sessions == null) sessions = new ArrayList<>();
            if (observations == null) observations = new ArrayList<>();
            if (summaries == null) summaries = new ArrayList<>();
            if (prompts == null) prompts = new ArrayList<>();
        }
    }

    /**
     * Import statistics response.
     */
    public record ImportStats(
        int sessionsImported,
        int sessionsSkipped,
        int observationsImported,
        int observationsSkipped,
        int summariesImported,
        int summariesSkipped,
        int promptsImported,
        int promptsSkipped,
        int errors,
        List<String> errorMessages
    ) {
        public ImportStats() {
            this(0, 0, 0, 0, 0, 0, 0, 0, 0, new ArrayList<>());
        }

        public ImportStats addSessionImported() {
            return new ImportStats(
                sessionsImported + 1, sessionsSkipped,
                observationsImported, observationsSkipped,
                summariesImported, summariesSkipped,
                promptsImported, promptsSkipped,
                errors, errorMessages
            );
        }

        public ImportStats addSessionSkipped() {
            return new ImportStats(
                sessionsImported, sessionsSkipped + 1,
                observationsImported, observationsSkipped,
                summariesImported, summariesSkipped,
                promptsImported, promptsSkipped,
                errors, errorMessages
            );
        }

        public ImportStats addObservationResult(ImportService.BulkImportResult result) {
            return new ImportStats(
                sessionsImported, sessionsSkipped,
                observationsImported + result.imported(),
                observationsSkipped + result.duplicates(),
                summariesImported, summariesSkipped,
                promptsImported, promptsSkipped,
                errors + result.errors(),
                mergeErrors(errorMessages, result.errorMessages())
            );
        }

        public ImportStats addSummaryImported() {
            return new ImportStats(
                sessionsImported, sessionsSkipped,
                observationsImported, observationsSkipped,
                summariesImported + 1, summariesSkipped,
                promptsImported, promptsSkipped,
                errors, errorMessages
            );
        }

        public ImportStats addSummarySkipped() {
            return new ImportStats(
                sessionsImported, sessionsSkipped,
                observationsImported, observationsSkipped,
                summariesImported, summariesSkipped + 1,
                promptsImported, promptsSkipped,
                errors, errorMessages
            );
        }

        public ImportStats addPromptResult(ImportService.BulkImportResult result) {
            return new ImportStats(
                sessionsImported, sessionsSkipped,
                observationsImported, observationsSkipped,
                summariesImported, summariesSkipped,
                promptsImported + result.imported(),
                promptsSkipped + result.duplicates(),
                errors + result.errors(),
                mergeErrors(errorMessages, result.errorMessages())
            );
        }

        public ImportStats addError(String message) {
            List<String> newErrors = new ArrayList<>(this.errorMessages);
            newErrors.add(message);
            return new ImportStats(
                sessionsImported, sessionsSkipped,
                observationsImported, observationsSkipped,
                summariesImported, summariesSkipped,
                promptsImported, promptsSkipped,
                errors + 1, newErrors
            );
        }

        private static List<String> mergeErrors(List<String> existing, List<String> newErrors) {
            List<String> merged = new ArrayList<>(existing);
            if (newErrors != null) {
                merged.addAll(newErrors);
            }
            return merged;
        }
    }

    // ==========================================================================
    // API Endpoints
    // ==========================================================================

    /**
     * Bulk import all data types in a single request.
     *
     * POST /api/import
     */
    @Transactional
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> bulkImport(@RequestBody BulkImportRequest request) {
        log.info("Bulk import request: {} sessions, {} observations, {} summaries, {} prompts",
            request.sessions().size(),
            request.observations().size(),
            request.summaries().size(),
            request.prompts().size());

        ImportStats stats = new ImportStats();

        // Import sessions first (other imports depend on sessions)
        for (ImportService.SessionImportData session : request.sessions()) {
            try {
                ImportService.ImportResult result = importService.importSession(session);
                if (result.imported()) {
                    stats = stats.addSessionImported();
                } else {
                    stats = stats.addSessionSkipped();
                }
            } catch (Exception e) {
                log.error("Error importing session: {}", e.getMessage());
                stats = stats.addError(e.getMessage());
            }
        }

        // Import observations
        if (!request.observations().isEmpty()) {
            ImportService.BulkImportResult obsResult = importService.importObservations(request.observations());
            stats = stats.addObservationResult(obsResult);
        }

        // Import summaries
        for (ImportService.SummaryImportData summary : request.summaries()) {
            try {
                ImportService.ImportResult result = importService.importSummary(summary);
                if (result.imported()) {
                    stats = stats.addSummaryImported();
                } else {
                    stats = stats.addSummarySkipped();
                }
            } catch (Exception e) {
                log.error("Error importing summary: {}", e.getMessage());
                stats = stats.addError(e.getMessage());
            }
        }

        // Import user prompts
        if (!request.prompts().isEmpty()) {
            ImportService.BulkImportResult promptResult = importService.importUserPrompts(request.prompts());
            stats = stats.addPromptResult(promptResult);
        }

        log.info("Bulk import complete: {} sessions, {} observations, {} summaries, {} prompts imported",
            stats.sessionsImported(),
            stats.observationsImported(),
            stats.summariesImported(),
            stats.promptsImported());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "stats", Map.of(
                "sessionsImported", stats.sessionsImported(),
                "sessionsSkipped", stats.sessionsSkipped(),
                "observationsImported", stats.observationsImported(),
                "observationsSkipped", stats.observationsSkipped(),
                "summariesImported", stats.summariesImported(),
                "summariesSkipped", stats.summariesSkipped(),
                "promptsImported", stats.promptsImported(),
                "promptsSkipped", stats.promptsSkipped(),
                "errors", stats.errors()
            )
        ));
    }

    /**
     * Import sessions only.
     *
     * POST /api/import/sessions
     */
    @PostMapping(value = "/sessions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importSessions(@RequestBody List<ImportService.SessionImportData> sessions) {
        log.info("Importing {} sessions", sessions.size());

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (ImportService.SessionImportData session : sessions) {
            try {
                ImportService.ImportResult result = importService.importSession(session);
                if (result.imported()) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "imported", imported,
            "skipped", skipped,
            "errors", errors.size(),
            "errorMessages", errors
        ));
    }

    /**
     * Import observations only.
     *
     * POST /api/import/observations
     */
    @PostMapping(value = "/observations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importObservations(@RequestBody List<ImportService.ObservationImportData> observations) {
        log.info("Importing {} observations", observations.size());

        ImportService.BulkImportResult result = importService.importObservations(observations);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "imported", result.imported(),
            "skipped", result.duplicates(),
            "errors", result.errors(),
            "errorMessages", result.errorMessages()
        ));
    }

    /**
     * Import summaries only.
     *
     * POST /api/import/summaries
     */
    @PostMapping(value = "/summaries", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importSummaries(@RequestBody List<ImportService.SummaryImportData> summaries) {
        log.info("Importing {} summaries", summaries.size());

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (ImportService.SummaryImportData summary : summaries) {
            try {
                ImportService.ImportResult result = importService.importSummary(summary);
                if (result.imported()) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "imported", imported,
            "skipped", skipped,
            "errors", errors.size(),
            "errorMessages", errors
        ));
    }

    /**
     * Import user prompts only.
     *
     * POST /api/import/prompts
     */
    @PostMapping(value = "/prompts", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importPrompts(@RequestBody List<ImportService.UserPromptImportData> prompts) {
        log.info("Importing {} user prompts", prompts.size());

        ImportService.BulkImportResult result = importService.importUserPrompts(prompts);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "imported", result.imported(),
            "skipped", result.duplicates(),
            "errors", result.errors(),
            "errorMessages", result.errorMessages()
        ));
    }
}
