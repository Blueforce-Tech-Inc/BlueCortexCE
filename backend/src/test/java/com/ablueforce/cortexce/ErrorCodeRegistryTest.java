package com.ablueforce.cortexce;

import static org.assertj.core.api.Assertions.assertThat;

import com.ablueforce.cortexce.common.ErrorCode;
import com.ablueforce.cortexce.common.ErrorResponses;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The registry is the single source of truth for {@code error.code}. */
class ErrorCodeRegistryTest {

    @Test
    void codesAreUniqueNonEmptyAndSnakeCase() {
        Set<String> codes = new HashSet<>();
        for (ErrorCode value : ErrorCode.values()) {
            String code = value.code();
            assertThat(code)
                    .as("error code %s must be a non-empty snake_case token", value)
                    .matches("[a-z][a-z0-9_]*");
            assertThat(codes.add(code))
                    .as("error code %s is declared twice", code)
                    .isTrue();
            assertThat(ErrorCode.fromCode(code))
                    .as("fromCode must round-trip every registered value")
                    .contains(value);
        }
        assertThat(ErrorCode.codes()).containsExactlyInAnyOrderElementsOf(codes);
        assertThat(ErrorCode.fromCode("not_a_registered_code")).isEmpty();
    }

    @Test
    void everyStatusMappingUsesARegisteredCode() {
        for (int status : new int[] {400, 401, 403, 404, 409, 413, 429, 500, 503}) {
            String code = ErrorResponses.codeForStatus(status);
            assertThat(ErrorCode.fromCode(code))
                    .as("HTTP %s mapped to unregistered code %s", status, code)
                    .isPresent();
        }
        // The generic 4xx/5xx fallbacks matter more than the explicit cases:
        // they are what an unanticipated framework failure reaches.
        assertThat(ErrorCode.fromCode(ErrorResponses.codeForStatus(418))).isPresent();
        assertThat(ErrorCode.fromCode(ErrorResponses.codeForStatus(502))).isPresent();
    }
}
