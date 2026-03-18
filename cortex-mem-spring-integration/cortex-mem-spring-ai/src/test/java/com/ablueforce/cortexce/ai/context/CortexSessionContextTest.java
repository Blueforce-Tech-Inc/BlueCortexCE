package com.ablueforce.cortexce.ai.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CortexSessionContextTest {

    @AfterEach
    void tearDown() {
        CortexSessionContext.end();
    }

    @Test
    void whenNoContext_isActiveReturnsFalse() {
        assertThat(CortexSessionContext.isActive()).isFalse();
        assertThat(CortexSessionContext.getSessionId()).isEqualTo("unknown-session");
        assertThat(CortexSessionContext.getProjectPath()).isEmpty();
        assertThat(CortexSessionContext.getPromptNumber()).isZero();
    }

    @Test
    void begin_withProjectPath_generatesSessionId() {
        CortexSessionContext.begin("/my/project");
        assertThat(CortexSessionContext.isActive()).isTrue();
        assertThat(CortexSessionContext.getSessionId()).isNotBlank();
        assertThat(CortexSessionContext.getProjectPath()).isEqualTo("/my/project");
        assertThat(CortexSessionContext.getPromptNumber()).isZero();
    }

    @Test
    void begin_withSessionIdAndProjectPath() {
        CortexSessionContext.begin("sess-123", "/app");
        assertThat(CortexSessionContext.isActive()).isTrue();
        assertThat(CortexSessionContext.getSessionId()).isEqualTo("sess-123");
        assertThat(CortexSessionContext.getProjectPath()).isEqualTo("/app");
    }

    @Test
    void incrementAndGetPromptNumber() {
        CortexSessionContext.begin("s", "/p");
        assertThat(CortexSessionContext.incrementAndGetPromptNumber()).isEqualTo(1);
        assertThat(CortexSessionContext.incrementAndGetPromptNumber()).isEqualTo(2);
        assertThat(CortexSessionContext.getPromptNumber()).isEqualTo(2);
    }

    @Test
    void end_clearsContext() {
        CortexSessionContext.begin("s", "/p");
        assertThat(CortexSessionContext.isActive()).isTrue();
        CortexSessionContext.end();
        assertThat(CortexSessionContext.isActive()).isFalse();
        assertThat(CortexSessionContext.getSessionId()).isEqualTo("unknown-session");
    }
}
