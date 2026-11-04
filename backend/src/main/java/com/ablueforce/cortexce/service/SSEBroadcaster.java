package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe SSE broadcaster for real-time event distribution.
 * <p>
 * Uses CopyOnWriteArrayList for safe concurrent iteration during broadcast.
 * Dead connections are automatically cleaned up on IOException.
 *
 * <p>A heartbeat runs every {@code claudemem.sse.heartbeat-ms} (15 s by default) and sends an
 * SSE comment to every registered client. Two things depend on it: proxies and load
 * balancers keep an idle stream open instead of dropping it, and a client that vanished
 * silently is discovered on the next heartbeat instead of at the 30-minute timeout. Without
 * the heartbeat an idle deployment that never broadcast anything could not tell a live
 * stream from a closed socket.
 *
 * <p>{@code cortexce_sse_connections} exposes the live connection count to
 * {@code /actuator/prometheus} so the cap can be observed rather than guessed.
 */
@Component
public class SSEBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SSEBroadcaster.class);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Configurable so a deployment can raise the cap for many viewers without rebuilding.
     * The field initializer keeps the value correct when tests construct the class directly.
     */
    @Value("${claudemem.sse.max-connections:" + Constants.MAX_SSE_CONNECTIONS + "}")
    private int maxConnections = Constants.MAX_SSE_CONNECTIONS;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @PostConstruct
    void registerMetrics() {
        if (meterRegistry != null) {
            Gauge.builder("cortexce.sse.connections", this, SSEBroadcaster::getClientCount)
                .description("Live SSE connections registered with the broadcaster")
                .register(meterRegistry);
        }
    }

    public void add(SseEmitter emitter) {
        // P0: Enforce maximum connection limit to prevent DoS
        if (emitters.size() >= maxConnections) {
            log.warn("Maximum SSE connections ({}) exceeded, rejecting new connection", maxConnections);
            throw new IllegalStateException("Maximum SSE connections exceeded");
        }
        emitters.add(emitter);
        log.debug("SSE client connected. Total: {}", emitters.size());
    }

    /**
     * Sends a comment line to every client. A comment does not surface as a message in the
     * browser's {@code EventSource}, so the Viewer's {@code onmessage} handler never sees it,
     * but a write against a closed socket raises {@link IOException} and removes the emitter.
     */
    @Scheduled(fixedDelayString = "${claudemem.sse.heartbeat-ms:15000}",
        initialDelayString = "${claudemem.sse.heartbeat-ms:15000}")
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        List<SseEmitter> snapshot = new ArrayList<>(emitters);
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : snapshot) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            } catch (IllegalStateException e) {
                deadEmitters.add(emitter);
            }
        }
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.info("Removed {} dead SSE connection(s) on heartbeat; {} remain",
                deadEmitters.size(), emitters.size());
        }
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void remove(SseEmitter emitter) {
        emitters.remove(emitter);
        log.debug("SSE client disconnected. Total: {}", emitters.size());
    }

    /**
     * Broadcast an event to all connected SSE clients.
     * P1: Create snapshot copy before iteration to prevent ConcurrentModificationException
     * when remove() is called from onCompletion callback during broadcast.
     *
     * NOTE: Uses unnamed SSE events (just data, no event name) to match TS version.
     * The WebUI uses onmessage which only catches unnamed events.
     * The eventName parameter is for documentation/data-routing only — the actual
     * event type is carried in the "type" field inside the data payload.
     *
     * @param data the event data payload (must contain a "type" field for client routing)
     * @param eventName the event type for documentation (e.g. "new_prompt", "new_summary").
     *                  NOT used as the SSE event name — data["type"] is the routing key.
     */
    public void broadcast(Object data, String eventName) {
        // P1: Create snapshot to avoid concurrent modification during iteration
        List<SseEmitter> snapshot = new java.util.ArrayList<>(emitters);
        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();

        for (SseEmitter emitter : snapshot) {
            try {
                // Send unnamed event — just data, no event name
                // This matches TS/WebUI behavior where onmessage catches unnamed events
                emitter.send(SseEmitter.event()
                    .data(data));
            } catch (IOException e) {
                deadEmitters.add(emitter);
                log.debug("Dead SSE connection detected");
            } catch (IllegalStateException e) {
                // P2: send() throws IllegalStateException on an emitter that completed
                // (timeout/error callback) between the snapshot and this send. Treat it
                // like a dead connection instead of letting it abort the broadcast loop
                // and skip the remaining clients.
                deadEmitters.add(emitter);
                log.debug("SSE connection already completed, removing");
            }
        }

        // Remove dead emitters after iteration completes
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.debug("Removed {} dead SSE connections", deadEmitters.size());
        }
    }

    /**
     * Get the count of currently connected clients.
     */
    public int getClientCount() {
        return emitters.size();
    }
}
