package com.ablueforce.cortexce.ai.observation;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ObservationRequest;
import com.ablueforce.cortexce.client.dto.SessionEndRequest;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation that delegates to {@link CortexMemClient}.
 * <p>
 * All operations are fire-and-forget: failures are logged but never thrown
 * to avoid disrupting the AI pipeline.
 */
public class DefaultObservationCaptureService implements ObservationCaptureService {

    private static final Logger log = LoggerFactory.getLogger(DefaultObservationCaptureService.class);

    private final CortexMemClient client;

    public DefaultObservationCaptureService(CortexMemClient client) {
        this.client = client;
    }

    @Override
    public void recordToolObservation(ObservationRequest observation) {
        try {
            client.recordObservation(observation);
            log.debug("Recorded tool observation: tool={}, session={}",
                observation.toolName(), observation.sessionId());
        } catch (Exception e) {
            log.warn("Failed to record tool observation: {}", e.getMessage());
        }
    }

    @Override
    public void recordSessionEnd(SessionEndRequest request) {
        try {
            client.recordSessionEnd(request);
            log.debug("Recorded session end: session={}", request.sessionId());
        } catch (Exception e) {
            log.warn("Failed to record session end: {}", e.getMessage());
        }
    }

    @Override
    public void recordUserPrompt(UserPromptRequest request) {
        try {
            client.recordUserPrompt(request);
            log.debug("Recorded user prompt: session={}", request.sessionId());
        } catch (Exception e) {
            log.warn("Failed to record user prompt: {}", e.getMessage());
        }
    }
}
