package com.ablueforce.cortexce.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ablueforce.cortexce.config.ExtractionConfig.TemplateConfig;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.identity.MemoryScopeKey;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExtractionStorageService}.
 *
 * <p>These pin the post-refactor contract: extraction output and DLQ entries are
 * <b>standalone</b> observations. They carry no {@code conversation_id}, are typed by
 * {@code standalone_kind}, and belong to a memory scope. In particular the service must never
 * create a synthetic session row — the old behaviour that made a dead-letter queue look like a
 * conversation (and therefore deletable as one).
 */
@ExtendWith(MockitoExtension.class)
class ExtractionStorageServiceTest {

    private static final UUID SCOPE_ID = UUID.randomUUID();

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private MemoryScopeService memoryScopeService;

    private ExtractionStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new ExtractionStorageService(observationRepository, memoryScopeService);
    }

    private static TemplateConfig makeTemplate(String name) {
        TemplateConfig template = new TemplateConfig();
        template.setName(name);
        template.setEnabled(true);
        template.setSessionIdPattern("ext:test:" + name);
        template.setSourceFilter(List.of("user_statement", "manual"));
        return template;
    }

    // ===== storeExtractionResult =====

    @Test
    @DisplayName("extraction output is a standalone observation in the project scope")
    void storeExtractionResult_writesStandaloneObservation() {
        when(memoryScopeService.resolveProjectScope("/tmp/test-project")).thenReturn(SCOPE_ID);
        when(observationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TemplateConfig template = makeTemplate("user_preferences");
        ObservationEntity srcObs = new ObservationEntity();
        srcObs.setId(UUID.randomUUID());
        Map<String, Object> result = Map.of("likes", List.of("coffee", "tea"));

        storageService.storeExtractionResult(template, result, List.of(srcObs),
            "ext:hash:user-a", "/tmp/test-project");

        ArgumentCaptor<ObservationEntity> captor = ArgumentCaptor.forClass(ObservationEntity.class);
        verify(observationRepository).save(captor.capture());
        ObservationEntity saved = captor.getValue();

        assertThat(saved.getConversationId()).isNull();
        assertThat(saved.getStandaloneKind()).isEqualTo("extraction");
        assertThat(saved.getMemoryScopeId()).isEqualTo(SCOPE_ID);
        // The synthetic key survives as a compatibility/correlation value only.
        assertThat(saved.getContentSessionId()).isEqualTo("ext:hash:user-a");
        assertThat(saved.getType()).isEqualTo("extracted_user_preferences");
        assertThat(saved.getSource()).isEqualTo("llm_extraction");
        assertThat(saved.getExtractedData()).isEqualTo(result);
        assertThat(saved.getRefinedFromIds()).contains(srcObs.getId().toString());
        assertThat(saved.getTitle()).isEqualTo("Extraction: user_preferences");
        assertThat(saved.getPromptNumber()).isZero();
        assertThat(saved.getProjectPath()).isEqualTo("/tmp/test-project");
    }

    @Test
    @DisplayName("the service never creates a synthetic session")
    void storeExtractionResult_neverCreatesSession() {
        when(memoryScopeService.resolveProjectScope(anyString())).thenReturn(SCOPE_ID);
        when(observationRepository.save(any())).thenAnswer(inv -> {
            ObservationEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        ObservationEntity src = new ObservationEntity();
        src.setId(UUID.randomUUID());
        storageService.storeExtractionResult(makeTemplate("t"), Map.of("k", "v"),
            List.of(src), "ext:any", "/tmp/p");

        // The only collaborator is the observation repository plus the scope resolver:
        // there is no session repository to call any more.
        verifyNoMoreInteractions(memoryScopeService);
        verify(observationRepository, times(1)).save(any());
    }

    @Test
    void storeExtractionResult_skipsWhenResultIsEmpty() {
        storageService.storeExtractionResult(makeTemplate("t"), Map.of(), List.of(), "s", "/tmp/test");
        verifyNoInteractions(memoryScopeService);
        verifyNoInteractions(observationRepository);
    }

    @Test
    void storeExtractionResult_skipsWhenResultIsNull() {
        storageService.storeExtractionResult(makeTemplate("t"), null, List.of(), "s", "/tmp/test");
        verifyNoInteractions(memoryScopeService);
        verifyNoInteractions(observationRepository);
    }

    @Test
    void storeExtractionResult_rejectsNullSourceObservations() {
        assertThatThrownBy(() -> storageService.storeExtractionResult(
            makeTemplate("t"), Map.of("k", "v"), null, "s", "/tmp/test"))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(observationRepository);
    }

    @Test
    void storeExtractionResult_rejectsBlankCorrelationKey() {
        assertThatThrownBy(() -> storageService.storeExtractionResult(
            makeTemplate("t"), Map.of("k", "v"), List.of(new ObservationEntity()), "  ", "/tmp/test"))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(observationRepository);
    }

    // ===== storeDLQ =====

    @Test
    @DisplayName("a DLQ entry is standalone and scoped to the failing observation's project")
    void storeDLQ_writesStandaloneDlqEntry() {
        when(memoryScopeService.resolveProjectScope("/tmp/beta")).thenReturn(SCOPE_ID);
        when(observationRepository.save(any())).thenAnswer(inv -> {
            ObservationEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
        ListAppender<ILoggingEvent> logs = captureStorageLogs();

        try {
            storageService.storeDLQ("/tmp/beta", "user_preferences",
                new IllegalStateException("private-extraction-canary"));

            ArgumentCaptor<ObservationEntity> captor = ArgumentCaptor.forClass(ObservationEntity.class);
            verify(observationRepository).save(captor.capture());
            ObservationEntity saved = captor.getValue();

            assertThat(saved.getConversationId()).isNull();
            assertThat(saved.getStandaloneKind()).isEqualTo("dlq");
            assertThat(saved.getMemoryScopeId()).isEqualTo(SCOPE_ID);
            assertThat(saved.getContentSessionId()).isEqualTo("dlq:extraction");
            assertThat(saved.getType()).isEqualTo("dlq_user_preferences");
            assertThat(saved.getSource()).isEqualTo("dlq");
            assertThat(saved.getProjectPath()).isEqualTo("/tmp/beta");
            assertThat(saved.getExtractedData()).containsEntry("error", "IllegalStateException");
            assertThat(saved.getExtractedData().toString()).doesNotContain("private-extraction-canary");
            assertThat(logs.list).allSatisfy(event ->
                assertThat(event.getFormattedMessage()).doesNotContain("private-extraction-canary"));
            // The DLQ key is shared across projects, so scope must come from the row, not the key.
            verify(memoryScopeService).resolveProjectScope("/tmp/beta");
        } finally {
            detachStorageLogs(logs);
        }
    }

    @Test
    @DisplayName("two projects failing the same template land in different scopes")
    void storeDLQ_scopesPerProject() {
        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        when(memoryScopeService.resolveProjectScope("/tmp/a")).thenReturn(scopeA);
        when(memoryScopeService.resolveProjectScope("/tmp/b")).thenReturn(scopeB);
        when(observationRepository.save(any())).thenAnswer(inv -> {
            ObservationEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        storageService.storeDLQ("/tmp/a", "t", new IllegalArgumentException("err"));
        storageService.storeDLQ("/tmp/b", "t", new IllegalArgumentException("err"));

        ArgumentCaptor<ObservationEntity> captor = ArgumentCaptor.forClass(ObservationEntity.class);
        verify(observationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ObservationEntity::getMemoryScopeId)
            .containsExactly(scopeA, scopeB);
    }

    @Test
    @DisplayName("the manual-default scope key is never used for extraction or DLQ output")
    void extractionNeverLandsInManualDefault() {
        when(memoryScopeService.resolveProjectScope(anyString())).thenReturn(SCOPE_ID);
        when(observationRepository.save(any())).thenAnswer(inv -> {
            ObservationEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        storageService.storeDLQ("/tmp/p", "t", new IllegalArgumentException("e"));
        ObservationEntity src2 = new ObservationEntity();
        src2.setId(UUID.randomUUID());
        storageService.storeExtractionResult(makeTemplate("t"), Map.of("k", "v"),
            List.of(src2), "ext:k", "/tmp/p");

        verify(memoryScopeService, never()).resolveManualDefault();
    }

    private static ListAppender<ILoggingEvent> captureStorageLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExtractionStorageService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachStorageLogs(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(ExtractionStorageService.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
