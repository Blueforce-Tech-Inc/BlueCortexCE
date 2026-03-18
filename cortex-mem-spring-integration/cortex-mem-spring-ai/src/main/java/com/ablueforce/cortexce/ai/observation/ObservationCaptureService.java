package com.ablueforce.cortexce.ai.observation;

import com.ablueforce.cortexce.client.dto.ObservationRequest;
import com.ablueforce.cortexce.client.dto.SessionEndRequest;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;

/**
 * Memory capture service — records agent actions into the Cortex CE memory system.
 */
public interface ObservationCaptureService {

    /**
     * Record a tool execution observation.
     */
    void recordToolObservation(ObservationRequest observation);

    /**
     * Signal that a session has ended.
     */
    void recordSessionEnd(SessionEndRequest request);

    /**
     * Record a user prompt.
     */
    void recordUserPrompt(UserPromptRequest request);
}
