package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionManagementServiceIdentityTest {

    @Test
    void concurrentCreatorLoadsTheCanonicalRowAfterConflictDoNothing() {
        SessionRepository sessions = mock(SessionRepository.class);
        SessionManagementService service = new SessionManagementService(sessions);
        ExternalSessionRef ref = ExternalSessionRef.of("codex", "same-id");
        SessionEntity winner = new SessionEntity();
        winner.setId(UUID.randomUUID());
        winner.setPlatformSource("codex");
        winner.setContentSessionId("same-id");
        when(sessions.findByPlatformSourceAndContentSessionId("codex", "same-id"))
            .thenReturn(Optional.empty(), Optional.of(winner));
        when(sessions.insertIfIdentityAbsent(eq("same-id"), eq("/repo"), anyString(),
            any(OffsetDateTime.class), anyLong(), isNull(), isNull(), eq("active"), eq("codex")))
            .thenReturn(0);

        SessionEntity actual = service.getOrCreate(ref, "/repo", "prompt");

        assertThat(actual.getId()).isEqualTo(winner.getId());
        verify(sessions).insertIfIdentityAbsent(eq("same-id"), eq("/repo"), eq("prompt"),
            any(OffsetDateTime.class), anyLong(), isNull(), isNull(), eq("active"), eq("codex"));
        verify(sessions, never()).saveAndFlush(any());
    }
}
