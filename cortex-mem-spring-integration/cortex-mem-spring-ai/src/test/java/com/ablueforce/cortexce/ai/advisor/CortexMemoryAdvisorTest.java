package com.ablueforce.cortexce.ai.advisor;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import com.ablueforce.cortexce.client.dto.ICLPromptResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CortexMemoryAdvisorTest {

    @Mock
    private CortexMemClient cortexClient;

    @Mock
    private CallAdvisorChain chain;

    private CortexMemoryAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = CortexMemoryAdvisor.builder(cortexClient)
            .projectPath("/my/project")
            .maxExperiences(4)
            .build();
    }

    @Test
    void getName_returnsCortexMemoryAdvisor() {
        assertThat(advisor.getName()).isEqualTo("CortexMemoryAdvisor");
    }

    @Test
    void getOrder_returnsConfiguredOrder() {
        var a = CortexMemoryAdvisor.builder(cortexClient).order(42).build();
        assertThat(a.getOrder()).isEqualTo(42);
    }

    @Test
    void adviseCall_whenClientReturnsIclPrompt_injectsIntoRequest() {
        when(cortexClient.buildICLPrompt(any())).thenReturn(
            new ICLPromptResult("Relevant historical experiences:\n\n### 1\n**Task**: past", "2"));
        ChatClientResponse mockResponse = mock(ChatClientResponse.class);
        when(chain.nextCall(any())).thenReturn(mockResponse);

        var prompt = new Prompt(List.of(new UserMessage("How do I fix the bug?")));
        var request = ChatClientRequest.builder().prompt(prompt).build();

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertThat(result).isEqualTo(mockResponse);
        ArgumentCaptor<ChatClientRequest> cap = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(cap.capture());
        ChatClientRequest enriched = cap.getValue();
        assertThat(enriched.prompt().getInstructions()).hasSize(2);
        assertThat(enriched.prompt().getInstructions().getFirst().getText())
            .contains("Relevant historical experiences");
    }

    @Test
    void adviseCall_whenClientReturnsEmpty_doesNotEnrich() {
        when(cortexClient.buildICLPrompt(any())).thenReturn(new ICLPromptResult("", "0"));
        ChatClientResponse mockResponse = mock(ChatClientResponse.class);
        when(chain.nextCall(any())).thenReturn(mockResponse);

        var prompt = new Prompt(List.of(new UserMessage("fix bug")));
        var request = ChatClientRequest.builder().prompt(prompt).build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> cap = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(cap.capture());
        assertThat(cap.getValue().prompt().getInstructions()).hasSize(1);
    }

    @Test
    void adviseCall_whenUserTextBlank_doesNotCallClient() {
        var prompt = new Prompt(List.of());
        var request = ChatClientRequest.builder().prompt(prompt).build();
        ChatClientResponse mockResponse = mock(ChatClientResponse.class);
        when(chain.nextCall(any())).thenReturn(mockResponse);

        advisor.adviseCall(request, chain);

        verifyNoInteractions(cortexClient);
        verify(chain).nextCall(request);
    }

    @Test
    void adviseCall_onClientException_returnsOriginalRequest() {
        when(cortexClient.buildICLPrompt(any())).thenThrow(new RuntimeException("network error"));
        ChatClientResponse mockResponse = mock(ChatClientResponse.class);
        when(chain.nextCall(any())).thenReturn(mockResponse);

        var prompt = new Prompt(List.of(new UserMessage("fix bug")));
        var request = ChatClientRequest.builder().prompt(prompt).build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> cap = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(cap.capture());
        assertThat(cap.getValue().prompt().getInstructions()).hasSize(1);
    }
}
