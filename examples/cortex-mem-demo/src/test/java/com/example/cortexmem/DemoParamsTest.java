package com.example.cortexmem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the integer-query-parameter rule the four demos now share.
 *
 * <p>The rule was chosen by measurement, not taste. Against a live backend,
 * {@code ?limit=0x10} returns 16 observations (Spring converts through
 * {@code Integer.decode} semantics), {@code ?limit= 5} and {@code ?limit=+5}
 * both return 5, and {@code ?limit=} returns the backend's default page. The Go,
 * Python and JS demos rejected some of those and accepted others, each being the
 * lone outlier in exactly one case, and the Java demo rejected none because it
 * bound the parameter straight to {@code Integer} and inherited the backend's
 * interpretation.</p>
 *
 * <p>What is pinned here is the agreed rule, so a future change to one demo
 * cannot silently reintroduce a fifth dialect: absent or empty is the default,
 * an optional sign followed by digits is an integer, and hex, exponent,
 * fraction, trailing garbage and overflow are not.</p>
 */
class DemoParamsTest {

    private static int limit(String raw) {
        return DemoParams.boundedInt(raw, 0, 0, 100, "limit");
    }

    @Test
    void absentOrEmptyUsesTheDefault() {
        assertEquals(0, limit(null));
        assertEquals(0, limit(""));
        assertEquals(0, limit("   "));
    }

    @Test
    void plainIntegersParse() {
        assertEquals(5, limit("5"));
        assertEquals(100, limit("100"));
    }

    @Test
    void leadingPlusIsAccepted() {
        // The backend, and the other three demos, all read "+5" as 5. The JS demo
        // used to reject it because its pattern was ^-?\d+$ with no plus branch.
        assertEquals(5, limit("+5"));
    }

    @Test
    void negativeIsRejectedByRangeNotByGrammar() {
        // "-5" is a well-formed integer; it is the range that refuses it.
        DemoParams.InvalidParam e =
                assertThrows(DemoParams.InvalidParam.class, () -> limit("-5"));
        assertTrue(e.getMessage().contains("between 0 and 100"), e.getMessage());
    }

    @Test
    void surroundingWhitespaceIsTolerated() {
        assertEquals(5, limit(" 5"));
        assertEquals(5, limit("5 "));
        assertEquals(5, limit("\t5\n"));
    }

    @Test
    void hexIsRejected() {
        // The regression this whole class exists for: the backend answers 200 and
        // returns 16 rows for this. A demo must not pass that through.
        DemoParams.InvalidParam e =
                assertThrows(DemoParams.InvalidParam.class, () -> limit("0x10"));
        assertTrue(e.getMessage().contains("must be an integer"), e.getMessage());
    }

    @Test
    void trailingGarbageIsRejected() {
        assertThrows(DemoParams.InvalidParam.class, () -> limit("10abc"));
        assertThrows(DemoParams.InvalidParam.class, () -> limit("5.0"));
        assertThrows(DemoParams.InvalidParam.class, () -> limit("1e3"));
    }

    @Test
    void loneSignIsRejected() {
        assertThrows(DemoParams.InvalidParam.class, () -> limit("+"));
        assertThrows(DemoParams.InvalidParam.class, () -> limit("-"));
    }

    @Test
    void overflowIsRejectedRatherThanWrapped() {
        assertThrows(DemoParams.InvalidParam.class, () -> limit("99999999999"));
    }

    @Test
    void outOfRangeUpperBoundIsRejected() {
        assertThrows(DemoParams.InvalidParam.class, () -> limit("101"));
    }

    @Test
    void messageNamesTheParameter() {
        DemoParams.InvalidParam e = assertThrows(DemoParams.InvalidParam.class,
                () -> DemoParams.boundedInt("abc", 0, 0, 100, "count"));
        assertEquals("count must be an integer", e.getMessage());
    }
}
