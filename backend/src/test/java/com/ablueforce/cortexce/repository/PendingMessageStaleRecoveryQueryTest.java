package com.ablueforce.cortexce.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class PendingMessageStaleRecoveryQueryTest {

    @Test
    void genericStaleRecoveryIsRestrictedToObservationJobs() throws Exception {
        Query update = PendingMessageRepository.class
            .getMethod("updateStaleMessages", String.class, String.class, long.class)
            .getAnnotation(Query.class);
        Query count = PendingMessageRepository.class
            .getMethod("countStale", long.class)
            .getAnnotation(Query.class);

        assertThat(update).isNotNull();
        assertThat(update.value()).contains("p.messageType = 'observation'");
        assertThat(PendingMessageRepository.class
            .getMethod("updateStaleMessages", String.class, String.class, long.class)
            .getAnnotation(Modifying.class)).isNotNull();
        assertThat(count.value()).contains("p.messageType = 'observation'");
    }
}
