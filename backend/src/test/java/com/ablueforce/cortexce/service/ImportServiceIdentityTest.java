package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.controller.ImportController;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceIdentityTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private ObservationRepository observationRepository;
    @Mock private SummaryRepository summaryRepository;
    @Mock private UserPromptRepository userPromptRepository;
    @Mock private SessionManagementService sessionManagementService;
    @Mock private MemoryScopeService memoryScopeService;
    @Mock private ConversationWriteService conversationWriteService;
    @Mock private StandaloneObservationImportLock standaloneObservationImportLock;

    private ImportService service;

    @BeforeEach
    void setUp() {
        service = new ImportService(sessionRepository, observationRepository, summaryRepository,
            userPromptRepository, sessionManagementService, memoryScopeService, conversationWriteService,
            standaloneObservationImportLock);
    }

    @Test
    void bulkObservationImportKeepsSameExternalIdIndependentAcrossPlatforms() {
        UUID claudeId = UUID.randomUUID();
        UUID codexId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        SessionEntity claude = session(claudeId, "claude");
        SessionEntity codex = session(codexId, "codex");
        when(sessionRepository.findByPlatformSourceAndContentSessionId("claude", "shared-id"))
            .thenReturn(Optional.of(claude));
        when(sessionRepository.findByPlatformSourceAndContentSessionId("codex", "shared-id"))
            .thenReturn(Optional.of(codex));
        when(observationRepository.findByConversationIdIn(anyList())).thenReturn(List.of());
        when(memoryScopeService.resolveProjectScope("/repo")).thenReturn(scopeId);
        when(observationRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ObservationEntity> rows = invocation.getArgument(0);
            rows.forEach(row -> row.setId(UUID.randomUUID()));
            return rows;
        });

        var result = service.importObservations(List.of(
            observationData("claude", "shared-id"), observationData("codex", "shared-id")));

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.duplicates()).isZero();
        assertThat(result.errors()).isZero();
        ArgumentCaptor<List<ObservationEntity>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(observationRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue())
            .extracting(ObservationEntity::getConversationId)
            .containsExactlyInAnyOrder(claudeId, codexId);
        assertThat(rowsCaptor.getValue()).allSatisfy(row -> {
            assertThat(row.getMemoryScopeId()).isEqualTo(scopeId);
            assertThat(row.getProjectPath()).isEqualTo("/repo");
        });
        verify(conversationWriteService).lockForChildMutations(any());
    }

    @Test
    void bulkObservationImportDeduplicatesAgainstConversationUuid() {
        UUID conversationId = UUID.randomUUID();
        SessionEntity parent = session(conversationId, "claude");
        ObservationEntity existing = new ObservationEntity();
        existing.setConversationId(conversationId);
        existing.setTitle("same title");
        existing.setCreatedAtEpoch(42L);
        when(sessionRepository.findByPlatformSourceAndContentSessionId("claude", "same-id"))
            .thenReturn(Optional.of(parent));
        when(observationRepository.findByConversationIdIn(anyList())).thenReturn(List.of(existing));

        var result = service.importObservations(List.of(observationData("claude", "same-id")));

        assertThat(result.imported()).isZero();
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.errors()).isZero();
        verify(observationRepository, never()).saveAll(anyList());
    }

    @Test
    void exportedStandaloneWireRowsImportWithoutCreatingConversations() throws Exception {
        UUID manualDefaultScope = UUID.randomUUID();
        UUID manualProjectScope = UUID.randomUUID();
        UUID projectScope = UUID.randomUUID();
        List<UUID> sourceObservationIds = List.of(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000004"));
        when(memoryScopeService.resolveManualDefault()).thenReturn(manualDefaultScope);
        when(memoryScopeService.resolveProjectScope("manual-memories")).thenReturn(manualProjectScope);
        when(memoryScopeService.resolveProjectScope("/repo")).thenReturn(projectScope);
        when(observationRepository.findByMemoryScopeIdInAndStandaloneKindIn(anyList(), anyList()))
            .thenReturn(List.of());
        when(observationRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ObservationEntity> rows = invocation.getArgument(0);
            rows.forEach(row -> row.setId(UUID.randomUUID()));
            return rows;
        });

        ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String exportedBundle = """
            {
              "exportedAt":"2026-10-23T00:00:00Z",
              "observations":[
                {
                  "id":"00000000-0000-0000-0000-000000000001",
                  "content_session_id":"manual-memories",
                  "platform_source":"claude",
                  "project":"manual-memories",
                  "type":"discovery",
                  "title":"saved default note",
                  "narrative":"remember this",
                  "facts":"[\\"fact\\"]",
                  "concepts":"[]",
                  "files_read":"[]",
                  "files_modified":"[]",
                  "created_at_epoch":123,
                  "conversation_id":null,
                  "standalone_kind":"manual",
                  "memory_scope_type":"manual_default",
                  "source":"manual",
                  "extractedData":{"origin":"export"}
                },
                {
                  "id":"00000000-0000-0000-0000-000000000002",
                  "content_session_id":"manual-memories",
                  "platform_source":"claude",
                  "project":"manual-memories",
                  "type":"discovery",
                  "title":"saved project note",
                  "narrative":"project memory",
                  "facts":"[]",
                  "concepts":"[]",
                  "files_read":"[]",
                  "files_modified":"[]",
                  "created_at_epoch":124,
                  "conversation_id":null,
                  "standalone_kind":"manual",
                  "memory_scope_type":"project",
                  "source":"manual"
                },
                {
                  "id":"00000000-0000-0000-0000-000000000003",
                  "content_session_id":"extract:abc",
                  "platform_source":"claude",
                  "project":"/repo",
                  "type":"extracted_user_preference",
                  "title":"extracted preference",
                  "narrative":"prefers concise answers",
                  "facts":"[]",
                  "concepts":"[]",
                  "files_read":"[]",
                  "files_modified":"[]",
                  "created_at_epoch":125,
                  "conversation_id":null,
                  "standalone_kind":"extraction",
                  "memory_scope_type":"project",
                  "source":"llm_extraction"
                },
                {
                  "id":"00000000-0000-0000-0000-000000000004",
                  "content_session_id":"dlq:abc",
                  "platform_source":"claude",
                  "project":"/repo",
                  "type":"dlq_extraction",
                  "title":"failed extraction",
                  "narrative":"retry later",
                  "facts":"[]",
                  "concepts":"[]",
                  "files_read":"[]",
                  "files_modified":"[]",
                  "created_at_epoch":126,
                  "conversation_id":null,
                  "standalone_kind":"dlq",
                  "memory_scope_type":"project",
                  "source":"dlq"
                }
              ]
            }
            """;
        ImportController.BulkImportRequest imported = mapper.readValue(
            exportedBundle, ImportController.BulkImportRequest.class);

        var result = service.importObservations(imported.observations());

        assertThat(result.imported()).isEqualTo(4);
        assertThat(result.duplicates()).isZero();
        assertThat(result.errors()).isZero();
        verify(sessionRepository, never()).findByPlatformSourceAndContentSessionId(any(), any());
        ArgumentCaptor<List<ObservationEntity>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(observationRepository).saveAll(rowsCaptor.capture());
        List<ObservationEntity> rows = rowsCaptor.getValue();
        assertThat(rows).allSatisfy(row -> assertThat(row.getConversationId()).isNull());
        assertThat(rows).extracting(ObservationEntity::getStandaloneKind)
            .containsExactly("manual", "manual", "extraction", "dlq");
        assertThat(rows).extracting(ObservationEntity::getMemoryScopeId)
            .containsExactly(manualDefaultScope, manualProjectScope, projectScope, projectScope);
        assertThat(rows).extracting(ObservationEntity::getSource)
            .containsExactly("manual", "manual", "llm_extraction", "dlq");
        assertThat(rows).extracting(ObservationEntity::getId)
            .doesNotContainAnyElementsOf(sourceObservationIds);
    }

    @Test
    void importDtoAcceptsObservationExportWireNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String exportedBundle = """
            {
              "exportedAt":"2026-10-23T00:00:00Z",
              "sessions":[{
                "id":"source-session-uuid",
                "content_session_id":"conversation-1",
                "project":"/repo",
                "user_prompt":"request",
                "started_at_epoch":100,
                "completed_at_epoch":200,
                "status":"completed"
              }],
              "observations":[{
                "id":"source-observation-uuid",
                "content_session_id":"manual-memories",
                "platform_source":"claude",
                "project":"manual-memories",
                "type":"discovery",
                "title":"saved note",
                "narrative":"remember this",
                "facts":"[\\"fact\\"]",
                "concepts":"[]",
                "files_read":"[]",
                "files_modified":"[]",
                "created_at_epoch":123,
                "conversation_id":null,
                "standalone_kind":"manual",
                "memory_scope_type":"manual_default",
                "source":"manual",
                "extractedData":{"origin":"export"}
              }],
              "summaries":[{
                "session_id":"conversation-1",
                "project":"/repo",
                "request":"request",
                "created_at_epoch":200
              }],
              "prompts":[{
                "content_session_id":"conversation-1",
                "platform_source":"claude",
                "prompt_number":1,
                "prompt_text":"request",
                "created_at_epoch":100
              }]
            }
            """;

        ImportController.BulkImportRequest imported = mapper.readValue(
            exportedBundle, ImportController.BulkImportRequest.class);

        assertThat(imported.sessions()).singleElement().satisfies(session -> {
            assertThat(session.contentSessionId()).isEqualTo("conversation-1");
            assertThat(session.projectPath()).isEqualTo("/repo");
        });
        assertThat(imported.observations()).singleElement().satisfies(observation -> {
            assertThat(observation.sessionId()).isEqualTo("manual-memories");
            assertThat(observation.projectPath()).isEqualTo("manual-memories");
            assertThat(observation.content()).isEqualTo("remember this");
            assertThat(observation.factsJson()).isEqualTo("[\"fact\"]");
            assertThat(observation.standaloneKind()).isEqualTo("manual");
            assertThat(observation.memoryScopeType()).isEqualTo("manual_default");
            assertThat(observation.source()).isEqualTo("manual");
            assertThat(observation.extractedData()).containsEntry("origin", "export");
        });
        assertThat(imported.summaries()).singleElement().satisfies(summary -> {
            assertThat(summary.sessionId()).isEqualTo("conversation-1");
            assertThat(summary.projectPath()).isEqualTo("/repo");
        });
        assertThat(imported.prompts()).singleElement().satisfies(prompt -> {
            assertThat(prompt.sessionId()).isEqualTo("conversation-1");
            assertThat(prompt.promptText()).isEqualTo("request");
        });
    }

    private SessionEntity session(UUID id, String platform) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setPlatformSource(platform);
        session.setContentSessionId("shared-id");
        session.setProjectPath("/repo");
        session.setStatus("completed");
        return session;
    }

    private ImportService.ObservationImportData observationData(String platform, String externalId) {
        return new ImportService.ObservationImportData(
            externalId, platform, "/repo", "discovery", "same title", null, "content",
            null, null, null, null, 1, 42L, 0,
            null, null, null, null, null, null, null, null);
    }

    private ImportService.ObservationImportData standaloneData(String correlationKey, String type,
                                                                 String scopeType, String source) {
        return new ImportService.ObservationImportData(
            correlationKey, "claude", "manual_default".equals(scopeType) ? "manual-memories"
                : "manual".equals(source) ? "manual-memories" : "/repo",
            type, "title-" + type, null, "content", "[]", "[]", "[]", "[]", 1, 42L, 0,
            null, null, null, null, source.equals("manual") ? "manual" : source.equals("dlq") ? "dlq" : "extraction",
            scopeType, source, Map.of("origin", "fixture"));
    }
}
