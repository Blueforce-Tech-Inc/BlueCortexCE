package com.ablueforce.cortexce.ai.tools;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.Experience;
import com.ablueforce.cortexce.client.dto.ExperienceRequest;
import com.ablueforce.cortexce.client.dto.ObservationUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring AI Tools for on-demand memory retrieval from Cortex CE.
 * <p>
 * Unlike {@link com.ablueforce.cortexce.ai.advisor.CortexMemoryAdvisor} which injects
 * memory context on every request, these tools are called only when the AI decides
 * it needs to look up past experiences.
 * <p>
 * <b>Usage</b>: Add to ChatClient explicitly (not auto-injected):
 * <pre>{@code
 * chatClient.prompt()
 *     .tools(cortexMemoryTools)
 *     .user("How do I fix the login bug?")
 *     .call();
 * }</pre>
 * <p>
 * <b>Configuration</b>: Bean is created only when {@code cortex.mem.memory-tools-enabled=true}.
 *
 * @see com.ablueforce.cortexce.ai.advisor.CortexMemoryAdvisor
 */
public class CortexMemoryTools {

    private static final Logger log = LoggerFactory.getLogger(CortexMemoryTools.class);

    private final CortexMemClient cortexClient;
    private final String defaultProjectPath;
    private final int defaultCount;

    public CortexMemoryTools(CortexMemClient cortexClient, String defaultProjectPath, int defaultCount) {
        this.cortexClient = cortexClient;
        this.defaultProjectPath = defaultProjectPath != null ? defaultProjectPath : "";
        this.defaultCount = Math.max(1, Math.min(defaultCount, 10));
    }

    /**
     * Search for relevant past experiences related to a task.
     * Use this when you need to recall how similar problems were solved before.
     *
     * @param task   Description of the current task or problem (e.g. "fix login timeout")
     * @param count  Number of experiences to retrieve (optional, default 4, max 10)
     * @return Formatted text of relevant past experiences
     */
    @Tool(description = "Search for relevant past experiences related to a task. " +
        "Use when you need to recall how similar problems were solved before. " +
        "Returns formatted descriptions of past task outcomes.")
    public String searchMemories(
        @ToolParam(description = "Description of the current task or problem to search for") String task,
        @ToolParam(description = "Number of past experiences to retrieve (optional, 1-10, default 4)") Integer count) {
        if (task == null || task.isBlank()) {
            return "No search task provided.";
        }
        int effectiveCount = (count == null || count <= 0) ? defaultCount : Math.min(count, 10);
        String project = resolveProjectPath();

        try {
            List<Experience> experiences = cortexClient.retrieveExperiences(
                ExperienceRequest.builder()
                    .task(task)
                    .project(project)
                    .count(effectiveCount)
                    .build());

            if (experiences == null || experiences.isEmpty()) {
                return "No relevant past experiences found for: " + task;
            }

            return experiences.stream()
                .map(e -> formatExperience(e))
                .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            log.warn("Failed to search memories: {}", e.getMessage());
            return "Error searching memories: " + e.getMessage();
        }
    }

    /**
     * Get ICL-formatted memory context for a task.
     * Returns a ready-to-use prompt snippet with historical experiences.
     *
     * @param task Description of the current task
     * @return ICL prompt text, or empty if no experiences found
     */
    @Tool(description = "Get in-context learning (ICL) formatted memory for a task. " +
        "Returns a preformatted prompt with historical experiences. " +
        "Use when you want a compact summary of past approaches.")
    public String getMemoryContext(
        @ToolParam(description = "Description of the current task") String task) {
        if (task == null || task.isBlank()) {
            return "";
        }
        String project = resolveProjectPath();

        try {
            var result = cortexClient.buildICLPrompt(
                com.ablueforce.cortexce.client.dto.ICLPromptRequest.builder()
                    .task(task)
                    .project(project)
                    .build());
            return result != null && result.prompt() != null ? result.prompt() : "";
        } catch (Exception e) {
            log.warn("Failed to get memory context: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    // ==================== Memory Management Tools (V14) ====================

    /**
     * Update an existing memory/observation.
     * Use this to correct errors, add missing information, or mark important memories.
     *
     * @param observationId UUID of the observation to update
     * @param title New title (optional, pass null to keep existing)
     * @param content New content/narrative (optional, pass null to keep existing)
     * @param concepts New concepts/tags list (optional, pass null to keep existing)
     * @param facts New facts list (optional, pass null to keep existing)
     * @param source New source attribution (optional, pass null to keep existing)
     * @return Success or error message
     */
    @Tool(description = "Update an existing memory/observation. " +
        "Use to correct errors, add missing information, or update concepts/tags. " +
        "Requires the observation ID from a previous search result.")
    public String updateMemory(
        @ToolParam(description = "UUID of the observation to update") String observationId,
        @ToolParam(description = "New title (optional, null to keep existing)", required = false) String title,
        @ToolParam(description = "New content/narrative (optional, null to keep existing)", required = false) String content,
        @ToolParam(description = "New concepts/tags list (optional, null to keep existing)", required = false) List<String> concepts,
        @ToolParam(description = "New facts list (optional, null to keep existing)", required = false) List<String> facts,
        @ToolParam(description = "New source attribution (optional, null to keep existing)", required = false) String source) {
        
        if (observationId == null || observationId.isBlank()) {
            return "Error: observation ID is required";
        }
        
        try {
            var update = ObservationUpdate.builder()
                .title(title)
                .content(content)
                .concepts(concepts)
                .facts(facts)
                .source(source)
                .build();
            
            cortexClient.updateObservation(observationId, update);
            return "Successfully updated observation: " + observationId;
        } catch (Exception e) {
            log.warn("Failed to update memory {}: {}", observationId, e.getMessage());
            return "Error updating memory: " + e.getMessage();
        }
    }

    /**
     * Delete a memory/observation.
     * Use this to remove outdated or irrelevant memories.
     *
     * @param observationId UUID of the observation to delete
     * @return Success or error message
     */
    @Tool(description = "Delete an existing memory/observation. " +
        "Use to remove outdated, irrelevant, or incorrect memories. " +
        "This action is permanent - the memory cannot be recovered.")
    public String deleteMemory(
        @ToolParam(description = "UUID of the observation to delete") String observationId) {
        
        if (observationId == null || observationId.isBlank()) {
            return "Error: observation ID is required";
        }
        
        try {
            cortexClient.deleteObservation(observationId);
            return "Successfully deleted observation: " + observationId;
        } catch (Exception e) {
            log.warn("Failed to delete memory {}: {}", observationId, e.getMessage());
            return "Error deleting memory: " + e.getMessage();
        }
    }

    private String resolveProjectPath() {
        if (CortexSessionContext.isActive()) {
            String ctx = CortexSessionContext.getProjectPath();
            if (ctx != null && !ctx.isBlank()) {
                return ctx;
            }
        }
        return defaultProjectPath;
    }

    private static String formatExperience(Experience e) {
        var sb = new StringBuilder();
        if (e.task() != null) sb.append("Task: ").append(e.task()).append("\n");
        if (e.strategy() != null) sb.append("Strategy: ").append(e.strategy()).append("\n");
        if (e.outcome() != null) sb.append("Outcome: ").append(e.outcome()).append("\n");
        if (e.qualityScore() > 0) sb.append("Quality: ").append(e.qualityScore()).append("\n");
        if (e.createdAt() != null) sb.append("When: ").append(e.createdAt()).append("\n");
        return sb.length() > 0 ? sb.toString() : e.toString();
    }
}
