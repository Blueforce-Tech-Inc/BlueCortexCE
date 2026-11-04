package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.repository.PendingMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the three shutdown outcomes the coordinator exists for: nothing in flight,
 * the work finishes inside the window, and the deadline passes with jobs still
 * running (those must go back to the queue).
 */
@ExtendWith(MockitoExtension.class)
class GracefulShutdownCoordinatorTest {

    @Mock private ProcessingHeartbeat processingHeartbeat;
    @Mock private PendingMessageRepository pendingMessageRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private GracefulShutdownCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new GracefulShutdownCoordinator(
            processingHeartbeat, pendingMessageRepository, transactionTemplate);
        ReflectionTestUtils.setField(coordinator, "inFlightTimeoutMs", 300L);
        ReflectionTestUtils.setField(coordinator, "pollIntervalMs", 25L);
        coordinator.start();
    }

    @Test
    void idleShutdownRequeuesNothing() {
        when(processingHeartbeat.inFlightIds()).thenReturn(Set.of());

        coordinator.stop();

        assertThat(coordinator.isShuttingDown()).isTrue();
        verify(pendingMessageRepository, never()).requeueProcessing(any());
    }

    @Test
    void jobsThatFinishInsideTheWindowAreNotRequeued() {
        UUID jobId = UUID.randomUUID();
        AtomicInteger polls = new AtomicInteger();
        when(processingHeartbeat.inFlightIds()).thenAnswer(invocation ->
            polls.incrementAndGet() <= 2 ? Set.of(jobId) : Set.of());

        coordinator.stop();

        verify(pendingMessageRepository, never()).requeueProcessing(any());
    }

    @Test
    void jobsStillRunningAtTheDeadlineAreReturnedToPending() {
        UUID jobId = UUID.randomUUID();
        when(processingHeartbeat.inFlightIds()).thenReturn(Set.of(jobId));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Integer> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(pendingMessageRepository.requeueProcessing(any())).thenReturn(1);

        coordinator.stop();

        verify(pendingMessageRepository).requeueProcessing(any());
        verify(processingHeartbeat).stopAll();
    }

    @Test
    void shutdownFlagIsSetBeforeTheWaitSoNewClaimsStop() {
        UUID jobId = UUID.randomUUID();
        when(processingHeartbeat.inFlightIds()).thenReturn(Set.of(jobId));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Integer> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(pendingMessageRepository.requeueProcessing(any())).thenReturn(0);

        coordinator.stop();

        assertThat(coordinator.isShuttingDown()).isTrue();
    }

    @Test
    void stopBeforeStartDoesNothing() {
        GracefulShutdownCoordinator neverStarted = new GracefulShutdownCoordinator(
            processingHeartbeat, pendingMessageRepository, transactionTemplate);

        neverStarted.stop();

        assertThat(neverStarted.isShuttingDown()).isFalse();
        verify(pendingMessageRepository, never()).requeueProcessing(any());
    }
}
