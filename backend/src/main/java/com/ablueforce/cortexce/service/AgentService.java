package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.common.LogHelper;
import com.ablueforce.cortexce.common.LogMarkers;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.exception.DataValidationException;
import com.ablueforce.cortexce.exception.RetryableException;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.util.XmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent service — orchestrates LLM interactions and observation/summary persistence.
 * <p>
 * This is the "core brain" per the cookbook. In production, it would:
 * 1. Receive tool use events from the thin proxy
 * 2. Build prompts using the template system
 * 3. Call LLM (Gemini/Anthropic) via Spring AI
 * 4. Parse XML responses
 * 5. Persist observations/summaries to PostgreSQL
 * <p>
 * <b>Refactored (2026-03-19):</b> Delegates to {@link SessionManagementService} (session)
 * and {@link TemplateService} (prompts). Controllers call specialized services directly.
 * <p>
 * Responsibilities in this class:
 * - Tool use processing (async)
 * - Observation persistence
 * - Pending message queue management
 * - Deduplication
 *
 * @author Cortex CE Team
 */
@Service
public class AgentService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    @Override
    public Logger getLogger() {
        return log;
    }

    // ========================================================================
    // Dependencies (repositories and infrastructure)
    // ========================================================================

    @Autowired
    private SessionManagementService sessionManagementService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PendingMessageRepository pendingMessageRepository;

    @Autowired
    private SSEBroadcaster sseBroadcaster;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private LlmService llmService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ContextCacheService contextCacheService;

    @Value("${claudemem.max-retries:3}")
    private int maxRetries;

    // ========================================================================
    // Tool use processing
    // ========================================================================

    /**
     * Process tool use event asynchronously.
     */
    @Async
    public void processToolUseAsync(UUID sessionDbId, String contentSessionId,
                                     String toolName, String toolInput, String toolResponse,
                                     String cwd, Integer promptNumber) {
        UUID pendingId = null;
        PendingMessageEntity pending = null;

        if (sessionDbId == null) {
            if (contentSessionId == null) {
                logFailure("Cannot process tool use: both sessionDbId and contentSessionId are null");
                return;
            }
            SessionEntity session = sessionManagementService.ensureSession(contentSessionId, cwd, null);
            sessionDbId = session.getId();
            log.debug("Resolved sessionDbId from contentSessionId via ensureSession: {}", sessionDbId);
        }

        try {
            // Dedup — skip if same session+tool+input already exists
            String toolInputHash = calculateToolInputHash(toolInput);
            boolean exists = pendingMessageRepository.existsBySessionAndTool(
                contentSessionId,
                toolName != null ? toolName : "",
                toolInputHash
            );
            if (exists) {
                logDataIn("Duplicate tool-use event skipped: session={}, tool={}", contentSessionId, toolName);
                return;
            }

            // Enqueue for crash recovery
            pending = new PendingMessageEntity();
            pending.setSessionDbId(sessionDbId);
            pending.setContentSessionId(contentSessionId);
            pending.setMessageType("observation");
            pending.setToolName(toolName);
            pending.setToolInput(toolInput);
            pending.setToolInputHash(toolInputHash);
            pending.setToolResponse(toolResponse);
            pending.setCwd(cwd);
            pending.setPromptNumber(promptNumber);
            pending.setStatus("pending");
            pending.setRetryCount(0);
            pending.setCreatedAtEpoch(Instant.now().toEpochMilli());
            pending = pendingMessageRepository.save(pending);
            pendingId = pending.getId();

            logDataIn("Processing tool use event: session={}, tool={}, pendingId={}", contentSessionId, toolName, pendingId);

            // Mark as processing
            pending.setStatus("processing");
            pending.setStartedProcessingAtEpoch(Instant.now().toEpochMilli());
            pendingMessageRepository.save(pending);

            // Get session context
            Optional<SessionEntity> sessionOpt = sessionManagementService.findByContentSessionId(contentSessionId);
            String userPrompt = sessionOpt.map(SessionEntity::getUserPrompt).orElse("");
            String projectPath = sessionOpt.map(SessionEntity::getProjectPath).orElse(cwd);

            // Build prompts using template service
            String now = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC));
            String systemPrompt = templateService.getInitPromptTemplate()
                .replace("{{userPrompt}}", templateService.escapeTemplateValue(userPrompt))
                .replace("{{date}}", now);

            String userMsg = templateService.getObservationPromptTemplate()
                .replace("{{toolName}}", templateService.escapeTemplateValue(toolName))
                .replace("{{occurredAt}}", now)
                .replace("{{cwd}}", templateService.escapeTemplateValue(cwd))
                .replace("{{toolInput}}", templateService.escapeTemplateValue(
                    templateService.truncate(toolInput, Constants.MAX_TOOL_CONTENT_LENGTH)))
                .replace("{{toolOutput}}", templateService.escapeTemplateValue(
                    templateService.truncate(toolResponse, Constants.MAX_TOOL_CONTENT_LENGTH)));

            // Call LLM
            LlmService.LlmResponse llmResponse = llmService.chatCompletionWithUsage(systemPrompt, userMsg);
            String llmContent = llmResponse.content();
            int discoveryTokens = llmResponse.totalTokens();
            log.debug("LLM response: {}, tokens: {}", llmContent, discoveryTokens);

            // Check for skip
            String skipReason = XmlParser.extractTag(llmContent, "skip");
            if (skipReason != null) {
                logHappyPath("LLM skipped observation: {}", skipReason);
                pending.setStatus("skipped");
                pending.setCompletedAtEpoch(Instant.now().toEpochMilli());
                pendingMessageRepository.save(pending);
                return;
            }

            // Parse and save observation
            XmlParser.ParsedObservation parsed = XmlParser.parseObservation(llmContent);
            saveObservation(contentSessionId, projectPath, parsed, promptNumber, discoveryTokens);

            // Mark context cache for refresh
            contextCacheService.markForRefresh(projectPath);

            pending.setStatus("processed");
            pending.setCompletedAtEpoch(Instant.now().toEpochMilli());
            pendingMessageRepository.save(pending);
            logSuccess("Observation saved for tool={} in session={}", toolName, contentSessionId);

        } catch (RetryableException e) {
            logHappyPath("Retryable error processing tool use: {}", e.getMessage());
            if (pendingId != null) {
                pendingMessageRepository.markFailedWithRetry(
                    pendingId, maxRetries, Instant.now().toEpochMilli());
            }
        } catch (DataValidationException e) {
            logFailure("Data validation error (no retry): {}", e.getMessage());
            if (pending != null) {
                pending.setStatus("failed");
                pending.setCompletedAtEpoch(Instant.now().toEpochMilli());
                pendingMessageRepository.save(pending);
            }
        } catch (DataIntegrityViolationException e) {
            logDataIn("Duplicate pending message detected (concurrent insert): session={}, tool={}", contentSessionId, toolName);
        } catch (Exception e) {
            logFailure("Unexpected error processing tool use", e);
            boolean isRetryable = isRetryableException(e);
            if (pending != null) {
                if (isRetryable) {
                    pendingMessageRepository.markFailedWithRetry(
                        pendingId, maxRetries, Instant.now().toEpochMilli());
                } else {
                    pending.setStatus("failed");
                    pending.setCompletedAtEpoch(Instant.now().toEpochMilli());
                    pendingMessageRepository.save(pending);
                }
            }
        }
    }

    // ========================================================================
    // Observation persistence
    // ========================================================================

    /**
     * Save an observation directly (from parsed XML or API input).
     */
    public ObservationEntity saveObservation(String contentSessionId, String projectPath,
                                              XmlParser.ParsedObservation parsed, Integer promptNumber,
                                              int discoveryTokens) {
        // Calculate content hash for deduplication
        String contentForHash = buildContentForHash(parsed.title, parsed.narrative, parsed.facts, parsed.concepts);
        String contentHash = calculateContentHash(contentForHash);

        // Check for duplicate within 30-second window
        long windowStart = Instant.now().toEpochMilli() - 30000;
        Optional<ObservationEntity> existing = observationRepository.findDuplicateByContentHash(contentHash, windowStart);
        if (existing.isPresent()) {
            log.debug(LogMarkers.HAPPY_PATH + "Duplicate observation within 30s window, returning existing: {}",
                existing.get().getId());
            return existing.get();
        }

        ObservationEntity obs = new ObservationEntity();
        obs.setContentSessionId(contentSessionId);
        obs.setProjectPath(projectPath);
        obs.setType(parsed.type != null ? parsed.type : "change");
        obs.setTitle(parsed.title);
        obs.setSubtitle(parsed.subtitle);
        obs.setContent(parsed.narrative);
        obs.setFacts(parsed.facts);
        obs.setConcepts(parsed.concepts);
        obs.setFilesRead(parsed.filesRead);
        obs.setFilesModified(parsed.filesModified);
        // V14: Source and extracted data
        if (parsed.source != null) {
            obs.setSource(parsed.source);
        }
        if (parsed.extractedData != null) {
            obs.setExtractedData(parsed.extractedData);
        }
        obs.setPromptNumber(promptNumber);
        obs.setCreatedAtEpoch(Instant.now().toEpochMilli());
        obs.setContentHash(contentHash);

        // Set discoveryTokens
        if (discoveryTokens > 0) {
            obs.setDiscoveryTokens(discoveryTokens);
        } else {
            int estimatedTokens = tokenService.calculateObservationTokens(obs) * 8;
            obs.setDiscoveryTokens(estimatedTokens);
            log.debug("Estimated discovery tokens: {} (actual usage unavailable)", estimatedTokens);
        }

        // Generate embedding
        generateEmbedding(parsed, obs);

        ObservationEntity saved = observationRepository.save(obs);

        // Broadcast SSE event
        java.util.Map<String, Object> eventData = new java.util.HashMap<>();
        eventData.put("type", "new_observation");
        eventData.put("observation", saved);
        sseBroadcaster.broadcast(eventData, "new_observation");

        return saved;
    }

    // ========================================================================
    // Pending message processing
    // ========================================================================

    /**
     * Check if any session is actively processing.
     */
    public boolean isAnySessionProcessing() {
        return pendingMessageRepository.countByStatus("processing") > 0;
    }

    /**
     * Process a pending message by its ID.
     * Called by PendingMessageEventListener (via Spring Event).
     */
    public void processPendingMessage(UUID pendingMessageId) {
        PendingMessageEntity pending = pendingMessageRepository.findById(pendingMessageId).orElse(null);

        if (pending == null) {
            log.warn("Pending message not found: {}", pendingMessageId);
            return;
        }

        if (!"pending".equals(pending.getStatus())) {
            log.debug("Pending message {} already processed, status: {}", pendingMessageId, pending.getStatus());
            return;
        }

        log.info("Processing pending message: {}", pendingMessageId);

        pending.setStatus("processing");
        pending.setStartedProcessingAtEpoch(System.currentTimeMillis());
        pendingMessageRepository.save(pending);

        try {
            String contentSessionId = pending.getContentSessionId();
            String toolName = pending.getToolName();
            String toolInput = pending.getToolInput();
            String toolResponse = pending.getToolResponse();
            String cwd = pending.getCwd();
            Integer promptNumber = pending.getPromptNumber();

            // Get session context
            Optional<SessionEntity> sessionOpt = sessionManagementService.findByContentSessionId(contentSessionId);
            String userPrompt = sessionOpt.map(SessionEntity::getUserPrompt).orElse("");
            String projectPath = sessionOpt.map(SessionEntity::getProjectPath).orElse(cwd);

            // Build prompts
            String now = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC));
            String systemPrompt = templateService.getInitPromptTemplate()
                .replace("{{userPrompt}}", templateService.escapeTemplateValue(userPrompt))
                .replace("{{date}}", now);

            String userMsg = templateService.getObservationPromptTemplate()
                .replace("{{toolName}}", templateService.escapeTemplateValue(toolName))
                .replace("{{occurredAt}}", now)
                .replace("{{cwd}}", templateService.escapeTemplateValue(cwd))
                .replace("{{toolInput}}", templateService.escapeTemplateValue(
                    templateService.truncate(toolInput, Constants.MAX_TOOL_CONTENT_LENGTH)))
                .replace("{{toolOutput}}", templateService.escapeTemplateValue(
                    templateService.truncate(toolResponse, Constants.MAX_TOOL_CONTENT_LENGTH)));

            // Call LLM
            LlmService.LlmResponse llmResponse = llmService.chatCompletionWithUsage(systemPrompt, userMsg);
            String llmContent = llmResponse.content();
            int discoveryTokens = llmResponse.totalTokens();

            // Check for skip
            String skipReason = XmlParser.extractTag(llmContent, "skip");
            if (skipReason != null) {
                logHappyPath("LLM skipped observation: {}", skipReason);
                pending.setStatus("skipped");
                pending.setCompletedAtEpoch(System.currentTimeMillis());
                pendingMessageRepository.save(pending);
                return;
            }

            // Parse and save
            XmlParser.ParsedObservation parsed = XmlParser.parseObservation(llmContent);
            saveObservation(contentSessionId, projectPath, parsed, promptNumber, discoveryTokens);

            contextCacheService.markForRefresh(projectPath);

            pending.setStatus("processed");
            pending.setCompletedAtEpoch(System.currentTimeMillis());
            pendingMessageRepository.save(pending);

            logSuccess("Processed pending message: {}", pendingMessageId);

        } catch (Exception e) {
            log.error("Failed to process pending message: {}", pendingMessageId, e);
            pending.setStatus("failed");
            pending.setCompletedAtEpoch(System.currentTimeMillis());
            pendingMessageRepository.save(pending);
        }
    }

    /**
     * Get the depth of the pending message queue.
     */
    public long getQueueDepth() {
        return pendingMessageRepository.countByStatus("pending");
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    /**
     * Check if exception is retryable.
     */
    private boolean isRetryableException(Exception e) {
        if (e instanceof java.net.SocketTimeoutException ||
            e instanceof java.net.ConnectException ||
            e instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        if (e.getMessage() != null && e.getMessage().contains("LLM")) {
            return true;
        }
        return false;
    }

    /**
     * Calculate SHA-256 hash for deduplication (collision-resistant).
     */
    private String calculateToolInputHash(String toolInput) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toolInput != null ? toolInput.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Critical: SHA-256 not available", e);
        }
    }

    /**
     * Build content string for observation hash calculation.
     */
    private String buildContentForHash(String title, String narrative, List<String> facts, List<String> concepts) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append("|");
        if (narrative != null) sb.append(narrative).append("|");
        if (facts != null && !facts.isEmpty()) sb.append(String.join(",", facts)).append("|");
        if (concepts != null && !concepts.isEmpty()) sb.append(String.join(",", concepts));
        return sb.toString();
    }

    /**
     * Calculate SHA-256 hash for observation content.
     * Returns first 16 characters to fit VARCHAR(16) column.
     */
    private String calculateContentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    /**
     * Generate embedding for observation.
     */
    private void generateEmbedding(XmlParser.ParsedObservation parsed, ObservationEntity obs) {
        try {
            String textToEmbed = buildEmbeddingText(parsed.title, parsed.narrative, parsed.facts);
            if (textToEmbed != null && !textToEmbed.isBlank()) {
                float[] vector = embeddingService.embed(textToEmbed);
                int dim = vector.length;
                switch (dim) {
                    case 768 -> obs.setEmbedding768(vector);
                    case 1024 -> obs.setEmbedding1024(vector);
                    case 1536 -> obs.setEmbedding1536(vector);
                    default -> log.warn("Unsupported embedding dimension: {} (supported: 768, 1024, 1536)", dim);
                }
                obs.setEmbeddingModelId(embeddingService.getModel());
            }
        } catch (org.springframework.web.reactive.function.client.WebClientRequestException e) {
            logHappyPath("Embedding service unavailable, saving observation without embedding: {}", e.getMessage());
        } catch (Exception e) {
            logHappyPath("Embedding generation failed for observation '{}', saving without embedding: {}",
                parsed.title, e.getMessage());
        }
    }

    /**
     * Build text for embedding from observation fields.
     */
    private String buildEmbeddingText(String title, String narrative, List<String> facts) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append(". ");
        if (narrative != null) sb.append(narrative).append(" ");
        if (facts != null && !facts.isEmpty()) {
            sb.append(String.join("; ", facts));
        }
        return sb.toString().trim();
    }
}
