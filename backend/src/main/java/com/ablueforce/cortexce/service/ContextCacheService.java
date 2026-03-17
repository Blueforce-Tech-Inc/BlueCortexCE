package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * P2-1: Context caching service.
 * <p>
 * Manages cached context for active sessions:
 * - Marks sessions for refresh when observations are ingested
 * - Periodically refreshes cached context for active sessions
 * - Returns cached context for session-start (fast path)
 */
@Service
public class ContextCacheService {

    private static final Logger log = LoggerFactory.getLogger(ContextCacheService.class);

    private final SessionRepository sessionRepository;
    private final ContextService contextService;

    @Value("${claudemem.cache.refresh-interval-seconds:60}")
    private int refreshIntervalSeconds;

    public ContextCacheService(SessionRepository sessionRepository, ContextService contextService) {
        this.sessionRepository = sessionRepository;
        this.contextService = contextService;
    }

    /**
     * Mark a session's context for refresh (called when new observation is ingested).
     */
    @Transactional
    public void markForRefresh(String projectPath) {
        List<SessionEntity> activeSessions = sessionRepository.findByProjectPathAndStatus(projectPath, "active");
        for (SessionEntity session : activeSessions) {
            session.setNeedsContextRefresh(true);
        }
        if (!activeSessions.isEmpty()) {
            sessionRepository.saveAll(activeSessions);
            log.debug("Marked {} active sessions for refresh", activeSessions.size());
        }
    }

    /**
     * Get context for a project (returns cached if available and fresh).
     * Returns null if cache should be refreshed.
     */
    public String getContextIfFresh(String projectPath) {
        List<SessionEntity> sessions = sessionRepository.findByProjectPathAndStatus(projectPath, "active");
        if (sessions.isEmpty()) {
            return null;
        }

        SessionEntity session = sessions.get(0);
        if (Boolean.TRUE.equals(session.getNeedsContextRefresh())) {
            log.debug("Context refresh needed for project {}", projectPath);
            return null;
        }

        // Check if cache is still fresh
        long cacheAgeSeconds = session.getContextRefreshedAtEpoch() != null
            ? (Instant.now().toEpochMilli() - session.getContextRefreshedAtEpoch()) / 1000
            : Long.MAX_VALUE;

        if (cacheAgeSeconds > refreshIntervalSeconds * 2) {
            // P2: Use INFO level for cache miss - important for debugging
            log.info("Context cache expired for project {} (age: {}s)", projectPath, cacheAgeSeconds);
            return null;
        }

        return session.getCachedContext();
    }

    /**
     * Refresh cached context for a session.
     */
    @Transactional
    public void refreshContext(SessionEntity session) {
        long startTime = System.currentTimeMillis();
        String context = contextService.generateContext(session.getProjectPath());

        session.setCachedContext(context);
        session.setContextRefreshedAtEpoch(Instant.now().toEpochMilli());
        session.setNeedsContextRefresh(false);

        sessionRepository.save(session);

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Refreshed context for session {} in {}ms", session.getContentSessionId(), duration);
    }

    /**
     * Periodically refresh context for active sessions that need it.
     * Runs every refreshIntervalSeconds.
     */
    @Scheduled(fixedRateString = "${claudemem.cache.refresh-interval-seconds:60}000")
    @Transactional
    public void refreshStaleContexts() {
        List<SessionEntity> staleSessions = sessionRepository.findByNeedsContextRefreshTrue();

        if (staleSessions.isEmpty()) {
            log.trace("No sessions need context refresh");
            return;
        }

        log.info("Refreshing context for {} stale sessions", staleSessions.size());

        long startTime = System.currentTimeMillis();
        int refreshed = 0;
        int failed = 0;

        for (SessionEntity session : staleSessions) {
            try {
                String context = contextService.generateContext(session.getProjectPath());
                session.setCachedContext(context);
                session.setContextRefreshedAtEpoch(Instant.now().toEpochMilli());
                session.setNeedsContextRefresh(false);
                // P1: Save immediately to prevent inconsistent state on crash
                sessionRepository.save(session);
                refreshed++;
            } catch (Exception e) {
                log.error("Failed to refresh context for session {}", session.getContentSessionId(), e);
                failed++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Refreshed {} contexts in {}ms", refreshed, duration);
    }
}
