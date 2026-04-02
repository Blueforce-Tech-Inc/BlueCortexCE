package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContextCacheService — cache freshness logic and refresh marking.
 * Uses Mockito for SessionRepository only (ContextService excluded due to classloader constraints).
 */
@ExtendWith(MockitoExtension.class)
class ContextCacheServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionEntity activeSession;

    @BeforeEach
    void setUp() {
        activeSession = new SessionEntity();
        activeSession.setContentSessionId("test-session");
        activeSession.setProjectPath("/tmp/test-project");
        activeSession.setStatus("active");
    }

    // ===== markForRefresh =====

    @Test
    void markForRefresh_setsNeedsRefreshOnActiveSessions() {
        when(sessionRepository.findByProjectPathAndStatus("/tmp/test", "active"))
            .thenReturn(List.of(activeSession));

        // Use constructor with null contextService (markForRefresh doesn't use it)
        ContextCacheService cacheService = new ContextCacheService(sessionRepository, null);
        cacheService.markForRefresh("/tmp/test");

        assertThat(activeSession.getNeedsContextRefresh()).isTrue();
        verify(sessionRepository).saveAll(any());
    }

    @Test
    void markForRefresh_noActiveSessions_noSave() {
        when(sessionRepository.findByProjectPathAndStatus("/tmp/test", "active"))
            .thenReturn(Collections.emptyList());

        ContextCacheService cacheService = new ContextCacheService(sessionRepository, null);
        cacheService.markForRefresh("/tmp/test");

        verify(sessionRepository, never()).saveAll(any());
    }

    // ===== getContextIfFresh =====

    @Test
    void getContextIfFresh_noSessions_returnsNull() {
        when(sessionRepository.findByProjectPathAndStatus("/tmp/test", "active"))
            .thenReturn(Collections.emptyList());

        ContextCacheService cacheService = new ContextCacheService(sessionRepository, null);
        assertThat(cacheService.getContextIfFresh("/tmp/test")).isNull();
    }

    @Test
    void getContextIfFresh_needsRefresh_returnsNull() {
        activeSession.setNeedsContextRefresh(true);
        when(sessionRepository.findByProjectPathAndStatus("/tmp/test", "active"))
            .thenReturn(List.of(activeSession));

        ContextCacheService cacheService = new ContextCacheService(sessionRepository, null);
        assertThat(cacheService.getContextIfFresh("/tmp/test")).isNull();
    }

    @Test
    void getContextIfFresh_staleCache_returnsNull() {
        activeSession.setNeedsContextRefresh(false);
        activeSession.setCachedContext("cached-context");
        // Set cache age to 10 minutes ago (stale — default refresh interval is 60s, 2x = 120s)
        activeSession.setContextRefreshedAtEpoch(Instant.now().minusSeconds(600).toEpochMilli());
        when(sessionRepository.findByProjectPathAndStatus("/tmp/test", "active"))
            .thenReturn(List.of(activeSession));

        ContextCacheService cacheService = new ContextCacheService(sessionRepository, null);
        assertThat(cacheService.getContextIfFresh("/tmp/test")).isNull();
    }

    @Test
    void getContextIfFresh_freshCache_returnsContext() {
        activeSession.setNeedsContextRefresh(false);
        activeSession.setCachedContext("fresh-context");
        // Set cache age to just now (fresh)
        activeSession.setContextRefreshedAtEpoch(Instant.now().toEpochMilli());
        when(sessionRepository.findByProjectPathAndStatus("/tmp/test", "active"))
            .thenReturn(List.of(activeSession));

        ContextCacheService cacheService = new ContextCacheService(sessionRepository, null);
        assertThat(cacheService.getContextIfFresh("/tmp/test")).isEqualTo("fresh-context");
    }

    @Test
    void getContextIfFresh_nullRefreshedAt_returnsNull() {
        activeSession.setNeedsContextRefresh(false);
        activeSession.setCachedContext("context");
        activeSession.setContextRefreshedAtEpoch(null);
        when(sessionRepository.findByProjectPathAndStatus("/tmp/test", "active"))
            .thenReturn(List.of(activeSession));

        ContextCacheService cacheService = new ContextCacheService(sessionRepository, null);
        assertThat(cacheService.getContextIfFresh("/tmp/test")).isNull();
    }

    // ===== refreshStaleContexts =====

    @Test
    void refreshStaleContexts_emptyList_noAction() {
        when(sessionRepository.findByNeedsContextRefreshTrue()).thenReturn(Collections.emptyList());

        ContextCacheService cacheService = new ContextCacheService(sessionRepository, null);
        cacheService.refreshStaleContexts();

        verify(sessionRepository, never()).save(any());
    }
}
