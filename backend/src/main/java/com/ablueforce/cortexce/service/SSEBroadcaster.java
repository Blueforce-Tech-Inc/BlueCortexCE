package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe SSE broadcaster for real-time event distribution.
 * <p>
 * Uses CopyOnWriteArrayList for safe concurrent iteration during broadcast.
 * Dead connections are automatically cleaned up on IOException.
 */
@Component
public class SSEBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SSEBroadcaster.class);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void add(SseEmitter emitter) {
        // P0: Enforce maximum connection limit to prevent DoS
        if (emitters.size() >= Constants.MAX_SSE_CONNECTIONS) {
            log.warn("Maximum SSE connections ({}) exceeded, rejecting new connection", Constants.MAX_SSE_CONNECTIONS);
            throw new IllegalStateException("Maximum SSE connections exceeded");
        }
        emitters.add(emitter);
        log.debug("SSE client connected. Total: {}", emitters.size());
    }

    public void remove(SseEmitter emitter) {
        emitters.remove(emitter);
        log.debug("SSE client disconnected. Total: {}", emitters.size());
    }

    /**
     * Broadcast an event to all connected SSE clients.
     * P1 FIX: Create snapshot copy before iteration to prevent ConcurrentModificationException
     * when remove() is called from onCompletion callback during broadcast.
     *
     * NOTE: Uses unnamed SSE events (just data, no event name) to match TS version.
     * The WebUI uses onmessage which only catches unnamed events.
     * The eventName parameter is ignored - type field in data is used for routing.
     */
    public void broadcast(Object data, String eventName) {
        // P1: Create snapshot to avoid concurrent modification during iteration
        List<SseEmitter> snapshot = new java.util.ArrayList<>(emitters);
        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();

        for (SseEmitter emitter : snapshot) {
            try {
                // Send unnamed event - just data, no event name
                // This matches TS version behavior for WebUI compatibility
                emitter.send(SseEmitter.event()
                    .data(data));
            } catch (IOException e) {
                deadEmitters.add(emitter);
                log.debug("Dead SSE connection detected");
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
