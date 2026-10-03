package com.ablueforce.cortexce.mcp;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Covers the two MCP tool parameters that the tool layer is responsible for
 * bounding: the {@code orderBy} whitelist on {@code get_observations} and the
 * {@code limit} clamp on {@code recent}.
 *
 * <p>Both are places where an uncapped or case-mismatched value used to pass
 * straight through, and both fail quietly — an ignored {@code orderBy} returns
 * an unordered list that looks complete, and an unbounded {@code limit} hands
 * the caller whatever the project happens to contain.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaudeMemMcpToolsTest {

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private SummaryRepository summaryRepository;

    @Mock
    private SessionRepository sessionRepository;

    private ClaudeMemMcpTools tools;

    @BeforeEach
    void setUp() {
        // Only the repositories these two tools touch are wired; the rest of the
        // constructor is unused by the paths under test.
        tools = new ClaudeMemMcpTools(
                null, null, observationRepository, null, summaryRepository, sessionRepository);
    }

    private static ObservationEntity obs(long epoch) {
        ObservationEntity o = new ObservationEntity();
        o.setId(UUID.randomUUID());
        o.setProjectPath("/p");
        o.setType("bugfix");
        o.setCreatedAtEpoch(epoch);
        return o;
    }

    @Nested
    @DisplayName("get_observations orderBy whitelist")
    class OrderBy {

        @Test
        @DisplayName("accepts the two supported spellings regardless of case")
        void acceptsEitherCase() {
            ObservationEntity older = obs(100L);
            ObservationEntity newer = obs(200L);
            // Repository order is deliberately the opposite of creation order.
            when(observationRepository.findAllById(any())).thenReturn(List.of(older, newer));

            for (String spelling : List.of("created_at_epoch", "createdAtEpoch",
                    "CREATED_AT_EPOCH", "Created_At_Epoch", "CREATEDATEPOCH")) {
                Map<String, Object> result = tools.getObservations(
                        List.of(older.getId().toString(), newer.getId().toString()),
                        null, spelling, null);

                @SuppressWarnings("unchecked")
                List<ObservationEntity> returned = (List<ObservationEntity>) result.get("observations");
                assertThat(returned)
                        .as("orderBy=%s should order newest first", spelling)
                        .extracting(ObservationEntity::getCreatedAtEpoch)
                        .containsExactly(200L, 100L);
            }
        }

        @Test
        @DisplayName("an unsupported value leaves the list untouched rather than half-sorting it")
        void unsupportedValueIsIgnored() {
            ObservationEntity first = obs(100L);
            ObservationEntity second = obs(200L);
            when(observationRepository.findAllById(any())).thenReturn(List.of(first, second));

            Map<String, Object> result = tools.getObservations(
                    List.of(first.getId().toString(), second.getId().toString()),
                    null, "quality_score", null);

            @SuppressWarnings("unchecked")
            List<ObservationEntity> returned = (List<ObservationEntity>) result.get("observations");
            assertThat(returned)
                    .as("repository order must be preserved when orderBy is not recognised")
                    .extracting(ObservationEntity::getCreatedAtEpoch)
                    .containsExactly(100L, 200L);
        }
    }

    @Nested
    @DisplayName("recent limit clamp")
    class Limit {

        @Test
        @DisplayName("clamps a caller-supplied limit to MAX_PAGE_SIZE")
        void clampsHugeLimit() {
            when(summaryRepository.findByProjectLimited(anyString(), any(Integer.class)))
                    .thenReturn(List.of(new SummaryEntity()));

            tools.recent("/p", 1_000_000);

            ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
            org.mockito.Mockito.verify(summaryRepository)
                    .findByProjectLimited(org.mockito.ArgumentMatchers.eq("/p"), limit.capture());
            assertThat(limit.getValue())
                    .as("findByProjectLimited passes :limit straight into SQL with no cap of its own")
                    .isEqualTo(Constants.MAX_PAGE_SIZE);
        }

        @Test
        @DisplayName("keeps the documented default of 3 when no limit is given")
        void defaultsToThree() {
            when(summaryRepository.findByProjectLimited(anyString(), any(Integer.class)))
                    .thenReturn(List.of(new SummaryEntity()));

            tools.recent("/p", null);

            ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
            org.mockito.Mockito.verify(summaryRepository)
                    .findByProjectLimited(org.mockito.ArgumentMatchers.eq("/p"), limit.capture());
            assertThat(limit.getValue()).isEqualTo(3);
        }
    }
}
