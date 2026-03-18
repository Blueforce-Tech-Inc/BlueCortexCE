package com.ablueforce.cortexce.ai.advisor;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ICLPromptResult;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test: Bridge + MemoryAdvisor chain. Verifies no conflict — when Bridge
 * activates CortexSessionContext, MemoryAdvisor correctly resolves projectPath from context.
 */
@ExtendWith(MockitoExtension.class)
class BridgeMemoryAdvisorIntegrationTest {

    @Mock
    private CortexMemClient cortexClient;

    @Mock
    private CallAdvisorChain terminalChain;

    @AfterEach
    void tearDown() {
        CortexSessionContext.end();
    }

    @Test
    void bridgeThenMemoryAdvisor_resolvesProjectPathFromContext() {
        CortexSessionContextBridgeAdvisor bridge = CortexSessionContextBridgeAdvisor.builder()
            .projectPath("/bridge-project")
            .build();
        CortexMemoryAdvisor memory = CortexMemoryAdvisor.builder(cortexClient)
            .projectPath("/fallback-project")
            .captureEnabled(true)
            .build();

        when(cortexClient.buildICLPrompt(any())).thenReturn(new ICLPromptResult("", "0"));
        ChatClientResponse mockResponse = ChatClientResponse.builder().build();
        when(terminalChain.nextCall(any())).thenReturn(mockResponse);

        ChatClientRequest request = ChatClientRequest.builder()
            .prompt(new Prompt(List.of(new UserMessage("test from bridge"))))
            .context(Map.of(ChatMemory.CONVERSATION_ID, "conv-bridge-1"))
            .build();

        CallAdvisorChain memoryChain = mock(CallAdvisorChain.class);
        when(memoryChain.nextCall(any())).thenAnswer(inv -> {
            ChatClientRequest req = inv.getArgument(0);
            return memory.adviseCall(req, terminalChain);
        });
        ChatClientResponse result = bridge.adviseCall(request, memoryChain);

        assertThat(result).isSameAs(mockResponse);
        assertThat(CortexSessionContext.isActive()).isFalse();

        ArgumentCaptor<UserPromptRequest> cap = ArgumentCaptor.forClass(UserPromptRequest.class);
        verify(cortexClient).recordUserPrompt(cap.capture());
        UserPromptRequest captured = cap.getValue();
        assertThat(captured.sessionId()).isEqualTo("conv-bridge-1");
        assertThat(captured.projectPath()).isEqualTo("/bridge-project");
    }
}
