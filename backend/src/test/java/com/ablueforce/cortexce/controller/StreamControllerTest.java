package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.exception.GlobalExceptionHandler;
import com.ablueforce.cortexce.service.AgentService;
import com.ablueforce.cortexce.service.ProjectCatalogService;
import com.ablueforce.cortexce.service.SSEBroadcaster;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * L1 — at the SSE connection cap {@code /stream} answers an immediate 503 in the standard
 * error envelope instead of a 500.
 *
 * <p>No Spring context: the three collaborators are stubs, so this costs no database
 * connections while still running the real exception handler.
 */
class StreamControllerTest {

    @Test
    void capReachedAnswers503InTheStandardEnvelope() throws Exception {
        SSEBroadcaster broadcaster = mock(SSEBroadcaster.class);
        doThrow(new IllegalStateException("Maximum SSE connections exceeded"))
            .when(broadcaster).add(any());
        org.mockito.Mockito.when(broadcaster.getMaxConnections()).thenReturn(200);

        StreamController controller = new StreamController(
            broadcaster, mock(ProjectCatalogService.class), mock(AgentService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/stream"))
            .andReturn();

        assertEquals(503, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":\"service_unavailable\""), body);
        assertTrue(body.contains("200"), body);
    }
}
