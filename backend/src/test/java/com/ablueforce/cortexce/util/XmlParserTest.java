package com.ablueforce.cortexce.util;

import com.ablueforce.cortexce.exception.DataValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Offline contract for the answers the extraction prompt asks a model to produce.
 *
 * <p>No provider is involved. These cases pin what {@link XmlParser} accepts, so a
 * prompt change that stops requiring closing tags, or a parser change that starts
 * rejecting markdown fences, fails here instead of at the queue's terminal state.
 */
class XmlParserTest {

    private static final String WELL_FORMED = """
            Here is the observation.
            ```xml
            <observation>
              <type>bugfix</type>
              <title>Fixed null orderId in PaymentService</title>
              <subtitle>Null order ids now fail fast instead of throwing an NPE</subtitle>
              <facts>
                <fact>PaymentService.authorize throws IllegalArgumentException for a blank orderId</fact>
                <fact>A regression test covers the null case</fact>
              </facts>
              <narrative>The public API now validates orderId before calling the gateway.</narrative>
              <concepts>
                <concept>problem-solution</concept>
                <concept>what-changed</concept>
              </concepts>
              <files_read>
                <file>src/main/java/com/example/PaymentService.java</file>
              </files_read>
              <files_modified>
                <file>src/test/java/com/example/PaymentServiceTest.java</file>
              </files_modified>
            </observation>
            ```
            """;

    @Test
    @DisplayName("a fenced, well-formed observation parses into every field")
    void parsesWellFormedObservationInsideMarkdownFence() {
        XmlParser.ParsedObservation parsed = XmlParser.parseObservation(WELL_FORMED);

        assertEquals("bugfix", parsed.type);
        assertEquals("Fixed null orderId in PaymentService", parsed.title);
        assertEquals("Null order ids now fail fast instead of throwing an NPE", parsed.subtitle);
        assertEquals("The public API now validates orderId before calling the gateway.", parsed.narrative);
        assertEquals(List.of(
                "PaymentService.authorize throws IllegalArgumentException for a blank orderId",
                "A regression test covers the null case"), parsed.facts);
        assertEquals(List.of("problem-solution", "what-changed"), parsed.concepts);
        assertEquals(List.of("src/main/java/com/example/PaymentService.java"), parsed.filesRead);
        assertEquals(List.of("src/test/java/com/example/PaymentServiceTest.java"), parsed.filesModified);
    }

    @Test
    @DisplayName("an explicit <skip> answer is recognised instead of failing the job")
    void recognisesExplicitSkipAnswer() {
        assertEquals("routine test file write",
                XmlParser.extractTag("No output necessary.\n<skip>routine test file write</skip>\n", "skip"));
    }

    @Test
    @DisplayName("free text without an <observation> or <skip> block is a validation error")
    void rejectsPlainTextAnswerWithoutTags() {
        String plain = "No observation recorded — routine test file write with no substantive deliverable.";

        assertThrows(DataValidationException.class, () -> XmlParser.parseObservation(plain));
        assertNull(XmlParser.extractTag(plain, "skip"));
    }

    @Test
    @DisplayName("an unclosed subtitle/narrative stays unparsed: the prompts must close them")
    void unclosedOptionalTagsStayNull() {
        String unclosed = """
                <observation>
                  <type>bugfix</type>
                  <title>Fixed a bug</title>
                  <subtitle>One sentence
                  <narrative>Full context
                  <facts><fact>A fact</fact></facts>
                </observation>
                """;

        XmlParser.ParsedObservation parsed = XmlParser.parseObservation(unclosed);

        assertEquals("Fixed a bug", parsed.title);
        assertNull(parsed.subtitle);
        assertNull(parsed.narrative);
        assertEquals(List.of("A fact"), parsed.facts);
    }
}
