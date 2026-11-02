package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.util.XmlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the deterministic extraction guard. No provider, no database: the duplicate
 * lookup is mocked, every rule is exercised directly.
 */
@ExtendWith(MockitoExtension.class)
class ExtractionValidationServiceTest {

    private static final String INPUT = "{\"file_path\":\"src/main/java/com/acme/payment/PaymentService.java\","
            + "\"new_string\":\"return repository.findByOrderId(orderId).orElseThrow("
            + "() -> new PaymentNotFoundException(orderId));\"}";
    private static final String OUTPUT = "Edit applied to PaymentService.java. "
            + "PaymentNotFoundException is now thrown for a missing orderId. "
            + "PaymentServiceTest#missingOrderThrows passes.";

    @Mock private ObservationRepository observationRepository;

    private ExtractionValidationService service;
    private UUID scopeId;

    @BeforeEach
    void setUp() {
        service = new ExtractionValidationService(observationRepository, true, 100, 5, 20);
        scopeId = UUID.randomUUID();
    }

    @Test
    @DisplayName("a grounded fact is kept and speculation is dropped")
    void keepsGroundedFactsAndDropsSpeculation() {
        XmlParser.ParsedObservation parsed = observation("Fixed PaymentService missing order crash",
                List.of("PaymentService now throws PaymentNotFoundException for a missing orderId",
                        "This change should make the payment path much better and faster"));

        ExtractionValidationService.Result result = validate(parsed);

        assertThat(result.accepted()).isTrue();
        assertThat(result.facts())
                .containsExactly("PaymentService now throws PaymentNotFoundException for a missing orderId");
        assertThat(result.droppedFacts())
                .containsExactly("This change should make the payment path much better and faster");
    }

    @Test
    @DisplayName("when every fact is unsupported the observation is rejected with a reason")
    void allFactsDroppedRejects() {
        XmlParser.ParsedObservation parsed = observation("Improved the payment module",
                List.of("The payment flow is now better and more robust",
                        "Users will probably be happier with this change"));

        ExtractionValidationService.Result result = validate(parsed);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("no verifiable facts after evidence check");
        assertThat(result.droppedFacts()).hasSize(2);
    }

    @Test
    @DisplayName("an empty title is rejected")
    void emptyTitleRejects() {
        XmlParser.ParsedObservation parsed = observation("", List.of("PaymentService throws PaymentNotFoundException"));

        ExtractionValidationService.Result result = validate(parsed);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("empty title");
    }

    @Test
    @DisplayName("a title over the length limit is rejected")
    void overlongTitleRejects() {
        XmlParser.ParsedObservation parsed = observation("x".repeat(101),
                List.of("PaymentService throws PaymentNotFoundException"));

        ExtractionValidationService.Result result = validate(parsed);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("title too long").contains("101");
    }

    @Test
    @DisplayName("facts are deduplicated and capped")
    void factsAreDeduplicatedAndCapped() {
        XmlParser.ParsedObservation parsed = observation("Fixed PaymentService order handling",
                List.of("PaymentService throws PaymentNotFoundException",
                        "paymentService throws PaymentNotFoundException",
                        "PaymentNotFoundException is imported",
                        "PaymentServiceTest#missingOrderThrows passes",
                        "the module build passes",
                        "PaymentService.java was edited",
                        "orderId is read from the repository"));

        ExtractionValidationService.Result result = validate(parsed);

        assertThat(result.accepted()).isTrue();
        assertThat(result.facts()).hasSize(5);
    }

    @Test
    @DisplayName("a same-scope same-type observation with a normalized equal title is a duplicate")
    void normalizedTitleDuplicateRejects() {
        when(observationRepository.findRecentByMemoryScopeId(eq(scopeId), any()))
                .thenReturn(List.of(existing("bugfix", "Fixed PaymentService missing order crash")));
        XmlParser.ParsedObservation parsed = observation(
                "Fixed PaymentService missing-order crash!",
                List.of("PaymentService throws PaymentNotFoundException"));

        ExtractionValidationService.Result result = validate(parsed);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).startsWith("duplicate of existing observation");
    }

    @Test
    @DisplayName("the same title under a different type is not a duplicate")
    void sameTitleDifferentTypeIsNotDuplicate() {
        when(observationRepository.findRecentByMemoryScopeId(eq(scopeId), any()))
                .thenReturn(List.of(existing("feature", "Fixed PaymentService missing order crash")));
        XmlParser.ParsedObservation parsed = observation("Fixed PaymentService missing order crash",
                List.of("PaymentService throws PaymentNotFoundException"));

        ExtractionValidationService.Result result = validate(parsed);

        assertThat(result.accepted()).isTrue();
    }

    @Test
    @DisplayName("a first write in a new project has no duplicate lookup")
    void nullScopeSkipsDuplicateLookup() {
        XmlParser.ParsedObservation parsed = observation("Fixed PaymentService missing order crash",
                List.of("PaymentService throws PaymentNotFoundException"));

        ExtractionValidationService.Result result = service.validate(parsed,
                "Edit", INPUT, OUTPUT, null);

        assertThat(result.accepted()).isTrue();
        verify(observationRepository, never()).findRecentByMemoryScopeId(any(), any());
    }

    @Test
    @DisplayName("disabled validation bypasses every check and keeps the parsed facts")
    void disabledValidationBypassesEverything() {
        ExtractionValidationService disabled =
                new ExtractionValidationService(observationRepository, false, 100, 5, 20);
        XmlParser.ParsedObservation parsed = observation("", List.of("unverifiable claim"));

        ExtractionValidationService.Result result = disabled.validate(parsed,
                "Edit", INPUT, OUTPUT, scopeId);

        assertThat(result.accepted()).isTrue();
        assertThat(result.facts()).containsExactly("unverifiable claim");
        assertThat(result.droppedFacts()).isEmpty();
        verify(observationRepository, never()).findRecentByMemoryScopeId(any(), any());
    }

    @Test
    @DisplayName("exact containment in the evidence is enough even without token overlap")
    void exactContainmentIsSupported() {
        assertThat(ExtractionValidationService.isSupported(
                "PaymentService throws PaymentNotFoundException", INPUT + "\n" + OUTPUT)).isTrue();
        assertThat(ExtractionValidationService.isSupported(
                "Telemetry dashboards were redesigned", INPUT + "\n" + OUTPUT)).isFalse();
    }

    private ExtractionValidationService.Result validate(XmlParser.ParsedObservation parsed) {
        return service.validate(parsed, "Edit", INPUT, OUTPUT, scopeId);
    }

    private static XmlParser.ParsedObservation observation(String title, List<String> facts) {
        XmlParser.ParsedObservation parsed = new XmlParser.ParsedObservation();
        parsed.type = "bugfix";
        parsed.title = title;
        parsed.facts = facts;
        parsed.concepts = List.of("problem-solution");
        return parsed;
    }

    private static ObservationEntity existing(String type, String title) {
        ObservationEntity entity = new ObservationEntity();
        entity.setType(type);
        entity.setTitle(title);
        return entity;
    }
}
