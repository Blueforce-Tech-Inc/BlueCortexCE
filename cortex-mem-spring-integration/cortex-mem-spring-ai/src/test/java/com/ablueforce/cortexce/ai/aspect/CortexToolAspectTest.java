package com.ablueforce.cortexce.ai.aspect;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.ai.observation.ObservationCaptureService;
import com.ablueforce.cortexce.client.dto.ObservationRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Integration test for CortexToolAspect with a real @Tool-annotated bean.
 */
@SpringBootTest(classes = CortexToolAspectTest.TestConfig.class)
class CortexToolAspectTest {

    @org.springframework.beans.factory.annotation.Autowired
    private TestTools testTools;

    @org.springframework.beans.factory.annotation.Autowired
    private ObservationCaptureService captureService;

    @BeforeEach
    void setUp() {
        CortexSessionContext.end();
        org.mockito.Mockito.reset(captureService);
        CortexSessionContext.begin("test-session", "/test/project");
    }

    @AfterEach
    void tearDown() {
        CortexSessionContext.end();
    }

    @Test
    void whenContextActive_toolExecutionIsCaptured() {
        String result = testTools.readFile("path/to/file.txt");
        assertThat(result).isEqualTo("content");

        verify(captureService).recordToolObservation(argThat((ObservationRequest r) ->
            r.sessionId().equals("test-session") &&
            r.projectPath().equals("/test/project") &&
            r.toolName().equals("readFile") &&
            r.toolResponse().toString().contains("content") &&
            String.valueOf(r.toolInput()).contains("path/to/file.txt")
        ));
    }

    @Test
    void whenContextInactive_toolExecutesWithoutCapture() {
        CortexSessionContext.end();
        org.mockito.Mockito.reset(captureService);

        String result = testTools.readFile("x");
        assertThat(result).isEqualTo("content");
        verifyNoInteractions(captureService);
    }

    @Test
    void largeToolInputAndResponse_areTruncated() {
        String largePath = "x".repeat(5000);
        String result = testTools.readFile(largePath);
        // result is small "content", but the path (large) should be truncated
        verify(captureService).recordToolObservation(argThat((ObservationRequest r) -> {
            String inputStr = String.valueOf(r.toolInput());
            // Path truncated with suffix
            if (!inputStr.contains("[truncated]")) {
                return false;
            }
            // Should NOT contain full 5000 chars
            return inputStr.length() < 5100;
        }));
    }

    @Test
    void smallToolInputAndResponse_areNotTruncated() {
        String result = testTools.readFile("small-path");
        assertThat(result).isEqualTo("content");
        verify(captureService).recordToolObservation(argThat((ObservationRequest r) -> {
            String inputStr = String.valueOf(r.toolInput());
            // Should NOT contain truncation marker
            return !inputStr.contains("[truncated]");
        }));
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        ObservationCaptureService captureService() {
            return mock(ObservationCaptureService.class);
        }

        @Bean
        CortexToolAspect cortexToolAspect(ObservationCaptureService captureService) {
            return new CortexToolAspect(captureService);
        }

        @Bean
        TestTools testTools() {
            return new TestTools();
        }
    }

    static class TestTools {
        @Tool(description = "Read a file", name = "readFile")
        public String readFile(String path) {
            return "content";
        }
    }
}
