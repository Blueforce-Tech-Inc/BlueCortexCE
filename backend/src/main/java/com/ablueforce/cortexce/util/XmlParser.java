package com.ablueforce.cortexce.util;

import com.ablueforce.cortexce.exception.DataValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based XML parser for LLM output.
 * <p>
 * LLM responses are not strict XML — they may contain malformed tags,
 * missing closing tags, or embedded markdown. Strict XML parsers will fail.
 * This uses regex extraction per the cookbook specification.
 */
public final class XmlParser {

    private static final Logger log = LoggerFactory.getLogger(XmlParser.class);

    // P2: Pre-compiled regex patterns for performance
    private static final PatternCache PATTERN_CACHE = new PatternCache();

    private XmlParser() {}

    /**
     * Pattern cache for tag extraction.
     */
    private static class PatternCache {
        // Cache for simple tag patterns
        private final Map<String, Pattern> tagPatterns = new ConcurrentHashMap<>();

        Pattern getTagPattern(String tagName) {
            return tagPatterns.computeIfAbsent(tagName, name ->
                Pattern.compile(
                    "<" + Pattern.quote(name) + ">([\\s\\S]*?)</" + Pattern.quote(name) + ">",
                    Pattern.DOTALL
                )
            );
        }

        Pattern getChildTagPattern(String parentTag, String childTag) {
            String key = parentTag + ">" + childTag;
            return tagPatterns.computeIfAbsent(key, k ->
                Pattern.compile(
                    "<" + Pattern.quote(childTag) + ">([\\s\\S]*?)</" + Pattern.quote(childTag) + ">",
                    Pattern.DOTALL
                )
            );
        }
    }

    /**
     * Extract the text content of a single XML tag.
     * P2: Limits input size to prevent ReDoS attacks.
     */
    public static String extractTag(String xml, String tagName) {
        if (xml == null || tagName == null) return null;
        // P2: Limit input size to prevent catastrophic backtracking
        if (xml.length() > 100000) {
            xml = xml.substring(0, 100000);
        }
        Pattern p = PATTERN_CACHE.getTagPattern(tagName);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Extract an array of items from a parent/child tag structure.
     * <p>
     * Example: {@code <facts><fact>A</fact><fact>B</fact></facts>}
     * → {@code ["A", "B"]}
     * P0: Added input size limits to prevent ReDoS attacks.
     */
    public static List<String> extractArray(String xml, String parentTag, String childTag) {
        if (xml == null) return Collections.emptyList();
        // P0: Limit input size before extracting parent content
        if (xml.length() > 100000) {
            xml = xml.substring(0, 100000);
        }
        String parentContent = extractTag(xml, parentTag);
        if (parentContent == null) return Collections.emptyList();

        List<String> items = new ArrayList<>();
        // P2: Use cached pattern for child tag extraction
        Pattern p = PATTERN_CACHE.getChildTagPattern(parentTag, childTag);
        Matcher m = p.matcher(parentContent);
        int maxItems = 1000; // P0: Prevent unbounded array growth
        while (m.find() && items.size() < maxItems) {
            String item = m.group(1).trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        if (items.size() >= maxItems) {
            log.warn("extractArray reached max items limit ({}) for {}/{}", maxItems, parentTag, childTag);
        }
        return items;
    }

    /**
     * Parse a full {@code <observation>} XML block into its component fields.
     * P1: Throws DataValidationException for malformed XML (not retryable).
     */
    public static ParsedObservation parseObservation(String xml) {
        String obsBlock = extractTag(xml, "observation");
        if (obsBlock == null) {
            throw new DataValidationException("No <observation> block found in XML");
        }

        ParsedObservation obs = new ParsedObservation();
        obs.type = extractTag(obsBlock, "type");
        obs.title = extractTag(obsBlock, "title");
        obs.subtitle = extractTag(obsBlock, "subtitle");
        obs.narrative = extractTag(obsBlock, "narrative");
        obs.facts = extractArray(obsBlock, "facts", "fact");
        obs.concepts = extractArray(obsBlock, "concepts", "concept");
        obs.filesRead = extractArray(obsBlock, "files_read", "file");
        obs.filesModified = extractArray(obsBlock, "files_modified", "file");
        return obs;
    }

    /**
     * Parse a full {@code <summary>} XML block into its component fields.
     * P1: Throws DataValidationException for malformed XML (not retryable).
     */
    public static ParsedSummary parseSummary(String xml) {
        String sumBlock = extractTag(xml, "summary");
        if (sumBlock == null) {
            throw new DataValidationException("No <summary> block found in XML");
        }

        ParsedSummary summary = new ParsedSummary();
        summary.request = extractTag(sumBlock, "request");
        summary.investigated = extractTag(sumBlock, "investigated");
        summary.learned = extractTag(sumBlock, "learned");
        summary.completed = extractTag(sumBlock, "completed");
        summary.nextSteps = extractTag(sumBlock, "next_steps");
        summary.notes = extractTag(sumBlock, "notes");
        return summary;
    }

    /**
     * Parsed observation data from XML.
     */
    public static class ParsedObservation {
        public String type;
        public String title;
        public String subtitle;
        public String narrative;
        public List<String> facts = Collections.emptyList();
        public List<String> concepts = Collections.emptyList();
        public List<String> filesRead = Collections.emptyList();
        public List<String> filesModified = Collections.emptyList();
    }

    /**
     * Parsed summary data from XML.
     */
    public static class ParsedSummary {
        public String request;
        public String investigated;
        public String learned;
        public String completed;
        public String nextSteps;
        public String notes;
    }
}
