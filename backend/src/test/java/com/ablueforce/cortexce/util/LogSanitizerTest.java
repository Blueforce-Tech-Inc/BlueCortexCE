package com.ablueforce.cortexce.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Credentials must never reach a log line or an exception detail. The provider
 * response body is kept for diagnosis, minus the parts that look like secrets.
 */
class LogSanitizerTest {

    @Test
    @DisplayName("api keys and bearer tokens are masked")
    void masksKnownSecretShapes() {
        assertThat(LogSanitizer.maskSecrets("key sk-abcdef1234567890 rejected"))
            .isEqualTo("key *** rejected");
        assertThat(LogSanitizer.maskSecrets("api_key=supersecretvalue"))
            .isEqualTo("api_key=***");
        assertThat(LogSanitizer.maskSecrets("\"authorization\": \"Bearer abcdefghijklmnop\""))
            .contains("***")
            .doesNotContain("abcdefghijklmnop");
        assertThat(LogSanitizer.maskSecrets("Authorization: Bearer abcdefghijklmnop"))
            .isEqualTo("Authorization: ***");
    }

    @Test
    @DisplayName("ordinary diagnostics survive unchanged")
    void keepsUsefulContext() {
        assertThat(LogSanitizer.maskSecrets("HTTP 429 from longcat: rate limit exceeded"))
            .isEqualTo("HTTP 429 from longcat: rate limit exceeded");
        assertThat(LogSanitizer.maskSecrets(null)).isNull();
        assertThat(LogSanitizer.maskSecrets("")).isEmpty();
    }

    @Test
    @DisplayName("safeDetail collapses whitespace, masks, and bounds length")
    void safeDetailIsBounded() {
        String masked = LogSanitizer.safeDetail("line one\nline two api_key=topsecretvalue", 120);
        assertThat(masked).doesNotContain("\n").doesNotContain("topsecretvalue");
        assertThat(LogSanitizer.safeDetail("x".repeat(400), 160))
            .hasSize(163)
            .endsWith("...");
        assertThat(LogSanitizer.safeDetail(null, 160)).isEqualTo("no message");
    }

    @Test
    @DisplayName("fingerprint identifies a model answer without writing it")
    void fingerprintIsStableAndDoesNotLeakThePayload() {
        String payload = "narrative with a token sk-live-1234567890";

        String fingerprint = LogSanitizer.fingerprint(payload);

        assertThat(fingerprint).hasSize(12).matches("[0-9a-f]{12}");
        assertThat(fingerprint).isEqualTo(LogSanitizer.fingerprint(payload));
        assertThat(fingerprint)
            .isNotEqualTo(LogSanitizer.fingerprint("narrative with a token sk-live-1234567891"));
        assertThat(fingerprint).doesNotContain("narrative").doesNotContain("sk-live");
        assertThat(LogSanitizer.fingerprint(null)).isEqualTo("empty");
        assertThat(LogSanitizer.fingerprint("")).isEqualTo("empty");
    }
}
