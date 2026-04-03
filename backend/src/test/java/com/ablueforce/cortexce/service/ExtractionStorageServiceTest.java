package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.ExtractionConfig.TemplateConfig;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExtractionStorageService.
 * Covers: session find-or-create, observation storage, DLQ handling.
 * Uses Mockito for all repository interactions.
 */
@ExtendWith(MockitoExtension.class)
class ExtractionStorageServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private ObservationRepository observationRepository;

    private ExtractionStorageService storageService;

    private SessionEntity existingSession;

    @BeforeEach
    void setUp() {
        storageService = new ExtractionStorageService(sessionRepository, observationRepository);

        existingSession = new SessionEntity();
        existingSession.setId(UUID.randomUUID());
        existingSession.setContentSessionId("test-session-123");
        existingSession.setProjectPath("/tmp/test-project");
        existingSession.setStatus("active");
        existingSession.setStartedAtEpoch(System.currentTimeMillis());
    }

    // ===== storeExtractionResult =====

    @Test
    void storeExtractionResult_reusesExistingSession() {
        // Given: session already exists
        when(sessionRepository.findByContentSessionId("test-session-123"))
            .thenReturn(Optional.of(existingSession));

        TemplateConfig template = makeTemplate("user_preferences");

        ObservationEntity srcObs = new ObservationEntity();
        srcObs.setId(UUID.randomUUID());

        Map<String, Object> result = Map.of("likes", List.of("coffee", "tea"));

        // When
        storageService.storeExtractionResult(template, result, List.of(srcObs), "test-session-123", "/tmp/test-project");

        // Then: session should NOT be created
        verify(sessionRepository, never()).save(any(SessionEntity.class));
        // And: observation should be saved
        ArgumentCaptor<ObservationEntity> obsCaptor = ArgumentCaptor.forClass(ObservationEntity.class);
        verify(observationRepository).save(obsCaptor.capture());

        ObservationEntity saved = obsCaptor.getValue();
        assertThat(saved.getContentSessionId()).isEqualTo("test-session-123");
        assertThat(saved.getType()).isEqualTo("extracted_user_preferences");
        assertThat(saved.getExtractedData()).isEqualTo(result);
        assertThat(saved.getRefinedFromIds()).contains(srcObs.getId().toString());
    }

    @Test
    void storeExtractionResult_createsNewSessionIfNotFound() {
        // Given: session does NOT exist
        when(sessionRepository.findByContentSessionId("new-session"))
            .thenReturn(Optional.empty());
        when(sessionRepository.save(any(SessionEntity.class)))
            .thenAnswer(inv -> {
                SessionEntity s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });

        TemplateConfig template = makeTemplate("allergies");

        ObservationEntity srcObs = new ObservationEntity();
        srcObs.setId(UUID.randomUUID());

        Map<String, Object> result = Map.of("allergens", List.of("peanuts"));

        // When
        storageService.storeExtractionResult(template, result, List.of(srcObs), "new-session", "/tmp/test-project");

        // Then: session should be created and saved
        verify(sessionRepository, times(1)).save(any(SessionEntity.class));
        verify(observationRepository).save(any(ObservationEntity.class));
    }

    @Test
    void storeExtractionResult_skipsWhenResultIsEmpty() {
        // Given: empty result
        TemplateConfig template = makeTemplate("test_template");

        // When
        storageService.storeExtractionResult(template, Map.of(), List.of(), "session-id", "/tmp/test");

        // Then: no saves
        verifyNoInteractions(sessionRepository);
        verifyNoInteractions(observationRepository);
    }

    @Test
    void storeExtractionResult_skipsWhenResultIsNull() {
        // Given: null result
        TemplateConfig template = makeTemplate("test_template");

        // When
        storageService.storeExtractionResult(template, null, List.of(), "session-id", "/tmp/test");

        // Then: no saves
        verifyNoInteractions(sessionRepository);
        verifyNoInteractions(observationRepository);
    }

    @Test
    void storeExtractionResult_correctObservationFields() {
        // Given
        when(sessionRepository.findByContentSessionId("session-x"))
            .thenReturn(Optional.of(existingSession));
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TemplateConfig template = makeTemplate("dietary");
        ObservationEntity srcObs = new ObservationEntity();
        srcObs.setId(UUID.randomUUID());

        Map<String, Object> result = Map.of("prefers", "vegetarian");

        // When
        storageService.storeExtractionResult(template, result, List.of(srcObs), "session-x", "/tmp/test-project");

        // Then
        ArgumentCaptor<ObservationEntity> captor = ArgumentCaptor.forClass(ObservationEntity.class);
        verify(observationRepository).save(captor.capture());

        ObservationEntity saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo("llm_extraction");
        assertThat(saved.getTitle()).isEqualTo("Extraction: dietary");
        assertThat(saved.getConcepts()).contains("extraction", "dietary");
        assertThat(saved.getPromptNumber()).isEqualTo(0);
        assertThat(saved.getProjectPath()).isEqualTo("/tmp/test-project");
    }

    // ===== storeDLQ =====

    @Test
    void storeDLQ_createsDlqSessionAndObservation() {
        // Given: no DLQ session exists
        when(sessionRepository.findByContentSessionId("dlq:extraction"))
            .thenReturn(Optional.empty());
        when(sessionRepository.save(any(SessionEntity.class)))
            .thenAnswer(inv -> {
                SessionEntity s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        storageService.storeDLQ("/tmp/test-project", "user_preferences", "LLM timeout");

        // Then: DLQ session and observation should be saved
        verify(sessionRepository, times(1)).save(any(SessionEntity.class));
        verify(observationRepository).save(any(ObservationEntity.class));
    }

    @Test
    void storeDLQ_reusesExistingDlqSession() {
        // Given: DLQ session already exists
        SessionEntity dlqSession = new SessionEntity();
        dlqSession.setId(UUID.randomUUID());
        dlqSession.setContentSessionId("dlq:extraction");

        when(sessionRepository.findByContentSessionId("dlq:extraction"))
            .thenReturn(Optional.of(dlqSession));
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        storageService.storeDLQ("/tmp/test", "allergies", "invalid schema");

        // Then: session should NOT be created
        verify(sessionRepository, never()).save(any(SessionEntity.class));
        verify(observationRepository).save(any(ObservationEntity.class));
    }

    @Test
    void storeDLQ_doesNotPropagateException() {
        // Given: DLQ observation save fails (session save succeeds)
        SessionEntity dlqSession = new SessionEntity();
        dlqSession.setId(UUID.randomUUID());
        dlqSession.setContentSessionId("dlq:extraction");

        when(sessionRepository.findByContentSessionId("dlq:extraction"))
            .thenReturn(Optional.of(dlqSession));
        when(observationRepository.save(any(ObservationEntity.class)))
            .thenThrow(new RuntimeException("DB error"));

        // When/Then: should NOT throw — self-protected
        storageService.storeDLQ("/tmp/test", "test", "error"); // must not throw
    }

    @Test
    void storeDLQ_correctDlqObservationFields() {
        // Given
        SessionEntity dlqSession = new SessionEntity();
        dlqSession.setId(UUID.randomUUID());
        dlqSession.setContentSessionId("dlq:extraction");

        when(sessionRepository.findByContentSessionId("dlq:extraction"))
            .thenReturn(Optional.of(dlqSession));
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        storageService.storeDLQ("/tmp/test", "user_preferences", "network timeout");

        // Then
        ArgumentCaptor<ObservationEntity> captor = ArgumentCaptor.forClass(ObservationEntity.class);
        verify(observationRepository).save(captor.capture());

        ObservationEntity saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo("dlq_user_preferences");
        assertThat(saved.getTitle()).isEqualTo("DLQ: user_preferences");
        assertThat(saved.getSource()).isEqualTo("dlq");
        assertThat(saved.getConcepts()).contains("dlq", "user_preferences");
        assertThat(saved.getExtractedData()).containsEntry("error", "network timeout");
        assertThat(saved.getExtractedData()).containsEntry("template", "user_preferences");
    }

    // ===== TemplateConfig factory helper =====

    private static TemplateConfig makeTemplate(String name) {
        TemplateConfig tc = new TemplateConfig();
        tc.setName(name);
        tc.setEnabled(true);
        return tc;
    }
}
