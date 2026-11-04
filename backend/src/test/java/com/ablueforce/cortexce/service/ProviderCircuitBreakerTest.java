package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.service.LlmService.FailureKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the three states the breaker was added for: open after repeated
 * unreachable outcomes, admit one probe after the cooldown, and close again as
 * soon as the provider answers.
 */
class ProviderCircuitBreakerTest {

    private ProviderCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new ProviderCircuitBreaker();
        ReflectionTestUtils.setField(breaker, "enabled", true);
        ReflectionTestUtils.setField(breaker, "failureThreshold", 3);
        ReflectionTestUtils.setField(breaker, "cooldownMs", 50L);
    }

    @Test
    void threeUnreachableOutcomesOpenTheCircuit() {
        breaker.onOutcome(FailureKind.TIMEOUT);
        breaker.onOutcome(FailureKind.UNAVAILABLE);
        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.CLOSED);

        breaker.onOutcome(FailureKind.TIMEOUT);

        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).as("an open circuit must not send requests").isFalse();
    }

    @Test
    void reachableFailuresDoNotCountTowardsOpening() {
        breaker.onOutcome(FailureKind.AUTHENTICATION);
        breaker.onOutcome(FailureKind.RATE_LIMIT);
        breaker.onOutcome(FailureKind.INVALID_RESPONSE);

        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.CLOSED);
        assertThat(breaker.consecutiveFailures()).isZero();
    }

    @Test
    void aSuccessResetsTheFailureStreak() {
        breaker.onOutcome(FailureKind.TIMEOUT);
        breaker.onOutcome(FailureKind.TIMEOUT);
        breaker.onOutcome(null);

        assertThat(breaker.consecutiveFailures()).isZero();
        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.CLOSED);
    }

    @Test
    void cooldownAdmitsOneProbeAndASuccessClosesTheCircuit() throws Exception {
        breaker.onOutcome(FailureKind.TIMEOUT);
        breaker.onOutcome(FailureKind.TIMEOUT);
        breaker.onOutcome(FailureKind.TIMEOUT);
        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.OPEN);

        Thread.sleep(60);
        assertThat(breaker.tryAcquire()).as("the probe after the cooldown is admitted").isTrue();
        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.tryAcquire()).as("only one probe may be in flight").isFalse();

        breaker.onOutcome(null);

        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    void aFailedProbeReopensTheCircuitForAnotherCooldown() throws Exception {
        breaker.onOutcome(FailureKind.TIMEOUT);
        breaker.onOutcome(FailureKind.TIMEOUT);
        breaker.onOutcome(FailureKind.TIMEOUT);

        Thread.sleep(60);
        assertThat(breaker.tryAcquire()).isTrue();
        breaker.onOutcome(FailureKind.UNAVAILABLE);

        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).as("a failed probe must not leave the circuit half-open").isFalse();
    }

    @Test
    void disabledBreakerAlwaysAdmits() {
        ReflectionTestUtils.setField(breaker, "enabled", false);
        for (int i = 0; i < 10; i++) {
            breaker.onOutcome(FailureKind.UNAVAILABLE);
        }

        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire()).isTrue();
    }
}
