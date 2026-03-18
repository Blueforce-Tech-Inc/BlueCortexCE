package com.ablueforce.cortexce.ai.observation;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ObservationRequest;
import com.ablueforce.cortexce.client.dto.SessionEndRequest;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultObservationCaptureServiceTest {

    @Mock
    private CortexMemClient client;

    @InjectMocks
    private DefaultObservationCaptureService service;

    @Test
    void recordToolObservation_delegatesToClient() {
        var obs = ObservationRequest.builder()
            .sessionId("s1")
            .projectPath("/p")
            .toolName("Read")
            .build();
        service.recordToolObservation(obs);
        verify(client).recordObservation(obs);
    }

    @Test
    void recordToolObservation_onException_doesNotThrow() {
        doThrow(new RuntimeException("network error")).when(client).recordObservation(any());
        service.recordToolObservation(ObservationRequest.builder()
            .sessionId("s").projectPath("/p").toolName("X").build());
        // should not throw
    }

    @Test
    void recordSessionEnd_delegatesToClient() {
        var req = SessionEndRequest.builder().sessionId("s2").projectPath("/app").build();
        service.recordSessionEnd(req);
        verify(client).recordSessionEnd(req);
    }

    @Test
    void recordUserPrompt_delegatesToClient() {
        var req = UserPromptRequest.builder()
            .sessionId("s3")
            .projectPath("/x")
            .promptText("hello")
            .build();
        service.recordUserPrompt(req);
        verify(client).recordUserPrompt(req);
    }
}
