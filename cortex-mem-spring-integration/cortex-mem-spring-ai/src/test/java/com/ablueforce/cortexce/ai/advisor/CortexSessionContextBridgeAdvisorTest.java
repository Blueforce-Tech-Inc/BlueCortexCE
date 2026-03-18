package com.ablueforce.cortexce.ai.advisor;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CortexSessionContextBridgeAdvisorTest {

    @Mock
    private CallAdvisorChain chain;

    @Mock
    private StreamAdvisorChain streamChain;

    private CortexSessionContextBridgeAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = CortexSessionContextBridgeAdvisor.builder()
            .projectPath("/test/project")
            .build();
    }

    @AfterEach
    void tearDown() {
        CortexSessionContext.end();
    }

    @Test
    void getName_returnsCortexSessionContextBridgeAdvisor() {
        assertThat(advisor.getName()).isEqualTo("CortexSessionContextBridgeAdvisor");
    }

    @Test
    void getOrder_returnsNegativeByDefault() {
        assertThat(advisor.getOrder()).isLessThan(0);
    }

    @Test
    void adviseCall_whenNoConversationId_passesThroughWithoutActivatingContext() {
        ChatClientRequest request = ChatClientRequest.builder()
            .prompt(new Prompt(List.of(new UserMessage("hello"))))
            .build();
        ChatClientResponse response = ChatClientResponse.builder().build();
        when(chain.nextCall(any())).thenReturn(response);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertThat(result).isSameAs(response);
        assertThat(CortexSessionContext.isActive()).isFalse();
        verify(chain).nextCall(request);
    }

    @Test
    void adviseCall_whenConversationIdSet_activatesContextAndReleasesAfter() {
        ChatClientRequest request = ChatClientRequest.builder()
            .prompt(new Prompt(List.of(new UserMessage("hello"))))
            .context(Map.of(ChatMemory.CONVERSATION_ID, "conv-123"))
            .build();
        ChatClientResponse response = ChatClientResponse.builder().build();
        when(chain.nextCall(any())).thenAnswer(inv -> {
            assertThat(CortexSessionContext.isActive()).isTrue();
            assertThat(CortexSessionContext.getSessionId()).isEqualTo("conv-123");
            assertThat(CortexSessionContext.getProjectPath()).isEqualTo("/test/project");
            return response;
        });

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertThat(result).isSameAs(response);
        assertThat(CortexSessionContext.isActive()).isFalse();
    }

    @Test
    void adviseCall_whenDefaultConversationId_passesThroughWithoutActivating() {
        ChatClientRequest request = ChatClientRequest.builder()
            .prompt(new Prompt(List.of(new UserMessage("hello"))))
            .context(Map.of(ChatMemory.CONVERSATION_ID, ChatMemory.DEFAULT_CONVERSATION_ID))
            .build();
        ChatClientResponse response = ChatClientResponse.builder().build();
        when(chain.nextCall(any())).thenReturn(response);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertThat(result).isSameAs(response);
        assertThat(CortexSessionContext.isActive()).isFalse();
    }

    @Test
    void adviseStream_whenConversationIdSet_activatesContextAndReleasesOnComplete() {
        ChatClientRequest request = ChatClientRequest.builder()
            .prompt(new Prompt(List.of(new UserMessage("hello"))))
            .context(Map.of(ChatMemory.CONVERSATION_ID, "conv-stream-456"))
            .build();
        ChatClientResponse response = ChatClientResponse.builder().build();
        when(streamChain.nextStream(any())).thenAnswer(inv -> {
            assertThat(CortexSessionContext.isActive()).isTrue();
            assertThat(CortexSessionContext.getSessionId()).isEqualTo("conv-stream-456");
            return Flux.just(response);
        });

        Flux<ChatClientResponse> flux = advisor.adviseStream(request, streamChain);
        ChatClientResponse result = flux.blockFirst();

        assertThat(result).isSameAs(response);
        assertThat(CortexSessionContext.isActive()).isFalse();
    }
}
