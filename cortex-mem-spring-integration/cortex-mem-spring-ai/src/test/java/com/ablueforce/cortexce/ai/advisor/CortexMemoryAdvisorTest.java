package com.ablueforce.cortexce.ai.advisor;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ICLPromptResult;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    /** Creates a real ChatClientResponse (ChatClientResponse is a final record, cannot be mocked). */
    private static ChatClientResponse realResponse() {
        return ChatClientResponse.builder()
            .chatResponse(ChatResponse.builder().generations(Collections.emptyList()).build())
            .context(Collections.emptyMap())
            .build();
    }

    @BeforeEach
    void setUp() {
        advisor = CortexMemoryAdvisor.builder(cortexClient)
            .projectPath("/my/project")
            .maxExperiences(4)
            .captureEnabled(true)
            .build();
    }

    @AfterEach
    void tearDown() {
        CortexSessionContext.end();
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
        when(chain.nextCall(any())).thenReturn(realResponse());

        var prompt = new Prompt(List.of(new UserMessage("How do I fix the bug?")));
        var request = ChatClientRequest.builder().prompt(prompt).build();

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertThat(result).isNotNull();
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
        when(chain.nextCall(any())).thenReturn(realResponse());

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
        when(chain.nextCall(any())).thenReturn(realResponse());

        advisor.adviseCall(request, chain);

        verifyNoInteractions(cortexClient);
        verify(chain).nextCall(request);
    }

    @Test
    void adviseCall_onClientException_returnsOriginalRequest() {
        when(cortexClient.buildICLPrompt(any())).thenThrow(new RuntimeException("network error"));
        when(chain.nextCall(any())).thenReturn(realResponse());

        var prompt = new Prompt(List.of(new UserMessage("fix bug")));
        var request = ChatClientRequest.builder().prompt(prompt).build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> cap = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(cap.capture());
        assertThat(cap.getValue().prompt().getInstructions()).hasSize(1);
    }

    @Test
    void adviseCall_whenContextActiveAndCaptureEnabled_recordsUserPrompt() {
        CortexSessionContext.begin("sess-99", "/app/proj");
        CortexSessionContext.incrementAndGetPromptNumber();

        when(cortexClient.buildICLPrompt(any())).thenReturn(new ICLPromptResult("", "0"));
        when(chain.nextCall(any())).thenReturn(realResponse());

        var prompt = new Prompt(List.of(new UserMessage("How do I fix the login bug?")));
        var request = ChatClientRequest.builder().prompt(prompt).build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<UserPromptRequest> cap = ArgumentCaptor.forClass(UserPromptRequest.class);
        verify(cortexClient).recordUserPrompt(cap.capture());
        UserPromptRequest captured = cap.getValue();
        assertThat(captured.sessionId()).isEqualTo("sess-99");
        assertThat(captured.projectPath()).isEqualTo("/app/proj");
        assertThat(captured.promptText()).isEqualTo("How do I fix the login bug?");
        assertThat(captured.promptNumber()).isEqualTo(1);
    }

    @Test
    void adviseCall_whenContextInactive_doesNotRecordUserPrompt() {
        // No CortexSessionContext.begin() - context inactive
        when(cortexClient.buildICLPrompt(any())).thenReturn(new ICLPromptResult("", "0"));
        when(chain.nextCall(any())).thenReturn(realResponse());

        var prompt = new Prompt(List.of(new UserMessage("fix bug")));
        var request = ChatClientRequest.builder().prompt(prompt).build();

        advisor.adviseCall(request, chain);

        verify(cortexClient).buildICLPrompt(any());
        verify(cortexClient, never()).recordUserPrompt(any());
    }

    @Test
    void adviseCall_whenSpringAiConversationIdInContext_recordsUserPrompt() {
        when(cortexClient.buildICLPrompt(any())).thenReturn(new ICLPromptResult("", "0"));
        when(chain.nextCall(any())).thenReturn(realResponse());

        var prompt = new Prompt(List.of(new UserMessage("Hello from Spring AI conversation")));
        var request = ChatClientRequest.builder()
            .prompt(prompt)
            .context(Map.of(ChatMemory.CONVERSATION_ID, "spring-conv-99"))
            .build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<UserPromptRequest> cap = ArgumentCaptor.forClass(UserPromptRequest.class);
        verify(cortexClient).recordUserPrompt(cap.capture());
        UserPromptRequest captured = cap.getValue();
        assertThat(captured.sessionId()).isEqualTo("spring-conv-99");
        assertThat(captured.projectPath()).isEqualTo("/my/project");
        assertThat(captured.promptText()).isEqualTo("Hello from Spring AI conversation");
    }

    @Test
    void adviseCall_whenCaptureDisabled_doesNotRecordUserPrompt() {
        var noCaptureAdvisor = CortexMemoryAdvisor.builder(cortexClient)
            .projectPath("/x")
            .captureEnabled(false)
            .build();

        CortexSessionContext.begin("sess-1", "/x");

        when(cortexClient.buildICLPrompt(any())).thenReturn(new ICLPromptResult("", "0"));
        when(chain.nextCall(any())).thenReturn(realResponse());

        var prompt = new Prompt(List.of(new UserMessage("hello")));
        var request = ChatClientRequest.builder().prompt(prompt).build();

        noCaptureAdvisor.adviseCall(request, chain);

        verify(cortexClient, never()).recordUserPrompt(any());
    }
}
