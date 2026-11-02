package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.util.XmlParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic post-validation of one extracted observation. No model call.
 *
 * <p>Runs after {@link XmlParser#parseObservation} and before persistence. It does not change the
 * XML contract and it does not change any public API: it can only turn a parsed observation into
 * “keep these facts” or “reject, with a reason”.</p>
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>title must not be blank and must not exceed {@code max-title-length};</li>
 *   <li>each fact must be supported by the observed tool input/output, otherwise it is dropped;</li>
 *   <li>facts are deduplicated and capped at {@code max-facts};</li>
 *   <li>if no fact survives, the observation is rejected;</li>
 *   <li>a same-scope, same-type observation with an equal normalized title makes this a duplicate,
 *       which is rejected rather than merged. Rejecting is deliberate: a bad new extraction must
 *       never rewrite a good existing memory.</li>
 * </ol>
 *
 * <p>The whole layer is switchable with {@code claudemem.extraction.validation.enabled}; disabled
 * means every check is bypassed and the parsed observation is kept as-is.</p>
 */
@Service
public class ExtractionValidationService {

    public enum Status {
        ACCEPTED,
        REJECTED
    }

    /** Validation outcome. {@code facts} is the fact list to persist; never null. */
    public record Result(Status status, String reason, List<String> facts, List<String> droppedFacts) {

        public boolean accepted() {
            return status == Status.ACCEPTED;
        }

        static Result accepted(List<String> facts, List<String> droppedFacts) {
            return new Result(Status.ACCEPTED, null, facts, droppedFacts);
        }

        static Result rejected(String reason, List<String> droppedFacts) {
            return new Result(Status.REJECTED, reason, List.of(), droppedFacts);
        }
    }

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "from", "by",
            "is", "are", "was", "were", "be", "been", "being", "it", "its", "this", "that", "these",
            "those", "now", "then", "also", "only", "just", "very", "more", "most", "some", "any",
            "all", "each", "every", "not", "no", "but", "as", "at", "into", "over", "under", "than",
            "should", "would", "could", "might", "may", "likely", "probably", "maybe", "seems",
            "appears", "better", "worse", "faster", "slower", "correctly", "properly", "handles",
            "works", "using", "used", "uses", "there", "their", "they", "we", "our", "you", "your");

    private final ObservationRepository observationRepository;
    private final boolean enabled;
    private final int maxTitleLength;
    private final int maxFacts;
    private final int duplicateLookback;

    public ExtractionValidationService(
            ObservationRepository observationRepository,
            @Value("${claudemem.extraction.validation.enabled:true}") boolean enabled,
            @Value("${claudemem.extraction.validation.max-title-length:100}") int maxTitleLength,
            @Value("${claudemem.extraction.validation.max-facts:5}") int maxFacts,
            @Value("${claudemem.extraction.validation.duplicate-lookback:20}") int duplicateLookback) {
        this.observationRepository = observationRepository;
        this.enabled = enabled;
        this.maxTitleLength = maxTitleLength;
        this.maxFacts = maxFacts;
        this.duplicateLookback = duplicateLookback;
    }

    /**
     * @param memoryScopeId resolved project scope, or {@code null} when the scope does not exist
     *                      yet (a new project cannot already hold a duplicate)
     */
    public Result validate(XmlParser.ParsedObservation parsed, String toolName, String toolInput,
                           String toolResponse, UUID memoryScopeId) {
        if (!enabled) {
            List<String> facts = parsed.facts == null ? List.of() : List.copyOf(parsed.facts);
            return Result.accepted(facts, List.of());
        }

        String title = parsed.title == null ? "" : parsed.title.trim();
        if (title.isEmpty()) {
            return Result.rejected("empty title", List.of());
        }
        if (title.length() > maxTitleLength) {
            return Result.rejected("title too long: " + title.length() + " chars > " + maxTitleLength,
                    List.of());
        }

        String evidence = ((toolName == null ? "" : toolName) + "\n"
                + (toolInput == null ? "" : toolInput) + "\n"
                + (toolResponse == null ? "" : toolResponse));

        List<String> kept = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (parsed.facts != null) {
            for (String fact : parsed.facts) {
                if (fact == null || fact.isBlank()) {
                    continue;
                }
                String trimmed = fact.trim();
                String key = normalizeText(trimmed);
                if (!seen.add(key)) {
                    continue;
                }
                if (kept.size() >= maxFacts) {
                    dropped.add(trimmed);
                } else if (!isSupported(trimmed, evidence)) {
                    dropped.add(trimmed);
                } else {
                    kept.add(trimmed);
                }
            }
        }

        if (kept.isEmpty()) {
            return Result.rejected("no verifiable facts after evidence check", dropped);
        }

        String duplicate = findDuplicate(parsed, title, memoryScopeId);
        if (duplicate != null) {
            return Result.rejected("duplicate of existing observation: " + duplicate, dropped);
        }

        return Result.accepted(kept, dropped);
    }

    /**
     * True when the fact is grounded in the trace. Exact containment is accepted; otherwise at
     * least half of the significant tokens must occur in the evidence. Tokens are lower-cased and
     * stopwords, speculation and generic words are ignored, so “the service is now better” does
     * not pass on the strength of “the/service”.
     */
    public static boolean isSupported(String fact, String evidence) {
        String normalizedFact = normalizeText(fact);
        String normalizedEvidence = normalizeText(evidence);
        if (normalizedFact.isEmpty()) {
            return false;
        }
        if (normalizedEvidence.contains(normalizedFact)) {
            return true;
        }
        List<String> tokens = significantTokens(fact);
        if (tokens.isEmpty()) {
            return false;
        }
        int hits = 0;
        for (String token : tokens) {
            if (normalizedEvidence.contains(token)) {
                hits++;
            }
        }
        return hits * 2 >= tokens.size();
    }

    private String findDuplicate(XmlParser.ParsedObservation parsed, String title, UUID memoryScopeId) {
        if (memoryScopeId == null || observationRepository == null) {
            return null;
        }
        String normalizedTitle = normalizeText(title);
        String type = parsed.type == null ? "" : parsed.type.trim().toLowerCase(Locale.ROOT);
        List<ObservationEntity> recent = observationRepository.findRecentByMemoryScopeId(
                memoryScopeId, PageRequest.of(0, Math.max(1, duplicateLookback)));
        for (ObservationEntity existing : recent) {
            if (existing == null || !type.equals(lower(existing.getType()))) {
                continue;
            }
            if (normalizedTitle.equals(normalizeText(existing.getTitle()))) {
                return existing.getTitle();
            }
        }
        return null;
    }

    private static List<String> significantTokens(String fact) {
        List<String> tokens = new ArrayList<>();
        for (String raw : fact.split("[^A-Za-z0-9]+")) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.length() < 3 || STOPWORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    /** Lower-case, punctuation to spaces, collapse whitespace: the comparison form for titles and facts. */
    static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
