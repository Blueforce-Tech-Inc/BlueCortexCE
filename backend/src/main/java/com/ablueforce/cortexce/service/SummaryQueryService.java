package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.repository.SummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only projections of stored summaries for display endpoints.
 *
 * <p>The context controller only needs the four narrative fields to render
 * "recent session context"; handing it the JPA entity leaked persistence into the
 * web layer for a plain text view. The projection keeps the entity in the service.
 */
@Service
public class SummaryQueryService {

    private final SummaryRepository summaryRepository;

    public SummaryQueryService(SummaryRepository summaryRepository) {
        this.summaryRepository = summaryRepository;
    }

    public record RecentSummary(
        String request,
        String completed,
        String learned,
        String nextSteps) {
    }

    @Transactional(readOnly = true)
    public List<RecentSummary> findRecent(String project, int limit) {
        return summaryRepository.findByProjectLimited(project, limit).stream()
            .map(summary -> new RecentSummary(
                summary.getRequest(),
                summary.getCompleted(),
                summary.getLearned(),
                summary.getNextSteps()))
            .toList();
    }
}
