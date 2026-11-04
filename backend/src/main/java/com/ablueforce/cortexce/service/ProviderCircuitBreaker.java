package com.ablueforce.cortexce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fast-fails provider calls after the provider has been unreachable for a while.
 *
 * <p>Every queue job calls the provider at least once, and a job-level retry
 * means a new call. With a 120 s read timeout, a completely unreachable provider
 * occupies a worker for roughly six minutes per job and the queue keeps feeding
 * more jobs into the same pocket. The breaker does not try to save the job — its
 * answer is still a failure — it stops the provider call from being attempted at
 * all, so a worker returns in milliseconds instead of minutes and the retry
 * budget reaches its bound quickly.
 *
 * <p>Only failures that mean "the provider did not answer" count: {@code TIMEOUT}
 * and {@code UNAVAILABLE}. A 401, a 429, or a 2xx with unusable content proves the
 * provider is reachable, so those reset the streak. That keeps a bad API key from
 * looking like an outage, and keeps fast-failing from hiding a rate limit that
 * would clear by itself.
 *
 * <p>After {@code cooldown-ms} the breaker lets exactly one probe through
 * (HALF_OPEN). A probe that answers at all closes the circuit; a probe that times
 * out or cannot connect reopens it for another cooldown. Requests never wait for
 * the probe: concurrent callers are rejected until the probe reports.
 *
 * <p>Defaults: three consecutive unreachable outcomes open the circuit, and the
 * cooldown is one minute. Both are configurable and the whole feature can be
 * switched off ({@code claudemem.llm.circuit-breaker.enabled=false}).
 */
@Component
public class ProviderCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(ProviderCircuitBreaker.class);

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final Object lock = new Object();
    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAtNanos;

    @Value("${claudemem.llm.circuit-breaker.enabled:true}")
    private boolean enabled = true;

    @Value("${claudemem.llm.circuit-breaker.failure-threshold:3}")
    private int failureThreshold = 3;

    @Value("${claudemem.llm.circuit-breaker.cooldown-ms:60000}")
    private long cooldownMs = 60_000L;

    /**
     * @return true when the caller may send the request. A half-open circuit allows
     *         exactly one caller (the probe); the others are rejected immediately.
     */
    public boolean tryAcquire() {
        synchronized (lock) {
            if (!enabled) {
                return true;
            }
            if (state == State.CLOSED) {
                return true;
            }
            if (state == State.OPEN && System.nanoTime() - openedAtNanos >= cooldownNanos()) {
                state = State.HALF_OPEN;
                log.info("Provider circuit half-open after {} ms; sending one probe", cooldownMs);
                return true;
            }
            return false;
        }
    }

    /**
     * Record the outcome of a call that was admitted by {@link #tryAcquire()}.
     *
     * @param kind {@code null} on a usable answer; otherwise the classified failure
     */
    public void onOutcome(LlmService.FailureKind kind) {
        synchronized (lock) {
            if (!enabled) {
                return;
            }
            if (!countsAsUnreachable(kind)) {
                if (state != State.CLOSED) {
                    log.info("Provider answered again ({}); circuit closed", kind == null ? "success" : kind);
                }
                state = State.CLOSED;
                consecutiveFailures = 0;
                return;
            }
            consecutiveFailures++;
            if (state == State.HALF_OPEN) {
                open("probe failed with " + kind);
                return;
            }
            if (state == State.CLOSED && consecutiveFailures >= Math.max(1, failureThreshold)) {
                open(failureThreshold + " consecutive unreachable outcomes (last: " + kind + ")");
            }
        }
    }

    /** Test/observability accessor. */
    public State state() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * Return to CLOSED and drop the streak. The breaker is a singleton, so a
     * shared-Spring-context test suite must start each test from this state.
     */
    public void reset() {
        synchronized (lock) {
            state = State.CLOSED;
            consecutiveFailures = 0;
            openedAtNanos = 0;
        }
    }

    /** Test/observability accessor. */
    public int consecutiveFailures() {
        synchronized (lock) {
            return consecutiveFailures;
        }
    }

    private boolean countsAsUnreachable(LlmService.FailureKind kind) {
        return kind == LlmService.FailureKind.TIMEOUT || kind == LlmService.FailureKind.UNAVAILABLE;
    }

    private long cooldownNanos() {
        return Math.max(0L, cooldownMs) * 1_000_000L;
    }

    private void open(String reason) {
        state = State.OPEN;
        openedAtNanos = System.nanoTime();
        consecutiveFailures = 0;
        log.warn("Provider circuit opened: {}; failing fast for {} ms", reason, cooldownMs);
    }
}
