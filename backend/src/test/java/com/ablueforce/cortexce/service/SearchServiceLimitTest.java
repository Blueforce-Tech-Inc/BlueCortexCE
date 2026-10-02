package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SearchService} result capping.
 *
 * <p>The repository is asked for {@code limit * 2} rows to leave headroom for post-filtering, so the
 * service must trim the result back to {@code limit}. Skipping that trim returned twice the requested
 * page size on every search that carried no filter, offset, or ordering.
 */
class SearchServiceLimitTest {

    private ObservationRepository repository;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        repository = mock(ObservationRepository.class);
        searchService = new SearchService(repository);
    }

    /** Stub the recent-observations query with exactly {@code rows} distinct observations. */
    private void stubRecent(int rows) {
        List<ObservationEntity> data = new ArrayList<>(Math.max(rows, 0));
        for (int i = 0; i < rows; i++) {
            ObservationEntity obs = new ObservationEntity();
            obs.setId(UUID.randomUUID());
            obs.setProjectPath("/tmp");
            data.add(obs);
        }
        when(repository.findByProjectLimitedWithOffset(anyString(), anyInt(), anyInt()))
                .thenReturn(data);
    }

    private static SearchService.SearchRequest request(int limit) {
        return request(limit, 0);
    }

    private static SearchService.SearchRequest request(int limit, int offset) {
        return new SearchService.SearchRequest("/tmp", null, null, null, null, null, null, null,
                limit, offset, null);
    }

    private static List<ObservationEntity> rows(int count, String type, Long epochBase) {
        List<ObservationEntity> data = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            ObservationEntity obs = new ObservationEntity();
            obs.setId(UUID.randomUUID());
            obs.setType(type);
            if (epochBase != null) {
                obs.setCreatedAtEpoch(epochBase + i);
            }
            data.add(obs);
        }
        return data;
    }

    /** The SQL LIMIT the service asked the repository for. */
    private int capturedSqlLimit() {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(repository).findByProjectLimitedWithOffset(anyString(), captor.capture(), anyInt());
        return captor.getValue();
    }

    // ===== unfiltered search: result must be trimmed back to limit =====

    @Test
    @DisplayName("unfiltered search returns exactly limit, not the limit*2 over-fetch")
    void unfilteredSearch_trimsToRequestedLimit() {
        stubRecent(40);

        SearchService.SearchResult result = searchService.search(request(20));

        assertThat(result.observations()).hasSize(20);
        assertThat(result.strategy()).isEqualTo("recent");
        assertThat(result.fellBack()).isFalse();
    }

    @Test
    @DisplayName("limit=1 still returns exactly 1 from a 2-row over-fetch")
    void limitOfOne_returnsOne() {
        stubRecent(2);

        assertThat(searchService.search(request(1)).observations()).hasSize(1);
    }

    @Test
    @DisplayName("small limits are trimmed too, not just page-sized ones")
    void smallLimits_areTrimmed() {
        for (int limit : new int[]{2, 3, 5, 17}) {
            setUp();
            stubRecent(limit * 2);
            assertThat(searchService.search(request(limit)).observations())
                    .as("limit=%d", limit)
                    .hasSize(limit);
        }
    }

    @Test
    @DisplayName("the repository over-fetch still asks for limit*2")
    void repositoryOverFetch_isPreserved() {
        stubRecent(40);

        searchService.search(request(20));

        assertThat(capturedSqlLimit()).isEqualTo(40);
    }

    // ===== MAX_PAGE_SIZE clamp =====

    @Test
    @DisplayName("limit above MAX_PAGE_SIZE is clamped, so the SQL LIMIT stays positive and bounded")
    void oversizedLimit_isClampedToMaxPageSize() {
        stubRecent(200);

        SearchService.SearchResult result = searchService.search(request(10_000));

        assertThat(capturedSqlLimit()).isEqualTo(Constants.MAX_PAGE_SIZE * 2);
        assertThat(result.observations()).hasSize(Constants.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("2^30 no longer overflows — the old code sent a negative SQL LIMIT")
    void twoToTheThirty_doesNotProduceNegativeSqlLimit() {
        stubRecent(200);

        SearchService.SearchResult result = searchService.search(request(1 << 30));

        assertThat(capturedSqlLimit())
                .isPositive()
                .isEqualTo(Constants.MAX_PAGE_SIZE * 2);
        assertThat(result.observations()).isNotEmpty();
        assertThat(result.strategy()).isEqualTo("recent");
        assertThat(result.fellBack()).isFalse();
    }

    @Test
    @DisplayName("Integer.MAX_VALUE cannot overflow limit*2 into a negative value")
    void intMaxValue_doesNotOverflow() {
        stubRecent(200);

        searchService.search(request(Integer.MAX_VALUE));

        assertThat(capturedSqlLimit())
                .isPositive()
                .isEqualTo(Constants.MAX_PAGE_SIZE * 2);
    }

    @Test
    @DisplayName("limit=MAX_PAGE_SIZE returns at most MAX_PAGE_SIZE rows")
    void maxPageSize_isHonoured() {
        stubRecent(200);

        assertThat(searchService.search(request(Constants.MAX_PAGE_SIZE)).observations())
                .hasSize(Constants.MAX_PAGE_SIZE);
    }

    // ===== non-positive limits fall back to the default =====

    @Test
    @DisplayName("limit=0 and negative limits fall back to the default page size")
    void nonPositiveLimits_fallBackToDefault() {
        for (int limit : new int[]{0, -1, -100, Integer.MIN_VALUE}) {
            setUp();
            stubRecent(SearchService.DEFAULT_LIMIT * 2);
            assertThat(searchService.search(request(limit)).observations())
                    .as("limit=%d", limit)
                    .hasSize(SearchService.DEFAULT_LIMIT);
        }
    }

    @Test
    @DisplayName("a null request is rejected rather than silently defaulting")
    void nullRequest_isRejected() {
        assertThatThrownBy(() -> searchService.search(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== filtered / offset / ordered paths keep their existing behaviour =====

    @Test
    @DisplayName("a type filter still narrows the result set to limit")
    void typeFilter_stillCapsAtLimit() {
        when(repository.findByAllFiltersWithOffset(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(rows(40, "bugfix", null));

        SearchService.SearchRequest req = new SearchService.SearchRequest(
                "/tmp", null, null, "bugfix", null, null, null, null, 20, 0, null);

        SearchService.SearchResult result = searchService.search(req);

        assertThat(result.strategy()).isEqualTo("filter");
        assertThat(result.observations()).hasSize(20);
    }

    @Test
    @DisplayName("offset is applied before the cap")
    void offset_isAppliedThenCapped() {
        stubRecent(40);

        // 40 rows fetched, skip 5, cap at 20 -> 20 rows remain
        assertThat(searchService.search(request(20, 5)).observations()).hasSize(20);
    }

    @Test
    @DisplayName("an offset past the fetched window yields no rows")
    void offsetBeyondWindow_returnsEmpty() {
        stubRecent(40);

        assertThat(searchService.search(request(20, 100)).observations()).isEmpty();
    }

    @Test
    @DisplayName("ordering is applied, then the result is trimmed to limit")
    void orderBy_isAppliedThenTrimmed() {
        stubRecent(40);
        when(repository.findByProjectLimitedWithOffset(anyString(), anyInt(), anyInt()))
                .thenReturn(rows(40, null, 0L));

        SearchService.SearchRequest req = new SearchService.SearchRequest(
                "/tmp", null, null, null, null, null, null, null, 5, 0, "createdAtEpoch");

        SearchService.SearchResult result = searchService.search(req);

        assertThat(result.observations()).hasSize(5);
        assertThat(result.observations().get(0).getCreatedAtEpoch()).isEqualTo(39L);
    }

    // ===== full-text path (query present, no usable vector) =====

    @Test
    @DisplayName("full-text fallback results are trimmed to limit as well")
    void fullTextPath_trimsToLimit() {
        when(repository.fullTextSearch(anyString(), anyString(), anyInt()))
                .thenReturn(rows(40, null, null));

        SearchService.SearchRequest req = new SearchService.SearchRequest(
                "/tmp", "auth bug", null, null, null, null, null, null, 20, 0, null);

        assertThat(searchService.search(req).observations()).hasSize(20);
    }

    @Test
    @DisplayName("an empty query vector is rejected and the trimmed full-text result is used")
    void invalidQueryVector_fallsThroughToTrimmedResult() {
        when(repository.fullTextSearch(anyString(), anyString(), anyInt()))
                .thenReturn(rows(40, null, null));

        // A zero-length vector serialises to "[]", which VectorValidator rejects, so hybrid is skipped.
        SearchService.SearchRequest req = new SearchService.SearchRequest(
                "/tmp", "auth bug", new float[0], null, null, null, null, null, 20, 0, null);

        SearchService.SearchResult result = searchService.search(req);

        assertThat(result.strategy()).isEqualTo("tsvector");
        assertThat(result.observations()).hasSize(20);
    }

    @Test
    @DisplayName("the hybrid path is trimmed to limit as well")
    void hybridPath_trimsToLimit() {
        when(repository.hybridSearch(anyString(), anyString(), anyString(), anyLong(), anyInt()))
                .thenReturn(rows(40, null, null));

        SearchService.SearchRequest req = new SearchService.SearchRequest(
                "/tmp", "auth bug", new float[]{0.5f, 0.25f}, null, null, null, null, null, 20, 0, null);

        SearchService.SearchResult result = searchService.search(req);

        assertThat(result.strategy()).isEqualTo("hybrid");
        assertThat(result.fellBack()).isFalse();
        assertThat(result.observations()).hasSize(20);
    }

    @Test
    @DisplayName("the hybrid path also honours the MAX_PAGE_SIZE clamp")
    void hybridPath_isClamped() {
        when(repository.hybridSearch(anyString(), anyString(), anyString(), anyLong(), anyInt()))
                .thenReturn(rows(200, null, null));

        SearchService.SearchRequest req = new SearchService.SearchRequest(
                "/tmp", "auth bug", new float[]{0.5f, 0.25f}, null, null, null, null, null,
                1 << 30, 0, null);

        SearchService.SearchResult result = searchService.search(req);

        assertThat(result.observations()).hasSize(Constants.MAX_PAGE_SIZE);
    }

    // ===== short and empty repository results pass through unchanged =====

    @Test
    @DisplayName("a short repository result is not padded")
    void shortResult_isPassedThrough() {
        stubRecent(3);

        assertThat(searchService.search(request(20)).observations()).hasSize(3);
    }

    @Test
    @DisplayName("an empty repository result stays empty")
    void emptyResult_staysEmpty() {
        stubRecent(0);

        assertThat(searchService.search(request(20)).observations()).isEmpty();
    }

    @Test
    @DisplayName("a repository failure on the recent path propagates to the caller")
    void repositoryFailure_propagates() {
        when(repository.findByProjectLimitedWithOffset(anyString(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> searchService.search(request(20)))
                .isInstanceOf(IllegalStateException.class);
    }
}
