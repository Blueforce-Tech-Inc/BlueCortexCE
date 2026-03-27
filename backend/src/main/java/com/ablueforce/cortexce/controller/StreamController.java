package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.service.AgentService;
import com.ablueforce.cortexce.service.SSEBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Tag(name = "Stream", description = "Server-Sent Events (SSE) streaming endpoint for real-time event push to the Viewer WebUI")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    // P3: SSE timeout configurable via application properties (default: 30 minutes)
    @Value("${claudemem.sse.timeout-ms:1800000}")
    private long sseTimeout;

    private final SSEBroadcaster sseBroadcaster;
    private final SessionRepository sessionRepository;
    private final AgentService agentService;

    public StreamController(SSEBroadcaster sseBroadcaster,
                            SessionRepository sessionRepository,
                            AgentService agentService) {
        this.sseBroadcaster = sseBroadcaster;
        this.sessionRepository = sessionRepository;
        this.agentService = agentService;
    }

    /**
     * GET /stream — SSE endpoint.
     * Web UI expects type field and camelCase field names.
     */
    @GetMapping
    @Operation(summary = "SSE stream for real-time events",
        description = "Establishes a Server-Sent Events (SSE) connection for real-time event push to the Viewer WebUI. Sends an initial_load event with project list and processing_status event. Configurable timeout via claudemem.sse.timeout-ms (default: 30 minutes). Events include: new_observation, new_prompt, new_summary.")
    @ApiResponse(responseCode = "200", description = "SSE stream established. Returns SseEmitter which pushes events.",
        content = @Content(schema = @Schema(example = "event: data:{\"type\":\"initial_load\",\"projects\":[...],\"timestamp\":1709000000000}")))
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
