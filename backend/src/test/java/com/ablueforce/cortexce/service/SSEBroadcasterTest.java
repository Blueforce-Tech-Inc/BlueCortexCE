package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SSEBroadcaster.
 * Uses real SseEmitter instances (not mocks).
 *
 * Observable behaviors tested:
 * - add/remove affect clientCount
 * - add throws at MAX_SSE_CONNECTIONS limit
 * - broadcast to zero clients is a no-op
 * - broadcast with live emitters doesn't throw
 * - remove from empty broadcaster is safe
 */
class SSEBroadcasterTest {

    private SSEBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new SSEBroadcaster();
    }

    // ===== add() tests =====

    @Test
    void add_withinLimit_incrementsCount() {
        broadcaster.add(new SseEmitter(60_000L));
        assertThat(broadcaster.getClientCount()).isEqualTo(1);
    }

    @Test
    void add_multipleClients_incrementsCount() {
        broadcaster.add(new SseEmitter(60_000L));
        broadcaster.add(new SseEmitter(60_000L));
        broadcaster.add(new SseEmitter(60_000L));
        assertThat(broadcaster.getClientCount()).isEqualTo(3);
    }

    @Test
    void add_atLimit_throwsException() {
        for (int i = 0; i < Constants.MAX_SSE_CONNECTIONS; i++) {
            broadcaster.add(new SseEmitter(60_000L));
        }
        assertThatThrownBy(() -> broadcaster.add(new SseEmitter(60_000L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Maximum SSE connections exceeded");
    }

    // ===== remove() tests =====

    @Test
    void remove_decreasesCount() {
        SseEmitter e = new SseEmitter(60_000L);
        broadcaster.add(e);
        broadcaster.remove(e);
        assertThat(broadcaster.getClientCount()).isEqualTo(0);
    }

    @Test
    void remove_oneOfMany_decrementsOnly() {
        SseEmitter e1 = new SseEmitter(60_000L);
        SseEmitter e2 = new SseEmitter(60_000L);
        SseEmitter e3 = new SseEmitter(60_000L);
        broadcaster.add(e1);
        broadcaster.add(e2);
        broadcaster.add(e3);
        broadcaster.remove(e2);
        assertThat(broadcaster.getClientCount()).isEqualTo(2);
    }

    @Test
    void remove_unknownEmitter_noEffect() {
        SseEmitter e1 = new SseEmitter(60_000L);
        SseEmitter e2 = new SseEmitter(60_000L);
        broadcaster.add(e1);
        broadcaster.remove(e2); // not in list — no-op
        assertThat(broadcaster.getClientCount()).isEqualTo(1);
    }

    @Test
    void remove_fromEmpty_noErrors() {
        SseEmitter e = new SseEmitter(60_000L);
        broadcaster.remove(e); // not added — no-op
        assertThat(broadcaster.getClientCount()).isEqualTo(0);
    }

    // ===== getClientCount() tests =====

    @Test
    void getClientCount_initiallyZero() {
        assertThat(new SSEBroadcaster().getClientCount()).isEqualTo(0);
    }

    @Test
    void getClientCount_afterAddAndRemove() {
        SseEmitter e1 = new SseEmitter(60_000L);
        SseEmitter e2 = new SseEmitter(60_000L);
        broadcaster.add(e1);
        broadcaster.add(e2);
        broadcaster.remove(e1);
        assertThat(broadcaster.getClientCount()).isEqualTo(1);
    }

    // ===== broadcast() tests =====

    @Test
    void broadcast_toZeroClients_noException() {
        // Broadcasting to zero clients must not throw
        broadcaster.broadcast(java.util.Map.of("type", "test"), "test_event");
        assertThat(broadcaster.getClientCount()).isEqualTo(0);
    }

    @Test
    void broadcast_withLiveClients_noException() {
        broadcaster.add(new SseEmitter(60_000L));
        broadcaster.add(new SseEmitter(60_000L));
        // Must not throw
        broadcaster.broadcast(java.util.Map.of("type", "test"), "test_event");
        // Both emitters still registered
        assertThat(broadcaster.getClientCount()).isEqualTo(2);
    }

    @Test
    void broadcast_nullEventName_noException() {
        broadcaster.add(new SseEmitter(60_000L));
        // null eventName is allowed (unnamed SSE event)
        broadcaster.broadcast(java.util.Map.of("type", "new_prompt"), null);
        assertThat(broadcaster.getClientCount()).isEqualTo(1);
    }

    // ===== integration tests =====

    @Test
    void fullLifecycle_addBroadcastRemove_noErrors() throws Exception {
        SseEmitter e = new SseEmitter(60_000L);
        broadcaster.add(e);

        // First broadcast succeeds
        broadcaster.broadcast(java.util.Map.of("type", "event1"), "event1");
        assertThat(broadcaster.getClientCount()).isEqualTo(1);

        // Remove
        broadcaster.remove(e);
        assertThat(broadcaster.getClientCount()).isEqualTo(0);

        // Second broadcast to empty broadcaster is fine
        broadcaster.broadcast(java.util.Map.of("type", "event2"), "event2");
    }

    @Test
    void addRemoveBroadcastCycle_multipleClients() {
        SseEmitter e1 = new SseEmitter(60_000L);
        SseEmitter e2 = new SseEmitter(60_000L);

        broadcaster.add(e1);
        broadcaster.add(e2);

        broadcaster.broadcast(java.util.Map.of("type", "msg1"), "msg1");
        assertThat(broadcaster.getClientCount()).isEqualTo(2);

        broadcaster.remove(e1);

        broadcaster.broadcast(java.util.Map.of("type", "msg2"), "msg2");
        assertThat(broadcaster.getClientCount()).isEqualTo(1);

        broadcaster.remove(e2);
        assertThat(broadcaster.getClientCount()).isEqualTo(0);

        // Final broadcast to empty broadcaster
        broadcaster.broadcast(java.util.Map.of("type", "msg3"), "msg3");
    }

    @Test
    void broadcast_concurrentSafe_snapshotCopy() throws Exception {
        // Verify broadcast doesn't throw ConcurrentModificationException
        for (int i = 0; i < 20; i++) {
            broadcaster.add(new SseEmitter(60_000L));
        }
        // Multiple broadcasts should not throw
        for (int i = 0; i < 10; i++) {
            broadcaster.broadcast(java.util.Map.of("type", "batch_" + i), "batch_" + i);
        }
        assertThat(broadcaster.getClientCount()).isEqualTo(20);
    }

    @Test
    void addLimit_boundary() {
        // Just under the limit
        for (int i = 0; i < Constants.MAX_SSE_CONNECTIONS - 1; i++) {
            broadcaster.add(new SseEmitter(60_000L));
        }
        assertThat(broadcaster.getClientCount()).isEqualTo(Constants.MAX_SSE_CONNECTIONS - 1);

        // Adding one more should reach the limit
        broadcaster.add(new SseEmitter(60_000L));
        assertThat(broadcaster.getClientCount()).isEqualTo(Constants.MAX_SSE_CONNECTIONS);

        // And one more should throw
        assertThatThrownBy(() -> broadcaster.add(new SseEmitter(60_000L)))
            .isInstanceOf(IllegalStateException.class);
    }
}
