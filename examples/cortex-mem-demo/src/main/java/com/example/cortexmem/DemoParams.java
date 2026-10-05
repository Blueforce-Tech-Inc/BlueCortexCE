package com.example.cortexmem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Query-parameter parsing shared by the demo controllers.
 *
 * <p>The other three demos each validate integer query parameters before
 * forwarding them, but not identically, so the same request produced four
 * different answers. The backend itself is permissive: its {@code @RequestParam int}
 * binding accepts {@code " 5"} as 5 and — because Spring converts through
 * {@code Integer.decode} semantics — reads {@code "0x10"} as the <em>hexadecimal</em>
 * 16. Measured against a live backend, {@code /api/observations?limit=0x10}
 * returns 16 rows, not an error.</p>
 *
 * <p>Binding straight to {@code Integer} therefore skipped validation entirely in
 * this demo: it was the only one of the four that answered 200 to {@code limit=0x10}
 * and returned 16 observations for it. The other three rejected the value.</p>
 *
 * <p>The rule implemented here is the one the other three demos already agreed
 * on, and the one the backend's own documentation implies:</p>
 * <ul>
 *   <li>absent or empty → the caller's default (all four already agreed here)</li>
 *   <li>optional leading {@code +} or {@code -}, then digits only — so
 *       {@code " 5"}, {@code "+5"} and {@code "-1"} are integers, while
 *       {@code "0x10"}, {@code "1e3"}, {@code "1.5"} and {@code "10abc"} are not</li>
 *   <li>the result must fall inside the caller's range</li>
 * </ul>
 *
 * <p>Controllers that take the raw {@code String} and call {@link #boundedInt} rather than
 * declaring an integer parameter are covered by this rule. <strong>Not every controller
 * is:</strong> the call sites sit in {@code SearchController}, {@code ObservationsController}
 * and {@code ExtractionController} alone, while <strong>three</strong> other controllers bind
 * their numeric parameters directly — {@code ExperiencesController} ({@code count} and
 * {@code maxChars}, both {@code Integer}, range-checked in the handler),
 * {@code MemoryController} ({@code count} on two endpoints plus {@code maxChars}, all primitive
 * {@code int}, range-checked in the handler) and {@code SessionLifecycleController}
 * ({@code promptNumber}, also primitive {@code int} and the only numeric parameter in this demo
 * with no range check at all, so a negative value is accepted and persisted).
 * Those six parameters therefore get Spring's own binding instead, which accepts a different
 * set of values and answers with a different 400 body — measured live on one process,
 * {@code /demo/observations?limit=1_0} answers
 * {@code {"error":"limit must be an integer"}} while {@code /demo/experiences?count=1_0},
 * {@code /memory/experiences?count=1_0}, {@code /demo/iclprompt?maxChars=1_0} and
 * {@code /demo/session/prompt?promptNumber=1_0} all answer Spring's default
 * {@code {"timestamp":…,"status":400,"error":"Bad Request"}}, and {@code 0x10} is accepted on
 * every one of those six while {@code limit=0x10} is rejected here. Note that
 * {@code MemoryController} is the one controller served outside the {@code /demo} prefix, so
 * its paths read {@code /memory/...} — that is what the demo README documents.
 * Tracked as P2-56; closing it means choosing an integer grammar first (P2-55), so this
 * note describes the code as it stands rather than as it is meant to be.</p>
 */
final class DemoParams {

    private DemoParams() {
    }

    /**
     * Thrown for a query parameter that is present but not a valid integer in
     * range. Deliberately its own type rather than {@code IllegalArgumentException}:
     * the advice below matches on this class alone, so an unrelated illegal
     * argument thrown from inside a handler is not silently turned into a 400.
     */
    static final class InvalidParam extends RuntimeException {
        InvalidParam(String message) {
            super(message);
        }
    }

    /**
     * @param raw         the raw query value; {@code null} or the empty string means
     *                     "not supplied"
     * @param defaultValue value to use when the parameter is absent or empty
     * @param min         inclusive lower bound
     * @param max         inclusive upper bound
     * @param name        parameter name, used in the error message
     * @return the parsed value, or {@code defaultValue} when not supplied
     * @throws InvalidParam if the value is not an integer, or is out of range
     */
    static int boundedInt(String raw, int defaultValue, int min, int max, String name) {
        // Only a genuinely empty value means "not supplied". A whitespace-only
        // value such as "?limit=%20" is NOT the same as an absent parameter: the
        // backend trims and then fails to convert "", so it answers 400, and
        // Go/JS/Python all agree. Treating isBlank() as absent made this demo the
        // only one of the four to accept it (round 211 recheck).
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        String text = raw.trim();
        // Integer.parseInt would accept a leading '+', but a manual check is used
        // so the accepted grammar is exactly "optional sign, then digits" and
        // nothing else -- no hex, no exponent, no trailing garbage.
        boolean signed = text.startsWith("+") || text.startsWith("-");
        String digits = signed ? text.substring(1) : text;
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            throw new InvalidParam(name + " must be an integer");
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            // Digits that overflow an int, e.g. "99999999999".
            throw new InvalidParam(name + " must be an integer");
        }
        if (value < min || value > max) {
            throw new InvalidParam(name + " must be between " + min + " and " + max);
        }
        return value;
    }

    /**
     * Maps only {@link InvalidParam} to 400. Everything else keeps whatever
     * handling it already had.
     */
    @RestControllerAdvice
    static class InvalidParamAdvice {

        @ExceptionHandler(InvalidParam.class)
        ResponseEntity<Map<String, Object>> onInvalidParam(InvalidParam e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
