package com.ablueforce.cortexce.config;

import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.service.ProviderCircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Operational metrics for the queue, the model edge and retrieval.
 *
 * <p>Before this binder the service exposed JSON snapshots only: an operator could
 * see the queue depth <em>now</em>, but could not keep history, compute a p95 or
 * alert on a trend. These meters make the same facts scrapeable while keeping the
 * label set deliberately small (provider, path, outcome, failure kind) so the
 * series count stays bounded.
 *
 * <p>Queue and backlog are gauges: they are read from the database at scrape time
 * instead of being maintained in memory, so a second instance and a restart both
 * report the truth of the shared queue. A database error yields NaN rather than a
 * failed scrape. The latency timers and counters are in-memory per instance, which
 * is the normal Prometheus model for request-scoped measurements.
 *
 * <p>Access from services is static and tolerant of a missing registry: the
 * {@code @DataJpaTest} slices that import services do not create a
 * {@link MeterRegistry}, and instrumentation must not turn those slices into
 * constructor-injection failures.
 */
@Component
public class CortexMetrics implements MeterBinder {

    static final String QUEUE_DEPTH = "cortexce.queue.depth";
    static final String QUEUE_STALE = "cortexce.queue.stale";
    static final String EMBEDDING_BACKLOG = "cortexce.embedding.backlog";
    static final String WORKER_TIMER = "cortexce.worker.processing";
    static final String LLM_CALLS = "cortexce.llm.calls";
    static final String LLM_RETRIES = "cortexce.llm.retries";
    static final String SEARCH_REQUESTS = "cortexce.search.requests";
    static final String SEARCH_TIMER = "cortexce.search.duration";
    static final String CIRCUIT_STATE = "cortexce.llm.circuit.state";

    /** Circuit states, one gauge each, so a scrape at idle names every state. */
    private static final List<String> CIRCUIT_STATES = List.of("closed", "half_open", "open");

    /** Failure kinds the LLM edge can report; pre-registered so a scrape at idle sees them. */
    private static final List<String> FAILURE_KINDS =
        List.of("AUTHENTICATION", "RATE_LIMIT", "TIMEOUT", "UNAVAILABLE", "INVALID_RESPONSE", "UNKNOWN");

    /** Retrieval paths this service actually has. A pure vector path does not exist. */
    private static final List<String> SEARCH_PATHS = List.of("hybrid", "lexical", "filter", "recent");

    private static volatile CortexMetrics active;

    private final PendingMessageRepository pendingMessageRepository;
    private final ObservationRepository observationRepository;
    private final ProviderCircuitBreaker circuitBreaker;
    private final String provider;

    @Value("${claudemem.stale-message.threshold-minutes:10}")
    private int staleThresholdMinutes;

    private volatile MeterRegistry registry;
    private final Map<String, Counter> llmFailureCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> llmRetryCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> searchRequestCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> searchTimers = new ConcurrentHashMap<>();
    private volatile Counter llmSuccessCounter;
    private volatile Timer workerTimer;

    public CortexMetrics(PendingMessageRepository pendingMessageRepository,
                         ObservationRepository observationRepository,
                         ProviderCircuitBreaker circuitBreaker,
                         @Value("${claudemem.llm.provider:openai}") String provider) {
        this.pendingMessageRepository = pendingMessageRepository;
        this.observationRepository = observationRepository;
        this.circuitBreaker = circuitBreaker;
        this.provider = provider == null || provider.isBlank() ? "unknown" : provider;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
        active = this;

        for (String status : List.of("pending", "processing", "failed")) {
            Gauge.builder(QUEUE_DEPTH, () -> count(() -> statusCount(status)))
                .tag("status", status)
                .description("Queue rows in this state, read from the shared database at scrape time")
                .strongReference(true)
                .register(registry);
        }
        Gauge.builder(QUEUE_STALE, () -> count(this::staleCount))
            .description("Observation rows left processing past the stale threshold")
            .strongReference(true)
            .register(registry);
        Gauge.builder(EMBEDDING_BACKLOG, () -> count(observationRepository::countMissingEmbeddings))
            .description("Observations still without a usable vector")
            .strongReference(true)
            .register(registry);
        for (String state : CIRCUIT_STATES) {
            Gauge.builder(CIRCUIT_STATE, () -> circuitState(state))
                .tag("state", state)
                .description("1 when the provider circuit is in this state; read from the live breaker")
                .strongReference(true)
                .register(registry);
        }

        workerTimer = Timer.builder(WORKER_TIMER)
            .tag("provider", provider)
            .description("End-to-end duration of one observation worker job")
            .publishPercentileHistogram()
            .register(registry);

        llmSuccessCounter = Counter.builder(LLM_CALLS)
            .tag("outcome", "success")
            .tag("failure_kind", "none")
            .description("LLM calls that produced a usable answer")
            .register(registry);
        for (String kind : FAILURE_KINDS) {
            llmFailureCounters.put(kind, llmFailureCounter(kind));
            llmRetryCounters.put(kind, llmRetryCounter(kind));
        }

        for (String path : SEARCH_PATHS) {
            searchRequestCounters.put(path, searchRequestCounter(path));
            searchTimers.put(path, searchTimer(path));
        }
    }

    // ------------------------------------------------------------------
    // Static entry points used by the services
    // ------------------------------------------------------------------

    /** One completed observation worker job. */
    public static void workerCompleted(long nanos) {
        Timer timer = active == null ? null : active.workerTimer;
        if (timer != null) {
            timer.record(nanos, TimeUnit.NANOSECONDS);
        }
    }

    /** One provider answer that the caller could use. */
    public static void llmSuccess() {
        Counter counter = active == null ? null : active.llmSuccessCounter;
        if (counter != null) {
            counter.increment();
        }
    }

    /** One provider call that produced no usable answer, classified by kind. */
    public static void llmFailure(String failureKind) {
        CortexMetrics instance = active;
        if (instance == null || instance.registry == null) {
            return;
        }
        String kind = failureKind == null || failureKind.isBlank() ? "UNKNOWN" : failureKind;
        Counter counter = instance.llmFailureCounters.computeIfAbsent(kind, instance::llmFailureCounter);
        counter.increment();
    }

    /** One provider attempt that was scheduled to be repeated after a failure. */
    public static void llmRetry(String failureKind) {
        CortexMetrics instance = active;
        if (instance == null || instance.registry == null) {
            return;
        }
        String kind = failureKind == null || failureKind.isBlank() ? "UNKNOWN" : failureKind;
        Counter counter = instance.llmRetryCounters.computeIfAbsent(kind, instance::llmRetryCounter);
        counter.increment();
    }

    /** One retrieval request and how long the chosen path took. */
    public static void searchCompleted(String path, long nanos) {
        CortexMetrics instance = active;
        if (instance == null || instance.registry == null) {
            return;
        }
        String name = path == null || path.isBlank() ? "unknown" : path;
        instance.searchRequestCounters
            .computeIfAbsent(name, instance::searchRequestCounter)
            .increment();
        instance.searchTimers
            .computeIfAbsent(name, instance::searchTimer)
            .record(nanos, TimeUnit.NANOSECONDS);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Counter llmFailureCounter(String kind) {
        return Counter.builder(LLM_CALLS)
            .tag("outcome", "failure")
            .tag("failure_kind", kind)
            .description("LLM calls that produced no usable answer, by classified kind")
            .register(registry);
    }

    private Counter llmRetryCounter(String kind) {
        return Counter.builder(LLM_RETRIES)
            .tag("failure_kind", kind)
            .description("Provider attempts repeated after a transient failure")
            .register(registry);
    }

    private Counter searchRequestCounter(String path) {
        return Counter.builder(SEARCH_REQUESTS)
            .tag("path", path)
            .description("Retrieval requests by resolved path")
            .register(registry);
    }

    private Timer searchTimer(String path) {
        return Timer.builder(SEARCH_TIMER)
            .tag("path", path)
            .description("Retrieval latency by resolved path")
            .publishPercentileHistogram()
            .register(registry);
    }

    private long statusCount(String status) {
        return pendingMessageRepository.countByStatus(status);
    }

    private long staleCount() {
        long threshold = Instant.now()
            .minusSeconds(Math.max(1, staleThresholdMinutes) * 60L)
            .toEpochMilli();
        return pendingMessageRepository.countStale(threshold);
    }

    /** 1.0 when the breaker is in the named state, 0.0 otherwise. */
    private double circuitState(String state) {
        ProviderCircuitBreaker.State current = circuitBreaker.state();
        String name = current == ProviderCircuitBreaker.State.HALF_OPEN
            ? "half_open" : current.name().toLowerCase(java.util.Locale.ROOT);
        return name.equals(state) ? 1.0 : 0.0;
    }

    private double count(Supplier<Long> supplier) {
        try {
            Long value = supplier.get();
            return value == null ? Double.NaN : value.doubleValue();
        } catch (RuntimeException e) {
            // A unreachable database must not break the whole scrape; NaN is an
            // honest "unknown" that keeps the remaining series readable.
            return Double.NaN;
        }
    }
}
