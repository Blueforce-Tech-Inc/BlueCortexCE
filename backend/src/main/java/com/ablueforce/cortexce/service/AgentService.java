package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.common.LogHelper;
import com.ablueforce.cortexce.common.LogMarkers;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.util.XmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import java.util.Objects;
import java.time.Instant;

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

    // V17: Session wall-clock age guard - 4 hours max
    private static final long MAX_SESSION_AGE_MS = 4 * 60 * 60 * 1000;

    @Override
    public Logger getLogger() {
        return log;
    }

    // ========================================================================
    // Dependencies (repositories and infrastructure)
    // ========================================================================

    private final SessionManagementService sessionManagementService;
    private final TemplateService templateService;
    private final PendingMessageRepository pendingMessageRepository;
    private final SessionRepository sessionRepository;
    private final MemoryScopeService memoryScopeService;
    private final ConversationWriteService conversationWriteService;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;
    private final TokenService tokenService;
    private final ContextCacheService contextCacheService;

    @Value("${claudemem.max-retries:3}")
    private int maxRetries;

    public AgentService(SessionManagementService sessionManagementService,
                        TemplateService templateService,
                        PendingMessageRepository pendingMessageRepository,
                        SessionRepository sessionRepository,
                        MemoryScopeService memoryScopeService,
                        ConversationWriteService conversationWriteService,
                        EmbeddingService embeddingService,
                        LlmService llmService,
                        TokenService tokenService,
                        ContextCacheService contextCacheService) {
        this.sessionManagementService = sessionManagementService;
        this.templateService = templateService;
        this.pendingMessageRepository = pendingMessageRepository;
        this.sessionRepository = sessionRepository;
        this.memoryScopeService = memoryScopeService;
        this.conversationWriteService = conversationWriteService;
        this.embeddingService = embeddingService;
        this.llmService = llmService;
        this.tokenService = tokenService;
        this.contextCacheService = contextCacheService;
    }

    // ========================================================================
    // Tool use processing
    // ========================================================================

    /** Persist queue admission before returning success to the hook caller. */
    @Transactional
    public Optional<UUID> enqueueToolUse(UUID conversationId, String contentSessionId,
                                         String toolName, String toolInput, String toolResponse,
                                         String cwd, Integer promptNumber) {
        if (conversationId == null || sessionRepository.lockSessionForUpdate(conversationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        SessionEntity session = sessionRepository.findById(conversationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!Objects.equals(session.getContentSessionId(), contentSessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "tool-use external id does not match its Conversation");
        }
        if (!"active".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Conversation no longer accepts new tool-use events");
        }
        if (isSessionTooOld(session)) {
            markSessionExpired(session, "tool-use");
            return Optional.empty();
        }

        String toolInputHash = calculateToolInputHash(toolInput);
        if (pendingMessageRepository.countBySessionDbIdAndTool(
            conversationId, toolName != null ? toolName : "", toolInputHash) > 0) {
            return Optional.empty();
        }

        PendingMessageEntity pending = new PendingMessageEntity();
        pending.setSessionDbId(conversationId);
        pending.setContentSessionId(session.getContentSessionId());
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
        return Optional.of(pendingMessageRepository.saveAndFlush(pending).getId());
    }

    // ========================================================================
    // Observation persistence
    // ========================================================================

    /**
     * Save an observation directly (from parsed XML or API input).
     */
    /**
     * Persist an observation owned by a conversation.
     *
     * @param conversationId internal conversation UUID — the relation of record
     * @param platformSource the owning conversation's canonical platform
     * @param contentSessionId the external id, kept as a wire/diagnostic copy
     */
    public ObservationEntity saveObservation(java.util.UUID conversationId, String platformSource,
                                              String contentSessionId, String projectPath,
                                              XmlParser.ParsedObservation parsed, Integer promptNumber,
                                              int discoveryTokens) {
        return saveObservation(conversationId, null, platformSource, contentSessionId,
            projectPath, parsed, promptNumber, discoveryTokens);
    }

    private ObservationEntity saveObservation(java.util.UUID conversationId, UUID pendingId,
                                               String platformSource, String contentSessionId,
                                               String projectPath, XmlParser.ParsedObservation parsed,
                                               Integer promptNumber, int discoveryTokens) {
        // Calculate content hash for deduplication
        String contentForHash = buildContentForHash(parsed.title, parsed.narrative, parsed.facts, parsed.concepts);
        String contentHash = calculateContentHash(contentForHash);

        // Final duplicate check runs under the Conversation lock in the writer. Resolve the
        // Scope and generate embeddings before taking that lock to avoid holding it across
        // potentially slow local or remote embedding work.
        long windowStart = Instant.now().toEpochMilli() - 30000;
        java.util.UUID scopeId = memoryScopeService.resolveProjectScope(projectPath);

        ObservationEntity obs = new ObservationEntity();
        obs.setConversationId(conversationId);
        obs.setStandaloneKind(null);
        obs.setMemoryScopeId(scopeId);
        obs.setContentSessionId(contentSessionId);
        obs.setPlatformSource(platformSource);
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
        // platform_source is now taken from the owning conversation passed in by the caller;
        // re-deriving it from the external id would pick another platform's row.

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

        ObservationEntity saved = conversationWriteService.saveObservation(
            conversationId, pendingId, obs, windowStart);
        if (saved != obs) {
            log.debug(LogMarkers.HAPPY_PATH + "Duplicate observation within 30s window, returning existing: {}",
                saved.getId());
        }
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
     * Returns whether a session still has queued or actively processing work.
     * Viewer deletion uses this guard to avoid deleting data while an async
     * observation is still able to write into the session.
     */
    public boolean isSessionProcessing(UUID conversationId) {
        // Keyed on the internal UUID: two platforms may share an external id, so a bare-id
        // check would let one platform's queue block (or fail to block) the other's delete.
        return conversationId != null
            && pendingMessageRepository.countPendingForSession(
                conversationId, List.of("pending", "processing")) > 0;
    }

    /**
     * Process a pending message by its ID.
     * Called by PendingMessageEventListener (via Spring Event).
     */
    public void processPendingMessage(UUID pendingMessageId) {
        PendingMessageEntity pending = conversationWriteService.claimObservationJob(pendingMessageId).orElse(null);
        if (pending == null) {
            log.debug("Pending Observation job {} is missing, closed, or already claimed", pendingMessageId);
            return;
        }

        log.info("Processing pending message: {}", pendingMessageId);

        try {
            String contentSessionId = pending.getContentSessionId();
            String toolName = pending.getToolName();
            String toolInput = pending.getToolInput();
            String toolResponse = pending.getToolResponse();
            String cwd = pending.getCwd();
            Integer promptNumber = pending.getPromptNumber();

            // Shared: build prompts, call LLM, parse, and save
            boolean saved = callLlmAndSaveObservation(
                pending.getSessionDbId(), contentSessionId, toolName, toolInput,
                toolResponse, cwd, promptNumber, pending);
            if (!saved) return;

            logSuccess("Processed pending message: {}", pendingMessageId);

        } catch (DataIntegrityViolationException e) {
            logDataIn("Duplicate pending message detected (concurrent insert): id={}", pendingMessageId);
            conversationWriteService.markObservationFailed(
                pendingMessageId, maxRetries, System.currentTimeMillis());
        } catch (Exception e) {
            log.error("Error processing pending Observation {}: {}",
                pendingMessageId, e.getClass().getSimpleName());
            conversationWriteService.markObservationFailed(
                pendingMessageId, isRetryableException(e) ? maxRetries : 1, System.currentTimeMillis());
        }
    }

    /**
     * Shared observation processing: build prompts, call LLM, parse, and save.
     * Used by both processToolUseAsync and processPendingMessage.
     *
     * @return true if observation was saved, false if skipped
     */
    private boolean callLlmAndSaveObservation(java.util.UUID conversationId, String contentSessionId,
                                                String toolName,
                                                String toolInput, String toolResponse,
                                                String cwd, Integer promptNumber,
                                                PendingMessageEntity pending) {
        // Conversation context is read through the internal UUID. The external id could match
        // a row owned by another platform once duplicate ids are legal.
        Optional<SessionEntity> sessionOpt = sessionManagementService.findById(conversationId);
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
        log.debug("Observation extraction completed: response_chars={}, tokens={}",
            llmContent == null ? 0 : llmContent.length(), discoveryTokens);

        // Check for skip
        String skipReason = XmlParser.extractTag(llmContent, "skip");
        if (skipReason != null) {
            logHappyPath("LLM skipped observation");
            conversationWriteService.markObservationSkipped(pending.getId(), pending.getSessionDbId());
            return false;
        }

        // Parse and save observation
        XmlParser.ParsedObservation parsed = XmlParser.parseObservation(llmContent);
        saveObservation(conversationId, pending.getId(),
            sessionOpt.map(SessionEntity::getPlatformSource).orElse("claude"),
            contentSessionId, projectPath, parsed, promptNumber, discoveryTokens);

        // Mark context cache for refresh
        contextCacheService.markForRefresh(projectPath);
        return true;
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
     * Returns false for unrecoverable auth errors to prevent endless retry loops
     * (aligned with TS v10.6.3 commit 07ab7000).
     */
    private boolean isRetryableException(Exception e) {
        // Unrecoverable auth errors - do NOT retry (prevents 77K+ retry loops)
        if (e.getMessage() != null) {
            String msg = e.getMessage();
            if (msg.contains("API_KEY_INVALID") ||
                msg.contains("API key expired") ||
                msg.contains("API key not valid") ||
                msg.contains("PERMISSION_DENIED") ||
                msg.contains("Gemini API error: 400") ||
                msg.contains("Gemini API error: 401") ||
                msg.contains("Gemini API error: 403")) {
                return false;
            }
        }
        // Network/timeouts are retryable
        if (e instanceof java.net.SocketTimeoutException ||
            e instanceof java.net.ConnectException ||
            e instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        // LLM service errors are retryable
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
     * Returns null for empty content to avoid false-positive dedup.
     */
    private String calculateContentHash(String content) {
        if (content == null || content.isEmpty()) {
            // All fields were null — return null to skip dedup (no meaningful content to hash)
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
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
            logHappyPath("Embedding service unavailable, saving observation without embedding: {}",
                e.getClass().getSimpleName());
        } catch (Exception e) {
            logHappyPath("Embedding generation failed for observation, saving without embedding: {}",
                e.getClass().getSimpleName());
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

    // ========================================================================
    // V17: Session Wall-Clock Age Guard
    // ========================================================================

    /**
     * Check if a session exceeds the maximum age limit (4 hours).
     * Sessions older than MAX_SESSION_AGE_MS are considered stale and should not receive new work.
     */
    private boolean isSessionTooOld(SessionEntity session) {
        if (session == null || session.getStartedAtEpoch() == null) {
            return false; // Can't determine age, allow work
        }
        long sessionAgeMs = Instant.now().toEpochMilli() - session.getStartedAtEpoch();
        return sessionAgeMs > MAX_SESSION_AGE_MS;
    }

    /**
     * Mark a session as expired (too old to receive new work).
     */
    private void markSessionExpired(SessionEntity session, String reason) {
        if (session == null) return;
        log.warn("Session exceeded max age limit - marking as expired: session_id={}, reason={}, age_hours={}",
            session.getContentSessionId(), reason,
            (Instant.now().toEpochMilli() - session.getStartedAtEpoch()) / 3600000.0);
        session.setStatus("expired");
        sessionRepository.save(session);
    }
}
