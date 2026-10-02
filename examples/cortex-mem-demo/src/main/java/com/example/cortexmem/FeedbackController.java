package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Demo controller for Feedback API.
 * Demonstrates observation feedback submission.
 *
 * <p>Cross-SDK parity: Go /feedback, Python /feedback, JS /feedback.
 */
@RestController
@RequestMapping("/demo/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    private final CortexMemClient client;

    public FeedbackController(CortexMemClient client) {
        this.client = client;
    }

    /**
     * POST /demo/feedback
     * Body: {"observationId": "...", "feedbackType": "SUCCESS", "comment": "..."}
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @RequestBody(required = false) Map<String, Object> body) {

        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "request body is required"));
        }

        Object observationIdObj = body.get("observationId");
        if (!(observationIdObj instanceof String observationId) || observationId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "observationId is required"));
        }

        Object feedbackTypeObj = body.get("feedbackType");
        if (!(feedbackTypeObj instanceof String feedbackType) || feedbackType.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "feedbackType is required"));
        }

        String comment = null;
        if (body.containsKey("comment")) {
            Object commentObj = body.get("comment");
            if (commentObj instanceof String s) {
                comment = s.isBlank() ? null : s;
            }
        }

        try {
            client.submitFeedback(observationId, feedbackType, comment);
            return ResponseEntity.ok(Map.of("status", "submitted"));
        } catch (Exception e) {
            // The backend answers 404 for an unknown observationId. The SDK wraps
            // that in a generic RuntimeException, so the status and the real reason
            // are only reachable by walking the cause chain — without that, a
            // missing observation surfaced here as 500 "submitFeedback failed",
            // which both misreports the status and throws away the explanation.
            if (DemoErrors.isNotFound(e)) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", DemoErrors.messageOf(e)));
            }
            log.error("Submit feedback failed for observationId={}", observationId, e);
            String reason = DemoErrors.messageOf(e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Submit feedback failed: "
                            + (reason != null ? reason : e.getMessage())));
        }
    }

    /**
     * GET /demo/feedback/health
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("endpoint", "feedback", "status", "ok"));
    }
}
