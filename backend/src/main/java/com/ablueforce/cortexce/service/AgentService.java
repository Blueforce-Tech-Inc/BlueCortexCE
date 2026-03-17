package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.exception.DataValidationException;
import com.ablueforce.cortexce.exception.RetryableException;
import com.ablueforce.cortexce.common.LogHelper;
import org.springframework.dao.DataIntegrityViolationException;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.util.XmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
 * Currently provides direct persistence methods for the ingestion API.
 * LLM integration is a placeholder — plug in Spring AI ChatClient when ready.
 *
 * @TODO P2: AgentService violates SRP - split into:
 *   - ObservationService: Observation persistence and embedding
 *   - SummaryService: Summary persistence and generation
 *   - SessionService: Session lifecycle management
 *   - TemplateService: Template loading and validation
 */
@Service
public class AgentService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

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

    private String initPromptTemplate;
    private String observationPromptTemplate;
    private String summaryPromptTemplate;
    private String continuationPromptTemplate;

    // Runtime placeholders that MUST be present and replaced
    private static final java.util.Set<String> OBSERVATION_PLACEHOLDERS = java.util.Set.of(
        "{{toolName}}", "{{occurredAt}}", "{{cwd}}", "{{toolInput}}", "{{toolOutput}}"
    );
    private static final java.util.Set<String> CONTINUATION_PLACEHOLDERS = java.util.Set.of(
        "{{userPrompt}}", "{{date}}"
    );

    @jakarta.annotation.PostConstruct
    void loadPromptTemplates() {
        // Load prompts from synced resources directory
        // Prompts are synced from TS modes using: java/scripts/sync-prompts.sh
        initPromptTemplate = loadResource("prompts/init.txt");
        observationPromptTemplate = loadResource("prompts/observation.txt");
        summaryPromptTemplate = loadResource("prompts/summary.txt");
        continuationPromptTemplate = loadResource("prompts/continuation.txt");

        // Validate required placeholders (fail fast if missing)
        validatePlaceholders(observationPromptTemplate, "observation.txt", OBSERVATION_PLACEHOLDERS);
        validatePlaceholders(continuationPromptTemplate, "continuation.txt", CONTINUATION_PLACEHOLDERS);

        logSuccess("Prompt templates loaded and validated from synced TS resources");
    }

    private void validatePlaceholders(String template, String templateName, java.util.Set<String> required) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String placeholder : required) {
            if (!template.contains(placeholder)) {
                missing.add(placeholder);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Template '" + templateName + "' is missing required placeholders: " + missing
            );
        }
    }

    private String loadResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logFailure("Failed to load prompt template: {}", path, e);
            throw new RuntimeException("Missing prompt template: " + path, e);
        }
    }

    /**
     * Initialize or retrieve a session.
     * P1: Added null check to prevent NPE if save fails.
     */
    public SessionEntity initializeSession(String contentSessionId, String projectPath, String userPrompt) {
        return sessionRepository.findByContentSessionId(contentSessionId)
            .orElseGet(() -> {
                SessionEntity session = new SessionEntity();
                session.setContentSessionId(contentSessionId);
                session.setMemorySessionId(contentSessionId);
                session.setProjectPath(projectPath);
                session.setUserPrompt(userPrompt);
                session.setStartedAtEpoch(Instant.now().toEpochMilli());
                session.setStatus("active");
                SessionEntity saved = sessionRepository.save(session);
                if (saved == null) {
                    logFailure("Failed to save new session for contentSessionId: {}", contentSessionId);
                    throw new RuntimeException("Failed to create session: " + contentSessionId);
                }
                return saved;
            });
    }

    public SessionEntity ensureSession(String contentSessionId, String projectPath, String userPrompt) {
        return sessionRepository.findByContentSessionId(contentSessionId)
            .orElseGet(() -> {
                logDataIn("Creating session from ensureSession: {} (session was not initialized by SessionStart)", contentSessionId);
                SessionEntity session = new SessionEntity();
                session.setContentSessionId(contentSessionId);
                session.setMemorySessionId(contentSessionId);
                session.setProjectPath(projectPath);
                session.setUserPrompt(userPrompt);
                session.setStartedAtEpoch(Instant.now().toEpochMilli());
                session.setStatus("active");
                SessionEntity saved = sessionRepository.save(session);
                if (saved == null) {
                    logFailure("Failed to save new session for contentSessionId: {}", contentSessionId);
                    throw new RuntimeException("Failed to create session: " + contentSessionId);
                }
                return saved;
            });
    }

    /**
     * P1: Calculate SHA-256 hash for deduplication (collision-resistant).
     * Returns full 64-character hex string to prevent hash collisions.
     */
    private String calculateToolInputHash(String toolInput) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toolInput != null ? toolInput.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            // Return full 64-character hex string (256 bits / 4 = 64 hex chars)
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
            logFailure("SHA-256 algorithm not available - this should never happen in standard Java");
            throw new IllegalStateException("Critical: SHA-256 not available", e);
        }
    }

    /**
     * Build content string for observation hash calculation.
     * Combines key fields that define observation uniqueness.
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
            // Return first 16 characters to fit VARCHAR(16) column
            return hexString.toString().substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            logFailure("SHA-256 algorithm not available");
            return "";
        }
    }

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
            SessionEntity session = ensureSession(contentSessionId, cwd, null);
            sessionDbId = session.getId();
            log.debug("Resolved sessionDbId from contentSessionId via ensureSession: {}", sessionDbId);
        }

        try {
            // P1: Dedup — skip if same session+tool+input already exists (not failed)
            // Use full SHA-256 hex string for collision-resistant deduplication
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

            // Look up session for user prompt context
            SessionEntity session = sessionRepository.findByContentSessionId(contentSessionId).orElse(null);
            String userPrompt = session != null ? session.getUserPrompt() : "";
            String projectPath = session != null ? session.getProjectPath() : cwd;

            // Build the system prompt from init template
            // P0: Escape user-controlled content before template substitution to prevent injection
            // Template syntax like {{...}} is escaped to prevent injection attacks
            String now = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                OffsetDateTime.now(ZoneOffset.UTC));
            String systemPrompt = initPromptTemplate
                .replace("{{userPrompt}}", escapeTemplateValue(userPrompt))
                .replace("{{date}}", now);

            // Build the user prompt from observation template
            // P0: Escape all user-controlled inputs to prevent template injection
            String userMsg = observationPromptTemplate
                .replace("{{toolName}}", escapeTemplateValue(toolName))
                .replace("{{occurredAt}}", now)
                .replace("{{cwd}}", escapeTemplateValue(cwd))
                .replace("{{toolInput}}", escapeTemplateValue(truncate(toolInput, Constants.MAX_TOOL_CONTENT_LENGTH)))
                .replace("{{toolOutput}}", escapeTemplateValue(truncate(toolResponse, Constants.MAX_TOOL_CONTENT_LENGTH)));

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

            // Parse observation XML and save
            XmlParser.ParsedObservation parsed = XmlParser.parseObservation(llmContent);
            saveObservation(contentSessionId, projectPath, parsed, promptNumber, discoveryTokens);

            // Mark context cache for refresh so next session-start gets fresh context
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

            // Determine if retryable based on exception type
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

    /**
     * P1: Check if exception is retryable.
     */
    private boolean isRetryableException(Exception e) {
        // Network/timeouts are retryable
        if (e instanceof java.net.SocketTimeoutException ||
            e instanceof java.net.ConnectException ||
            e instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        // LLM service errors
        if (e.getMessage() != null && e.getMessage().contains("LLM")) {
            return true;
        }
        return false;
    }

    /**
     * Save an observation directly (from parsed XML or API input).
     */
    public ObservationEntity saveObservation(String memorySessionId, String projectPath,
                                              XmlParser.ParsedObservation parsed, Integer promptNumber,
                                              int discoveryTokens) {
        // Calculate content hash for deduplication
        // Hash based on: title + content + facts + concepts (key observation content)
        String contentForHash = buildContentForHash(parsed.title, parsed.narrative, parsed.facts, parsed.concepts);
        String contentHash = calculateContentHash(contentForHash);
        
        // Check for duplicate within 30-second window (aligns with TypeScript DEDUP_WINDOW_MS = 30000)
        long windowStart = Instant.now().toEpochMilli() - 30000;
        Optional<ObservationEntity> existing = observationRepository.findDuplicateByContentHash(contentHash, windowStart);
        if (existing.isPresent()) {
            logHappyPath("Found duplicate observation within 30s window, returning existing: {}", existing.get().getId());
            return existing.get();
        }

        ObservationEntity obs = new ObservationEntity();
        obs.setMemorySessionId(memorySessionId);
        obs.setProjectPath(projectPath);
        obs.setType(parsed.type != null ? parsed.type : "change");
        obs.setTitle(parsed.title);
        obs.setSubtitle(parsed.subtitle);
        obs.setContent(parsed.narrative);
        obs.setFacts(parsed.facts);
        obs.setConcepts(parsed.concepts);
        obs.setFilesRead(parsed.filesRead);
        obs.setFilesModified(parsed.filesModified);
        obs.setPromptNumber(promptNumber);
        obs.setCreatedAtEpoch(Instant.now().toEpochMilli());
        obs.setContentHash(contentHash);

        // Set discoveryTokens: use actual LLM usage if available, otherwise estimate
        // Note: discoveryTokens <= 0 means LLM didn't return usage (some models/APIs skip this)
        // In that case, estimate as read_tokens * 8 (typical compression ratio)
        if (discoveryTokens > 0) {
            obs.setDiscoveryTokens(discoveryTokens);
        } else {
            int estimatedTokens = tokenService.calculateObservationTokens(obs) * 8;
            obs.setDiscoveryTokens(estimatedTokens);
            log.debug("Estimated discovery tokens: {} (actual usage unavailable)", estimatedTokens);
        }

        // Auto-generate embedding from title + narrative
        // P1: Log embedding failures at WARN to enable alerting/monitoring
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

        ObservationEntity saved = observationRepository.save(obs);

        // Broadcast SSE event with type field
        // TypeScript useSSE.ts expects "observation" key, not "data"
        Map<String, Object> eventData = new java.util.HashMap<>();
        eventData.put("type", "new_observation");
        eventData.put("observation", saved);
        sseBroadcaster.broadcast(eventData, "new_observation");

        return saved;
    }

    /**
     * Save a summary directly (from parsed XML or API input).
     */
    public SummaryEntity saveSummary(String memorySessionId, String projectPath,
                                      XmlParser.ParsedSummary parsed, Integer promptNumber) {
        SummaryEntity summary = new SummaryEntity();
        summary.setMemorySessionId(memorySessionId);
        summary.setProjectPath(projectPath);
        summary.setRequest(parsed.request);
        summary.setInvestigated(parsed.investigated);
        summary.setLearned(parsed.learned);
        summary.setCompleted(parsed.completed);
        summary.setNextSteps(parsed.nextSteps);
        summary.setNotes(parsed.notes);
        summary.setPromptNumber(promptNumber);
        summary.setCreatedAtEpoch(Instant.now().toEpochMilli());

        SummaryEntity saved = summaryRepository.save(summary);

        // Broadcast SSE event with type field
        // TypeScript useSSE.ts expects "summary" key, not "data"
        Map<String, Object> eventData = new java.util.HashMap<>();
        eventData.put("type", "new_summary");
        eventData.put("summary", saved);
        sseBroadcaster.broadcast(eventData, "new_summary");

        return saved;
    }

    /**
     * Complete a session (sync — marks status only).
     */
    public void completeSession(String contentSessionId) {
        sessionRepository.findByContentSessionId(contentSessionId).ifPresent(session -> {
            session.setStatus("completed");
            session.setCompletedAtEpoch(Instant.now().toEpochMilli());
            session.setCompletedAt(OffsetDateTime.now());
            sessionRepository.save(session);
        });
    }

    /**
     * P0-1: Complete a session and asynchronously generate a summary.
     * Gathers all observations for the session, calls LLM to produce a summary,
     * parses the XML, and persists the SummaryEntity.
     *
     * @param contentSessionId       The session ID from Claude Code
     * @param lastAssistantMessage   The last assistant message (from transcript)
     */
    @Async
    public void completeSessionAsync(String contentSessionId, String lastAssistantMessage) {
        try {
            SessionEntity session = sessionRepository.findByContentSessionId(contentSessionId)
                .orElse(null);
            if (session == null) {
                logFailure("Cannot summarize: session not found for {}", contentSessionId);
                return;
            }

            if (!"completed".equals(session.getStatus())) {
                session.setStatus("completed");
                session.setCompletedAtEpoch(Instant.now().toEpochMilli());
                session.setCompletedAt(OffsetDateTime.now());
                sessionRepository.save(session);
            }

            if (lastAssistantMessage != null && !lastAssistantMessage.isEmpty()) {
                session.setLastAssistantMessage(lastAssistantMessage);
                sessionRepository.save(session);
                log.debug("Saved lastAssistantMessage for session {}", contentSessionId);
            }

            String memorySessionId = session.getMemorySessionId() != null
                ? session.getMemorySessionId() : contentSessionId;
            List<ObservationEntity> observations = observationRepository
                .findByMemorySessionIdOrderByCreatedAtEpochAsc(memorySessionId);

            if (observations.isEmpty()) {
                logDataIn("No observations to summarize for session {}", contentSessionId);
                return;
            }

            StringBuilder digest = new StringBuilder();
            for (int i = 0; i < observations.size(); i++) {
                ObservationEntity obs = observations.get(i);
                digest.append(String.format("[%d] %s: %s",
                    i + 1, obs.getType(), obs.getTitle()));
                if (obs.getSubtitle() != null) digest.append(" — ").append(obs.getSubtitle());
                digest.append("\n");
                if (obs.getContent() != null) digest.append("  ").append(obs.getContent()).append("\n");
                if (obs.getFacts() != null && !obs.getFacts().isEmpty()) {
                    obs.getFacts().forEach(f -> digest.append("  • ").append(f).append("\n"));
                }
            }

            String lastAssistantMsg = session.getLastAssistantMessage();
            String userPromptForSummary = summaryPromptTemplate
                .replace("{{lastAssistantMessage}}", lastAssistantMsg != null ? lastAssistantMsg : "(not available)");
            String systemPrompt = "You are a memory observer agent. Summarize the following session observations:\n\n" + digest;

            logDataIn("Generating summary for session {} ({} observations)", contentSessionId, observations.size());
            String llmResponse = llmService.chatCompletion(systemPrompt, userPromptForSummary);

            XmlParser.ParsedSummary parsed = XmlParser.parseSummary(llmResponse);
            int lastPromptNumber = observations.get(observations.size() - 1).getPromptNumber() != null
                ? observations.get(observations.size() - 1).getPromptNumber() : 0;
            saveSummary(memorySessionId, session.getProjectPath(), parsed, lastPromptNumber);

            contextCacheService.markForRefresh(session.getProjectPath());

            logSuccess("Summary saved for session {}", contentSessionId);

        } catch (Exception e) {
            logFailure("Failed to generate summary for session {}", contentSessionId, e);
        }
    }

    /**
     * Check if any session is actively processing.
     */
    public boolean isAnySessionProcessing() {
        return pendingMessageRepository.countByStatus("processing") > 0;
    }

    /**
     * Get the depth of the pending message queue.
     */
    public long getQueueDepth() {
        return pendingMessageRepository.countByStatus("pending");
    }

    /**
     * Escape template placeholder syntax in user-controlled content.
     * P0: Prevents template injection attacks by escaping {{ and }} patterns.
     *
     * @param input user-controlled input string
     * @return escaped string safe for template substitution
     */
    private String escapeTemplateValue(String input) {
        if (input == null) return "";
        // Escape {{ and }} to prevent template injection
        return input
            .replace("{{", "&#123;&#123;")
            .replace("}}", "&#125;&#125;")
            .replace("{{{{", "&#123;&#123;&#123;&#123;")
            .replace("}}}}", "&#125;&#125;&#125;&#125;");
    }

    /**
     * Truncate a string to maxLen chars for prompt safety.
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "... [truncated]";
    }

    /**
     * Build text for embedding from observation fields.
     */
    private String buildEmbeddingText(String title, String narrative, java.util.List<String> facts) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append(". ");
        if (narrative != null) sb.append(narrative).append(" ");
        if (facts != null && !facts.isEmpty()) {
            sb.append(String.join("; ", facts));
        }
        return sb.toString().trim();
    }
}
