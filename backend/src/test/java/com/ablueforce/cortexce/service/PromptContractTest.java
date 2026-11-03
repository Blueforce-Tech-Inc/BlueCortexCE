package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.util.XmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline assertions on the extraction prompt contract.
 *
 * <p>Two layers, both without a provider call:</p>
 * <ol>
 *   <li>the prompt text itself must contain the skip checklist, the must-record list, the title
 *       contract and the facts contract, in both the initial and the continuation system prompt;</li>
 *   <li>a scripted chat model drives the production templates + production {@link XmlParser}
 *       through the three behaviours the prompt is responsible for: a pure-read turn skips, a
 *       reproduction test produces an observation, and a title keeps identifiers verbatim.</li>
 * </ol>
 *
 * <p>The second layer cannot prove what a real model will answer; it proves the contract is wired
 * end to end and that nothing in the parser or template filling turns the intended answer into a
 * different outcome. Real-model behaviour is measured separately by the manual evaluator and its
 * recorded fixtures.</p>
 */
class PromptContractTest {

    private static final List<String> REQUIRED_RULES = List.of(
            "WHEN TO SKIP",
            "only reads files or searches the codebase to answer a question",
            "only lists or explores directories or project structure",
            "only views command output",
            "makes no change to code, tests, configuration, deployment files or documentation",
            "WHEN TO RECORD (never skip these)",
            "fixes a bug",
            "adds or changes tests",
            "refactors code while preserving behaviour",
            "changes configuration, build or deployment",
            "fixes a security issue",
            "TITLE CONTRACT",
            "verb> <object> <key identifier>",
            "verbatim",
            "Copy EVERY identifier that names the changed artifact",
            "decisive cause or symptom noun",
            "Length: 20-100 characters",
            "FACTS CONTRACT",
            "verifiable from the observed tool input/output",
            "1-5 facts",
            "Do not spend a fact on process noise",
            "CONCEPTS CONTRACT",
            "<skip>short reason</skip>");

    @Test
    @DisplayName("init prompt states the skip, must-record, title and facts rules")
    void initPromptStatesTheContract() throws Exception {
        assertContract(resource("prompts/init.txt"));
    }

    @Test
    @DisplayName("continuation prompt carries the same rules as init")
    void continuationPromptCarriesTheSameRules() throws Exception {
        assertContract(resource("prompts/continuation.txt"));
    }

    @Test
    @DisplayName("pure file read is scripted as <skip> and is not parsed as an observation")
    void pureReadSkips() throws Exception {
        Map<String, String> script = Map.of(
                "Button.tsx", "<skip>read-only answer, no change</skip>");

        String user = fill(resource("prompts/observation.txt"), "Read",
                "/repo/frontend", "{\"file_path\":\"src/components/Button.tsx\"}",
                "Read 42 lines of Button.tsx; the file renders a styled button with variant props.");
        String answer = new ScriptedChatModel(script).call(
                new Prompt(List.of(
                        new org.springframework.ai.chat.messages.SystemMessage(resource("prompts/init.txt")),
                        new org.springframework.ai.chat.messages.UserMessage(user))))
                .getResult().getOutput().getText();

        assertThat(XmlParser.extractTag(answer, "skip")).contains("read-only");
        assertThatThrownBy(() -> XmlParser.parseObservation(answer))
                .isInstanceOf(com.ablueforce.cortexce.exception.DataValidationException.class);
    }

    @Test
    @DisplayName("a reproduction test is scripted as an observation, not a skip")
    void reproductionTestProducesObservation() throws Exception {
        Map<String, String> script = Map.of(
                "reportUsesBusinessTimezone", observation(
                        "feature",
                        "Added DailyReportTest#reportUsesBusinessTimezone for Asia/Shanghai",
                        "DailyReportTest#reportUsesBusinessTimezone pins 2026-11-01T23:30Z "
                                + "and expects the Asia/Shanghai business date 2026-11-02"));

        String user = fill(resource("prompts/observation.txt"), "Write",
                "/repo/reporting", "{\"file_path\":\"src/test/java/com/acme/reporting/DailyReportTest.java\"}",
                "Added DailyReportTest#reportUsesBusinessTimezone. Test passes.");
        String answer = new ScriptedChatModel(script).call(
                new Prompt(List.of(
                        new org.springframework.ai.chat.messages.SystemMessage(resource("prompts/init.txt")),
                        new org.springframework.ai.chat.messages.UserMessage(user))))
                .getResult().getOutput().getText();

        assertThat(XmlParser.extractTag(answer, "skip")).isNull();
        XmlParser.ParsedObservation parsed = XmlParser.parseObservation(answer);
        assertThat(parsed.type).isEqualTo("feature");
        assertThat(parsed.title).contains("DailyReportTest#reportUsesBusinessTimezone");
        assertThat(parsed.facts).anySatisfy(fact ->
                assertThat(fact).contains("Asia/Shanghai").contains("2026-11-02"));
    }

    @Test
    @DisplayName("title identifiers are preserved verbatim, not translated")
    void titleKeepsIdentifiersVerbatim() throws Exception {
        Map<String, String> script = Map.of(
                "UserCache", observation("bugfix", "Fixed race in UserCache with computeIfAbsent",
                        "UserCache now loads entries through computeIfAbsent so concurrent readers "
                                + "cannot overwrite each other"));

        String user = fill(resource("prompts/observation.txt"), "Edit",
                "/repo/user-cache", "{\"file_path\":\"src/main/java/com/acme/cache/UserCache.java\"}",
                "UserCache now loads under computeIfAbsent; CacheConcurrencyTest passes.");
        String answer = new ScriptedChatModel(script).call(
                new Prompt(List.of(
                        new org.springframework.ai.chat.messages.SystemMessage(resource("prompts/init.txt")),
                        new org.springframework.ai.chat.messages.UserMessage(user))))
                .getResult().getOutput().getText();

        XmlParser.ParsedObservation parsed = XmlParser.parseObservation(answer);
        assertThat(parsed.title).contains("UserCache").contains("computeIfAbsent");
    }

    @Test
    @DisplayName("every observation template placeholder is consumed by the filler")
    void observationTemplatePlaceholdersAreConsumed() throws Exception {
        String filled = fill(resource("prompts/observation.txt"), "Edit", "/repo",
                "{\"file_path\":\"A.java\"}", "done");

        assertThat(filled).doesNotContain("{{", "}}");
        assertThat(filled).contains("A.java").contains("/repo").contains("done");
    }

    private static void assertContract(String prompt) {
        for (String rule : REQUIRED_RULES) {
            assertThat(prompt)
                    .as("prompt must state: " + rule)
                    .contains(rule);
        }
    }

    private static String resource(String path) throws Exception {
        try (InputStream in = PromptContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("classpath resource " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String fill(String template, String toolName, String cwd,
                               String toolInput, String toolOutput) {
        return template
                .replace("{{toolName}}", toolName)
                .replace("{{occurredAt}}", "2026-11-03T10:00:00Z")
                .replace("{{cwd}}", cwd)
                .replace("{{toolInput}}", toolInput)
                .replace("{{toolOutput}}", toolOutput)
                .replace("{{signals}}", TraceSignals.hintBlock(toolInput, toolOutput));
    }

    private static String observation(String type, String title, String fact) {
        return "<observation>\n"
                + "  <type>" + type + "</type>\n"
                + "  <title>" + title + "</title>\n"
                + "  <subtitle>offline prompt-contract check</subtitle>\n"
                + "  <facts><fact>" + fact + "</fact></facts>\n"
                + "  <narrative>" + fact + "</narrative>\n"
                + "  <concepts><concept>what-changed</concept><concept>pattern</concept></concepts>\n"
                + "  <files_read></files_read>\n"
                + "  <files_modified><file>src/main/java/com/acme/A.java</file></files_modified>\n"
                + "</observation>";
    }

    /**
     * Routes a prompt to a canned answer by a substring of the prompt. An unmatched prompt throws,
     * so a template change cannot silently turn a scenario into a false pass.
     */
    private static final class ScriptedChatModel implements ChatModel {

        private final Map<String, String> answers = new LinkedHashMap<>();

        private ScriptedChatModel(Map<String, String> answers) {
            this.answers.putAll(answers);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            String contents = prompt.getContents();
            for (Map.Entry<String, String> entry : answers.entrySet()) {
                if (contents.contains(entry.getKey())) {
                    return new ChatResponse(List.of(
                            new Generation(new AssistantMessage(entry.getValue()))));
                }
            }
            throw new IllegalStateException("no scripted answer for prompt: " + contents);
        }
    }
}
