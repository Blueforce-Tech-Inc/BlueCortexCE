package com.ablueforce.cortexce.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ablueforce.cortexce.dto.ApiRequests;
import com.ablueforce.cortexce.service.SessionManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionControllerFailureDisclosureTest {

    private static final String CANARY = "session-error-private-canary-71a4";

    private final Logger logger = (Logger) LoggerFactory.getLogger(SessionController.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private SessionManagementService sessionManagementService;
    private SessionController controller;

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        sessionManagementService = mock(SessionManagementService.class);
        controller = new SessionController(null, sessionManagementService, null, null, null, Optional.empty());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void startFailureReturnsStableErrorAndDoesNotLogExceptionMessage() {
        when(sessionManagementService.getOrCreate(any(), eq("/work"), isNull()))
            .thenThrow(new IllegalStateException(CANARY));

        ResponseEntity<Object> response = controller.startSession(new ApiRequests.SessionStartRequest(
            "session-1", "/work", null, null, null, null, null, "claude"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(Map.of("error", "Failed to initialize session"), response.getBody());
        assertFalse(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(CANARY)));
    }

    @Test
    void lookupFailureReturnsStableErrorAndDoesNotLogExceptionMessage() {
        when(sessionManagementService.findLegacyClaude("session-1"))
            .thenThrow(new IllegalStateException(CANARY));

        ResponseEntity<Object> response = controller.getSession("session-1");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(Map.of("error", "Failed to get session"), response.getBody());
        assertFalse(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(CANARY)));
    }
}
