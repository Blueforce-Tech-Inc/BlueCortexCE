package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.service.AgentService;
import com.ablueforce.cortexce.service.SSEBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * SSE streaming controller for real-time event push to Viewer UI.
 */
@RestController
@RequestMapping("/stream")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    // P3: SSE timeout configurable via application properties (default: 30 minutes)
    @Value("${claudemem.sse.timeout-ms:1800000}")
    private long sseTimeout;

    @Autowired
    private SSEBroadcaster sseBroadcaster;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private AgentService agentService;

    /**
     * GET /stream — SSE endpoint.
     * Web UI expects type field and camelCase field names.
     */
    @GetMapping
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(sseTimeout);

        // Resource cleanup callbacks
        Runnable removeCallback = () -> sseBroadcaster.remove(emitter);
        emitter.onCompletion(removeCallback);
        emitter.onError(e -> {
            removeCallback.run();
            log.debug("SSE error", e);
        });
        emitter.onTimeout(removeCallback);

        // Register with broadcaster
        sseBroadcaster.add(emitter);

        // Send initial load event with type field
        // NOTE: Use unnamed events (just data, no event name) to match TS version
        // WebUI uses onmessage which only catches unnamed events
        try {
            emitter.send(SseEmitter.event()
                .data(Map.of(
                    "type", "initial_load",
                    "projects", sessionRepository.findAllProjects(),
                    "timestamp", System.currentTimeMillis()
                )));

            emitter.send(SseEmitter.event()
                .data(Map.of(
                    "type", "processing_status",
                    "isProcessing", agentService.isAnySessionProcessing(),
                    "queueDepth", agentService.getQueueDepth()
                )));

        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}
