package com.ablueforce.cortexce.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VectorValidator — validates pgvector string format.
 * Covers valid formats, invalid inputs, edge cases, and dimension limits.
 */
class VectorValidatorTest {

    // ===== Valid vectors =====

    @Test
    void validVector_simple() {
        assertThat(VectorValidator.isValidVector("[1.0, 2.0, 3.0]")).isTrue();
    }

    @Test
    void validVector_integers() {
        assertThat(VectorValidator.isValidVector("[1, 2, 3]")).isTrue();
    }

    @Test
    void validVector_negative() {
        assertThat(VectorValidator.isValidVector("[-0.5, 0.5, -1.0]")).isTrue();
    }

    @Test
    void validVector_scientificNotation() {
        assertThat(VectorValidator.isValidVector("[1.0e-5, 2.0E+3, 3.0e0]")).isTrue();
    }

    @Test
    void validVector_singleElement() {
        assertThat(VectorValidator.isValidVector("[42.0]")).isTrue();
    }

    @Test
    void validVector_extraWhitespace() {
        assertThat(VectorValidator.isValidVector("[ 1.0 , 2.0 , 3.0 ]")).isTrue();
    }

    @Test
    void validVector_leadingPlus() {
        assertThat(VectorValidator.isValidVector("[+1.0, +2.0]")).isTrue();
    }

    @Test
    void validVector_leadingDot() {
        assertThat(VectorValidator.isValidVector("[.5, .1]")).isTrue();
    }

    // ===== Invalid vectors =====

    @Test
    void invalidVector_null() {
        assertThat(VectorValidator.isValidVector(null)).isFalse();
    }

    @Test
    void invalidVector_empty() {
        assertThat(VectorValidator.isValidVector("")).isFalse();
    }

    @Test
    void invalidVector_blank() {
        assertThat(VectorValidator.isValidVector("   ")).isFalse();
    }

    @Test
    void invalidVector_noBrackets() {
        assertThat(VectorValidator.isValidVector("1.0, 2.0, 3.0")).isFalse();
    }

    @Test
    void invalidVector_onlyOpenBracket() {
        assertThat(VectorValidator.isValidVector("[1.0, 2.0")).isFalse();
    }

    @Test
    void invalidVector_emptyBrackets() {
        assertThat(VectorValidator.isValidVector("[]")).isFalse();
    }

    @Test
    void invalidVector_alphaContent() {
        assertThat(VectorValidator.isValidVector("[abc, def]")).isFalse();
    }

    @Test
    void validVector_trailingComma() {
        // Parser is permissive with trailing commas (end-of-content break)
        assertThat(VectorValidator.isValidVector("[1.0, 2.0,]")).isTrue();
    }

    @Test
    void invalidVector_sqlInjection() {
        assertThat(VectorValidator.isValidVector("[1.0]; DROP TABLE users;--")).isFalse();
    }

    @Test
    void invalidVector_pathTraversal() {
        assertThat(VectorValidator.isValidVector("[../../../etc/passwd]")).isFalse();
    }

    @Test
    void invalidVector_tooLong() {
        // Build a string exceeding MAX_VECTOR_STRING_LENGTH (100000)
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 50001; i++) {
            if (i > 0) sb.append(",");
            sb.append("1.0");
        }
        sb.append("]");
        assertThat(VectorValidator.isValidVector(sb.toString())).isFalse();
    }

    @Test
    void invalidVector_signOnlyNoDigits() {
        assertThat(VectorValidator.isValidVector("[-]")).isFalse();
    }

    @Test
    void invalidVector_signAtEnd() {
        assertThat(VectorValidator.isValidVector("[1.0-]")).isFalse();
    }

    @Test
    void invalidVector_doubleExponent() {
        assertThat(VectorValidator.isValidVector("[1.0ee5]")).isFalse();
    }

    @Test
    void invalidVector_exponentNoDigits() {
        assertThat(VectorValidator.isValidVector("[1.0e]")).isFalse();
    }

    @Test
    void invalidVector_commaOnly() {
        assertThat(VectorValidator.isValidVector("[,]")).isFalse();
    }
}
