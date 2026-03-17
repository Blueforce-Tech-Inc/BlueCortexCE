package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.common.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ContextService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(ContextService.class);

    @Override
    public Logger getLogger() {
        return log;
    }

    private static final int FULL_OBSERVATION_COUNT = 10;
    private static final int TOTAL_OBSERVATION_COUNT = 50;
    private static final int SESSION_COUNT = 5;
    private static final String DITTO_MARK = "\u201D"; // U+201D right double quotation mark

    private static final DateTimeFormatter HEADER_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd h:mma z", Locale.US);
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("h:mma", Locale.US);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter DAY_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private SummaryRepository summaryRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ModeService modeService;

    // ===== Timeline Types =====

    /**
     * Timeline item type: observation or summary.
     * Aligned with TS TimelineItem type.
     */
    public sealed interface TimelineItem permits ObservationTimelineItem, SummaryTimelineItem {
        long getEpoch();
        String getTimeDisplay();
    }

    /**
     * Observation wrapped in timeline format.
     */
    public record ObservationTimelineItem(
        ObservationEntity observation,
        String timeDisplay
    ) implements TimelineItem {
        @Override
        public long getEpoch() {
            return observation.getCreatedAtEpoch() != null ? observation.getCreatedAtEpoch() : 0;
        }

        @Override
        public String getTimeDisplay() {
            return timeDisplay;
        }
    }

    /**
     * Summary wrapped in timeline format with display time for interleaving.
     * Aligned with TS SummaryTimelineItem.
     */
    public record SummaryTimelineItem(
        SummaryEntity summary,
        long displayEpoch,
        String displayTime,
        boolean shouldShowLink
    ) implements TimelineItem {
        @Override
        public long getEpoch() {
            return displayEpoch;
        }

        @Override
        public String getTimeDisplay() {
            return displayTime;
        }
    }

    /**
     * Day-grouped timeline for rendering.
     */
    public record DayGroup(
        LocalDate date,
        String dateDisplay,
        List<TimelineItem> items
    ) {}

    // ===== Context Configuration =====

    /**
     * P0: Context configuration aligned with TS ContextConfig
     * P2: Added display control fields for finer granularity
     *
     * Default observation types and concepts are now loaded from ModeService.
     * This is a static class to allow instantiation from external controllers.
     */
    public static class ContextConfig {
        private int totalObservationCount = TOTAL_OBSERVATION_COUNT;
        private int fullObservationCount = FULL_OBSERVATION_COUNT;
        private int sessionCount = SESSION_COUNT;
        private Set<String> observationTypes = new HashSet<>(Arrays.asList(
            "bugfix", "feature", "decision", "refactor", "discovery", "change"
        ));
        private Set<String> observationConcepts = new HashSet<>(Arrays.asList(
            "how-it-works", "why-it-exists", "what-changed", "problem-solution", "gotcha", "pattern", "trade-off"
        ));
        private boolean showLastSummary = true;
        private boolean showLastMessage = false;
        // P2: Display control fields
        private boolean showReadTokens = true;
        private boolean showWorkTokens = true;
        private boolean showSavingsAmount = true;
        private boolean showSavingsPercent = true;
        private String fullObservationField = "all"; // "all", "content", "facts", "none"

        /**
         * Default constructor with hardcoded defaults.
         * Use withModeService() to populate from active mode.
         */
        public ContextConfig() {
            // Uses default types and concepts
        }

        /**
         * Factory method to create ContextConfig populated from ModeService.
         */
        public static ContextConfig withModeService(ModeService modeService) {
            ContextConfig config = new ContextConfig();
            if (modeService != null && modeService.getActiveMode() != null) {
                config.observationTypes = new HashSet<>(modeService.getValidTypeIds());
                config.observationConcepts = new HashSet<>(modeService.getValidConceptIds());
            }
            return config;
        }

        // Getters and setters
        public int getTotalObservationCount() { return totalObservationCount; }
        public void setTotalObservationCount(int v) { this.totalObservationCount = v; }
        public int getFullObservationCount() { return fullObservationCount; }
        public void setFullObservationCount(int v) { this.fullObservationCount = v; }
        public int getSessionCount() { return sessionCount; }
        public void setSessionCount(int v) { this.sessionCount = v; }
        public Set<String> getObservationTypes() { return observationTypes; }
        public void setObservationTypes(Set<String> v) { this.observationTypes = v; }
        public Set<String> getObservationConcepts() { return observationConcepts; }
        public void setObservationConcepts(Set<String> v) { this.observationConcepts = v; }
        public boolean isShowLastSummary() { return showLastSummary; }
        public void setShowLastSummary(boolean v) { this.showLastSummary = v; }
        public boolean isShowLastMessage() { return showLastMessage; }
        public void setShowLastMessage(boolean v) { this.showLastMessage = v; }
        public boolean isShowReadTokens() { return showReadTokens; }
        public void setShowReadTokens(boolean v) { this.showReadTokens = v; }
        public boolean isShowWorkTokens() { return showWorkTokens; }
        public void setShowWorkTokens(boolean v) { this.showWorkTokens = v; }
        public boolean isShowSavingsAmount() { return showSavingsAmount; }
        public void setShowSavingsAmount(boolean v) { this.showSavingsAmount = v; }
        public boolean isShowSavingsPercent() { return showSavingsPercent; }
        public void setShowSavingsPercent(boolean v) { this.showSavingsPercent = v; }
        public String getFullObservationField() { return fullObservationField; }
        public void setFullObservationField(String v) { this.fullObservationField = v; }
    }

    // ===== Public API =====

    /**
     * P1: Validate project path to prevent path traversal attacks.
     * P0: Returns normalized path for consistent handling.
     */
    private String validateProjectPath(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("Project path cannot be null or empty");
        }
        // Normalize the path to resolve any relative components
        java.nio.file.Path normalized = java.nio.file.Paths.get(projectPath).normalize();
        // Check for path traversal attempts by comparing normalized path
        String normalizedStr = normalized.toString();
        if (!normalizedStr.equals(projectPath) &&
            !normalizedStr.startsWith(projectPath) &&
            !projectPath.contains("..")) {
            logFailure("Potential path traversal attempt: {}", projectPath);
            throw new IllegalArgumentException("Invalid project path");
        }
        return normalizedStr;
    }

    /**
     * Generate context for a project with Prior Messages support.
     * P0: Integrates prior session messages for context continuity.
     *
     * @param projectPath   Project path
     * @param config       Context configuration
     * @param sessionId    Current session ID (used to skip when finding prior messages)
     */
    public String generateContext(String projectPath, ContextConfig config, String sessionId) {
        // P1: Validate project path to prevent path traversal
        String validatedPath = validateProjectPath(projectPath);
        // P2: Handle root path edge case - getFileName() returns null for root paths
        Path path = Paths.get(validatedPath);
        String project = path.getFileName() != null ? path.getFileName().toString() : path.toString();

        if (validatedPath.length() > 4096) {
            logHappyPath("Project path too long: {} characters", validatedPath.length());
            throw new IllegalArgumentException("Project path exceeds maximum length");
        }

        // P0: Get prior messages for context continuity
        PriorMessages priorMessages = getPriorSessionMessages(projectPath, sessionId);

        // P0: Use filtered query with type and concept filtering
        List<String> types = new ArrayList<>(config.getObservationTypes());
        List<String> concepts = new ArrayList<>(config.getObservationConcepts());
        boolean conceptsEmpty = concepts.isEmpty();

        List<ObservationEntity> observations = observationRepository.findByTypeAndConcepts(
            projectPath, types, concepts, conceptsEmpty, config.getTotalObservationCount()
        );

        List<SummaryEntity> allSummaries = summaryRepository.findByProjectLimited(projectPath, 100);
        int sessionCount = Math.min(config.getSessionCount(), allSummaries.size());
        List<SummaryEntity> displaySummaries = allSummaries.isEmpty()
            ? allSummaries
            : allSummaries.subList(0, sessionCount);

        if (observations.isEmpty() && displaySummaries.isEmpty() &&
            priorMessages.assistantMessage().isEmpty()) {
            return renderEmptyState(project);
        }

        // Build unified timeline with interleaved observations and summaries
        List<SummaryTimelineItem> summaryItems = prepareSummariesForTimeline(displaySummaries, allSummaries);
        List<TimelineItem> timeline = buildTimeline(observations, summaryItems);

        // Render timeline with prior messages appended
        String timelineContext = renderTimeline(project, timeline, observations, config);
        List<String> priorSection = renderPreviouslySection(priorMessages);

        // Combine timeline and prior messages
        if (priorSection.isEmpty()) {
            return timelineContext;
        }

        // Append Prior Messages section at the end
        // P2: Use List.copyOf for immutability
        List<String> timelineLines = timelineContext.isEmpty()
            ? List.of()
            : List.of(timelineContext.split("\n"));
        List<String> output = new ArrayList<>(timelineLines);
        output.addAll(priorSection);
        return String.join("\n", output);
    }

    /**
     * Generate context string for a project with Type/Concept filtering.
     * P0: Aligned with TS queryObservations.
     */
    public String generateContext(String projectPath) {
        return generateContext(projectPath, new ContextConfig(), null);
    }

    /**
     * Generate context with custom configuration.
     */
    public String generateContext(String projectPath, ContextConfig config) {
        return generateContext(projectPath, config, null);
    }

    /**
     * Generate context with direct filter parameters (for WebUI Settings modal).
     * This method is called by /api/context/preview endpoint.
     *
     * @param projectPath Project path
     * @param types List of observation types to include (empty = all types)
     * @param concepts List of concepts to filter by (empty = all concepts)
     * @param includeObservations Whether to include observations
     * @param includeSummaries Whether to include summaries
     * @param maxObservations Maximum observations to include
     * @param maxSummaries Maximum summaries to include
     * @param sessionCount Number of recent sessions to query from
     * @param fullCount Number of observations to show full details
     * @return Formatted context string
     */
    public String generateContextWithFilters(
            String projectPath,
            List<String> types,
            List<String> concepts,
            boolean includeObservations,
            boolean includeSummaries,
            int maxObservations,
            int maxSummaries,
            int sessionCount,
            int fullCount) {

        if (projectPath == null || projectPath.isBlank()) {
            return renderEmptyState("unknown");
        }

        log.debug("Generating context with filters - types: {}, concepts: {}, maxObs: {}, maxSum: {}, sessions: {}, fullCount: {}",
                types, concepts, maxObservations, maxSummaries, sessionCount, fullCount);

        // Validate project path
        String validatedPath = validateProjectPath(projectPath);
        Path path = Paths.get(validatedPath);
        String project = path.getFileName() != null ? path.getFileName().toString() : path.toString();

        // Query observations with filters and session limit
        List<ObservationEntity> observations = new ArrayList<>();
        if (includeObservations) {
            List<String> safeTypes = types.isEmpty()
                    ? List.of("bugfix", "feature", "refactor", "discovery", "decision", "change")
                    : types;
            boolean conceptsEmpty = concepts.isEmpty();
            // Use session-aware query when sessionCount is specified
            if (sessionCount > 0) {
                observations = observationRepository.findByTypeAndConceptsWithSessionLimit(
                        validatedPath, safeTypes, concepts, conceptsEmpty, maxObservations, sessionCount);
            } else {
                observations = observationRepository.findByTypeAndConcepts(
                        validatedPath, safeTypes, concepts, conceptsEmpty, maxObservations);
            }
        }

        // Query summaries
        List<SummaryEntity> allSummaries = new ArrayList<>();
        if (includeSummaries) {
            allSummaries = summaryRepository.findByProjectPathOrderByCreatedAtDesc(validatedPath);
        }

        // Apply max summaries limit
        List<SummaryEntity> displaySummaries = allSummaries.stream()
                .limit(maxSummaries)
                .collect(Collectors.toList());

        // Check if we have any content
        if (observations.isEmpty() && displaySummaries.isEmpty()) {
            return renderEmptyState(project);
        }

        // Build timeline items
        List<SummaryTimelineItem> summaryItems = prepareSummariesForTimeline(displaySummaries, allSummaries);
        List<TimelineItem> timeline = buildTimeline(observations, summaryItems);

        // Create minimal ContextConfig for rendering
        ContextConfig config = new ContextConfig();
        config.setTotalObservationCount(maxObservations);
        config.setFullObservationCount(Math.min(fullCount, maxObservations));
        config.setShowReadTokens(false);
        config.setShowWorkTokens(false);
        config.setShowSavingsAmount(false);

        // Render timeline
        return renderTimeline(project, timeline, observations, config);
    }

    /**
     * P1: Generate context for multiple projects (worktree support).
     * Aligned with TS queryObservationsMulti.
     * Uses interleaved sorting: query each project separately, merge by time.
     */
    public String generateContextMultiProject(List<String> projectPaths, ContextConfig config) {
        if (projectPaths == null || projectPaths.isEmpty()) {
            return renderEmptyState("unknown");
        }

        String project = Paths.get(projectPaths.get(0)).getFileName().toString();

        // P2: Use interleaved sorting - query each project separately, merge by time
        List<String> types = new ArrayList<>(config.getObservationTypes());
        List<String> concepts = new ArrayList<>(config.getObservationConcepts());
        boolean conceptsEmpty = concepts.isEmpty();

        // Calculate per-project limit to ensure fair distribution
        int perProjectLimit = config.getTotalObservationCount();
        if (projectPaths.size() > 1) {
            perProjectLimit = Math.max(10, config.getTotalObservationCount() / projectPaths.size());
        }

        // Query each project separately and collect
        List<ObservationEntity> allObservations = new ArrayList<>();
        for (String projectPath : projectPaths) {
            List<ObservationEntity> projectObs = observationRepository.findByTypeAndConcepts(
                projectPath, types, concepts, conceptsEmpty, perProjectLimit
            );
            allObservations.addAll(projectObs);
        }

        // Sort by time (interleaved) - most recent first
        allObservations.sort((a, b) -> Long.compare(
            b.getCreatedAtEpoch() != null ? b.getCreatedAtEpoch() : 0,
            a.getCreatedAtEpoch() != null ? a.getCreatedAtEpoch() : 0
        ));

        // Trim to total limit with bounds checking
        if (allObservations.size() > config.getTotalObservationCount()) {
            int limit = Math.min(config.getTotalObservationCount(), allObservations.size());
            allObservations = allObservations.subList(0, limit);
        }

        // For summaries, query all projects (aligned with TS querySummariesMulti)
        List<SummaryEntity> allSummaries = summaryRepository.findByProjectsLimited(
            projectPaths, 100
        );
        int sessionCount = Math.min(config.getSessionCount(), allSummaries.size());
        List<SummaryEntity> displaySummaries = allSummaries.isEmpty()
            ? allSummaries
            : allSummaries.subList(0, sessionCount);

        if (allObservations.isEmpty() && displaySummaries.isEmpty()) {
            return renderEmptyState(project);
        }

        // Build unified timeline
        List<SummaryTimelineItem> summaryItems = prepareSummariesForTimeline(displaySummaries, allSummaries);
        List<TimelineItem> timeline = buildTimeline(allObservations, summaryItems);

        return renderTimeline(project, timeline, allObservations, config);
    }

    // ===== Timeline Building =====

    /**
     * P0: Prepare summaries for timeline - convert to SummaryTimelineItem with display time.
     * Aligned with TS prepareSummariesForTimeline.
     */
    private List<SummaryTimelineItem> prepareSummariesForTimeline(
        List<SummaryEntity> displaySummaries,
        List<SummaryEntity> allSummaries
    ) {
        if (displaySummaries.isEmpty()) {
            return List.of();
        }

        SummaryEntity mostRecentSummary = allSummaries.isEmpty() ? null : allSummaries.get(0);

        return IntStream.range(0, displaySummaries.size())
            .mapToObj(i -> {
                SummaryEntity summary = displaySummaries.get(i);
                SummaryEntity olderSummary = (i == 0 || i + 1 >= allSummaries.size()) ? null : allSummaries.get(i + 1);
                long displayEpoch = olderSummary != null && olderSummary.getCreatedAtEpoch() != null
                    ? olderSummary.getCreatedAtEpoch()
                    : (summary.getCreatedAtEpoch() != null ? summary.getCreatedAtEpoch() : 0);
                String displayTime = olderSummary != null && olderSummary.getCreatedAtEpoch() != null
                    ? formatTime(olderSummary.getCreatedAtEpoch())
                    : formatTime(summary.getCreatedAtEpoch());
                boolean shouldShowLink = mostRecentSummary == null ||
                    !Objects.equals(summary.getId(), mostRecentSummary.getId());
                return new SummaryTimelineItem(summary, displayEpoch, displayTime, shouldShowLink);
            })
            .collect(Collectors.toList());
    }

    /**
     * P0: Build unified timeline by interleaving observations and summaries.
     * Aligned with TS buildTimeline.
     */
    private List<TimelineItem> buildTimeline(
        List<ObservationEntity> observations,
        List<SummaryTimelineItem> summaries
    ) {
        List<TimelineItem> timeline = new ArrayList<>();

        // Add observations
        for (ObservationEntity obs : observations) {
            timeline.add(new ObservationTimelineItem(obs, formatTime(obs.getCreatedAtEpoch())));
        }

        // Add summaries
        timeline.addAll(summaries);

        // Sort by time (ascending - oldest first for chronological display)
        timeline.sort(Comparator.comparingLong(TimelineItem::getEpoch));

        return timeline;
    }

    /**
     * P0: Group timeline items by day.
     * Aligned with TS groupTimelineByDay.
     */
    private List<DayGroup> groupTimelineByDay(List<TimelineItem> timeline) {
        Map<String, List<TimelineItem>> itemsByDay = new LinkedHashMap<>();

        for (TimelineItem item : timeline) {
            LocalDate date = Instant.ofEpochMilli(item.getEpoch())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
            String dayKey = date.format(DAY_FMT);

            itemsByDay.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(item);
        }

        // Sort days in reverse chronological order (most recent first)
        List<String> sortedDays = new ArrayList<>(itemsByDay.keySet());
        sortedDays.sort((a, b) -> b.compareTo(a));

        // Build DayGroup list
        List<DayGroup> dayGroups = new ArrayList<>();
        for (String day : sortedDays) {
            LocalDate date = LocalDate.parse(day, DAY_FMT);
            String dateDisplay = date.format(DATE_FMT);
            List<TimelineItem> items = itemsByDay.get(day);

            // Within each day, sort by epoch (ascending)
            items.sort(Comparator.comparingLong(TimelineItem::getEpoch));

            dayGroups.add(new DayGroup(date, dateDisplay, items));
        }

        return dayGroups;
    }

    // ===== Timeline Rendering =====

    /**
     * P0: Render unified timeline with day grouping and file grouping.
     * Aligned with TS renderTimeline + renderDayTimeline.
     */
    private String renderTimeline(String project, List<TimelineItem> timeline,
                                   List<ObservationEntity> observations,
                                   ContextConfig config) {
        List<String> output = new ArrayList<>();
        TokenService.TokenEconomics economics = tokenService.calculateEconomics(observations);

        // Header
        String now = Instant.now().atZone(ZoneId.systemDefault()).format(HEADER_FMT).toLowerCase(Locale.US);
        output.add("# " + project + " recent context, " + now);
        output.add("");

        // Token economics header
        if (economics.totalObservations() > 0) {
            output.add(String.format("📊 %d observations | 📖 %,d read tokens | 💰 %,d saved (%.0f%%)",
                economics.totalObservations(),
                economics.totalReadTokens(),
                economics.savings(),
                economics.savingsPercent()));
            output.add("");
        }

        // Group and render by day
        List<DayGroup> dayGroups = groupTimelineByDay(timeline);
        Set<Long> fullObservationEpochs = getFullObservationEpochs(observations, config.getFullObservationCount());

        for (DayGroup dayGroup : dayGroups) {
            output.add("### " + dayGroup.dateDisplay());
            output.add("");

            // Render day items with file grouping
            renderDayTimeline(output, dayGroup.items(), fullObservationEpochs, config);

            // Add blank line between days
            output.add("");
        }

        // P0: Most recent summary at bottom (aligned with TS renderSummaryFields)
        if (config.isShowLastSummary()) {
            List<SummaryEntity> summaries = summaryRepository.findByProjectLimited(
                Paths.get(project).toString(), 1
            );
            if (!summaries.isEmpty()) {
                SummaryEntity summary = summaries.get(0);
                output.add("## Last Session Summary");
                if (summary.getRequest() != null) output.add("**Request:** " + summary.getRequest());
                if (summary.getCompleted() != null) output.add("**Completed:** " + summary.getCompleted());
                if (summary.getLearned() != null) output.add("**Learned:** " + summary.getLearned());
                if (summary.getNextSteps() != null) output.add("**Next Steps:** " + summary.getNextSteps());
                output.add("");
            }
        }

        // P0: Token Savings Footer
        output.addAll(renderTokenSavingsFooter(economics));

        return String.join("\n", output).stripTrailing();
    }

    /**
     * P0: Render a single day's timeline with file grouping.
     * Aligned with TS renderDayTimeline.
     */
    private void renderDayTimeline(List<String> output, List<TimelineItem> dayItems,
                                   Set<Long> fullObservationEpochs, ContextConfig config) {
        String currentFile = null;
        boolean tableOpen = false;
        String lastTimeDisplay = "";

        int obsIndex = 0;
        for (TimelineItem item : dayItems) {
            if (item instanceof SummaryTimelineItem summaryItem) {
                // Close any open table before summary
                if (tableOpen) {
                    output.add("");
                    tableOpen = false;
                }

                // Render summary item
                renderSummaryItem(output, summaryItem, lastTimeDisplay);
                lastTimeDisplay = summaryItem.displayTime();
            } else {
                ObservationTimelineItem obsItem = (ObservationTimelineItem) item;
                ObservationEntity obs = obsItem.observation();

                // Get file for grouping
                String file = extractFirstFile(obs.getFilesModified(), obs.getFilesRead());

                // New file group
                if (!Objects.equals(file, currentFile)) {
                    if (tableOpen) {
                        output.add("");
                    }
                    // Render file header
                    if (file != null && !file.isEmpty()) {
                        output.add("**" + file + "**");
                    }
                    // Render table header
                    output.add("| ID | Time | T | Title | Read | Work |");
                    output.add("|---|---|---|---|---|---|");
                    currentFile = file;
                    tableOpen = true;
                }

                // Determine if full or compact mode
                boolean isFull = fullObservationEpochs.contains(obs.getCreatedAtEpoch());
                String timeDisplay = obsItem.timeDisplay();
                String displayTime = timeDisplay.equals(lastTimeDisplay) ? DITTO_MARK : timeDisplay;
                lastTimeDisplay = timeDisplay;

                if (isFull) {
                    renderFullObservation(output, obs, obsIndex + 1, displayTime);
                } else {
                    renderCompactObservation(output, obs, obsIndex + 1, displayTime);
                }
                obsIndex++;
            }
        }
    }

    /**
     * Render a summary item in the timeline.
     */
    private void renderSummaryItem(List<String> output, SummaryTimelineItem summary,
                                    String lastTimeDisplay) {
        SummaryEntity s = summary.summary();
        String timeDisplay = summary.displayTime().equals(lastTimeDisplay) ? DITTO_MARK : summary.displayTime();

        output.add(String.format("**%s** 📝 **Session Summary**", timeDisplay));
        if (s.getRequest() != null) {
            output.add("  **Request:** " + truncate(s.getRequest(), 100));
        }
        if (s.getCompleted() != null) {
            output.add("  **Completed:** " + truncate(s.getCompleted(), 100));
        }
    }

    /**
     * Render full observation with all details.
     */
    private void renderFullObservation(List<String> output, ObservationEntity obs,
                                        int index, String timeDisplay) {
        String icon = tokenService.getWorkEmoji(obs.getType());
        int readTokens = tokenService.calculateObservationTokens(obs);
        int discoveryTokens = obs.getDiscoveryTokens() != null ? obs.getDiscoveryTokens() : 0;

        output.add(String.format("**#%d** %s %s **%s**",
            index, timeDisplay, icon, obs.getTitle() != null ? obs.getTitle() : "Untitled"));

        if (obs.getSubtitle() != null && !obs.getSubtitle().isEmpty()) {
            output.add("  " + obs.getSubtitle());
        }

        // Full mode: show narrative/facts
        if (obs.getContent() != null && !obs.getContent().isEmpty()) {
            output.add("  " + obs.getContent());
        }
        if (obs.getFacts() != null && !obs.getFacts().isEmpty()) {
            for (String fact : obs.getFacts()) {
                output.add("  • " + fact);
            }
        }

        // Token stats
        String discoveryDisplay = discoveryTokens > 0
            ? icon + " " + String.format("%,d", discoveryTokens) : "-";
        output.add(String.format("  📖 %,d | %s", readTokens, discoveryDisplay));
    }

    /**
     * Render compact observation as table row.
     */
    private void renderCompactObservation(List<String> output, ObservationEntity obs,
                                          int index, String timeDisplay) {
        String icon = tokenService.getWorkEmoji(obs.getType());
        int readTokens = tokenService.calculateObservationTokens(obs);
        int discoveryTokens = obs.getDiscoveryTokens() != null ? obs.getDiscoveryTokens() : 0;
        String discoveryDisplay = discoveryTokens > 0
            ? String.format("%,d", discoveryTokens) : "-";

        output.add(String.format("| %d | %s | %s | %s | %,d | %s |",
            index, timeDisplay, icon,
            obs.getTitle() != null ? obs.getTitle() : "",
            readTokens, discoveryDisplay));
    }

    /**
     * Get epochs of observations to show in full mode.
     * P0: Add sensible upper bound to prevent memory exhaustion.
     */
    private static final int MAX_FULL_OBSERVATIONS = 100;

    private Set<Long> getFullObservationEpochs(List<ObservationEntity> observations, int count) {
        Set<Long> epochs = new HashSet<>();
        // P0: Enforce upper bound to prevent memory issues
        int limit = Math.min(Math.min(observations.size(), count), MAX_FULL_OBSERVATIONS);
        for (int i = 0; i < limit; i++) {
            if (observations.get(i).getCreatedAtEpoch() != null) {
                epochs.add(observations.get(i).getCreatedAtEpoch());
            }
        }
        return epochs;
    }

    /**
     * Extract first modified file, or first read file as fallback.
     */
    private String extractFirstFile(List<String> filesModified, List<String> filesRead) {
        if (filesModified != null && !filesModified.isEmpty()) {
            return filesModified.get(0);
        }
        if (filesRead != null && !filesRead.isEmpty()) {
            return filesRead.get(0);
        }
        return null;
    }

    /**
     * P0: Render token savings footer with signature.
     * Aligned with TS Token Savings Footer.
     */
    private List<String> renderTokenSavingsFooter(TokenService.TokenEconomics economics) {
        List<String> footer = new ArrayList<>();
        footer.add("---");
        footer.add("Token Savings Summary");
        footer.add(String.format("- Total observations: %d", economics.totalObservations()));
        footer.add(String.format("- Read tokens: %,d", economics.totalReadTokens()));
        footer.add(String.format("- Saved tokens: %,d (%.0f%%)", economics.savings(), economics.savingsPercent()));
        footer.add("- _Claude Memory: Auto-regenerated from project observations_");
        return footer;
    }

    // ===== Prior Messages =====

    /**
     * P0: Get prior session's assistant message for context.
     * Aligned with TS getPriorSessionMessages + extractPriorMessages.
     *
     * Note: In Java backend architecture, we store the last assistant message in the database
     * rather than reading from transcript files (which are local to the user's machine).
     */
    public PriorMessages getPriorSessionMessages(String projectPath, String currentSessionId) {
        if (projectPath == null || projectPath.isEmpty()) {
            return new PriorMessages("", "");
        }

        List<SessionEntity> sessions = sessionRepository.findLastCompletedSessionWithMessage(projectPath);

        for (SessionEntity session : sessions) {
            // Skip if this is the current session
            if (currentSessionId != null && Objects.equals(session.getContentSessionId(), currentSessionId)) {
                continue;
            }
            // Return the first prior session's assistant message
            if (session.getLastAssistantMessage() != null && !session.getLastAssistantMessage().isEmpty()) {
                return new PriorMessages("", stripSystemReminders(session.getLastAssistantMessage()));
            }
        }

        return new PriorMessages("", "");
    }

    /**
     * Strip <system-reminder> tags from message content.
     * P0: Uses string-based parser instead of regex to prevent ReDoS attacks.
     */
    private String stripSystemReminders(String message) {
        if (message == null) return "";
        // P0: Limit input size to prevent ReDoS
        if (message.length() > 100000) {
            message = message.substring(0, 100000);
        }
        // P0: Safe string-based parsing instead of regex
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int tagStart;
        while ((tagStart = message.indexOf("<system-reminder>", i)) != -1) {
            // Append content before the tag
            sb.append(message, i, tagStart);
            // Find the end tag
            int tagEnd = message.indexOf("</system-reminder>", tagStart);
            if (tagEnd == -1) {
                // No end tag found, append rest and break
                break;
            }
            // Skip past the end tag
            i = tagEnd + 18; // length of </system-reminder>
        }
        // Append remaining content after all tags
        if (i < message.length()) {
            sb.append(message, i, message.length());
        }
        return sb.toString().trim();
    }

    /**
     * Render Prior Messages section.
     * Aligned with TS renderPreviouslySection.
     */
    public List<String> renderPreviouslySection(PriorMessages priorMessages) {
        List<String> output = new ArrayList<>();

        if (priorMessages == null || priorMessages.assistantMessage() == null || priorMessages.assistantMessage().isEmpty()) {
            return output;
        }

        output.add("---");
        output.add("Previously:");
        output.add(priorMessages.assistantMessage());

        return output;
    }

    /**
     * Prior messages record.
     */
    public record PriorMessages(String userMessage, String assistantMessage) {}

    // ===== Helper Methods =====

    private String renderEmptyState(String project) {
        return "# " + project + " — no memories yet\n\nStart working to create observations.";
    }

    private String formatTime(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) {
            return ""; // P1: Handle null and negative/zero epochs gracefully
        }
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(TIME_FMT)
            .toLowerCase(Locale.US);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    // ===== Preview API Methods =====

    /**
     * Get recent observations for a project (formatted for UI display).
     * Used by /api/context/preview endpoint.
     */
    public List<Map<String, Object>> getRecentObservations(String projectPath, int limit) {
        if (projectPath == null || projectPath.isBlank()) {
            return List.of();
        }

        List<ObservationEntity> observations = observationRepository.findByProjectPathOrderByCreatedAtDesc(projectPath);
        return observations.stream()
            .limit(limit)
            .map(obs -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", obs.getId());
                map.put("type", obs.getType());
                map.put("title", obs.getTitle());
                map.put("narrative", obs.getContent());
                map.put("facts", obs.getFacts());
                map.put("concepts", obs.getConcepts());
                map.put("filesRead", obs.getFilesRead());
                map.put("filesModified", obs.getFilesModified());
                map.put("createdAt", obs.getCreatedAt());
                map.put("createdAtEpoch", obs.getCreatedAtEpoch());
                return map;
            })
            .collect(Collectors.toList());
    }

    /**
     * Get recent summaries for a project (formatted for UI display).
     * Used by /api/context/preview endpoint.
     */
    public List<Map<String, Object>> getRecentSummaries(String projectPath, int limit) {
        if (projectPath == null || projectPath.isBlank()) {
            return List.of();
        }

        List<SummaryEntity> summaries = summaryRepository.findByProjectPathOrderByCreatedAtDesc(projectPath);
        return summaries.stream()
            .limit(limit)
            .map(sum -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", sum.getId());
                map.put("request", sum.getRequest());
                map.put("investigated", sum.getInvestigated());
                map.put("learned", sum.getLearned());
                map.put("completed", sum.getCompleted());
                map.put("nextSteps", sum.getNextSteps());
                map.put("createdAt", sum.getCreatedAt());
                map.put("createdAtEpoch", sum.getCreatedAtEpoch());
                return map;
            })
            .collect(Collectors.toList());
    }

    /**
     * Generate continuation suggestions for a project.
     * Used by /api/context/preview endpoint.
     */
    public String generateContinuation(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return "";
        }

        try {
            List<SummaryEntity> summaries = summaryRepository.findByProjectPathOrderByCreatedAtDesc(projectPath);
            if (!summaries.isEmpty()) {
                SummaryEntity lastSummary = summaries.get(0);
                if (lastSummary.getNextSteps() != null && !lastSummary.getNextSteps().isEmpty()) {
                    return "Next steps from last session:\n" + lastSummary.getNextSteps();
                }
            }
        } catch (Exception e) {
            logHappyPath("Failed to generate continuation for {}: {}", projectPath, e.getMessage());
        }
        return "";
    }
}
