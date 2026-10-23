package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.ExtractionConfig;
import com.ablueforce.cortexce.config.ExtractionConfig.TemplateConfig;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StructuredExtractionManualScopeTest {

    @Test
    void templateExcludesManualStandaloneRowsByDefault() {
        TemplateConfig template = template();
        StructuredExtractionService service = service(template);

        service.runExtraction("/repo");

        verify(repository).findBySourceIn("/repo", List.of("user_statement", "manual"), false, 100);
    }

    @Test
    void templateCanExplicitlyOptInToManualStandaloneRows() {
        TemplateConfig template = template();
        template.setIncludeStandaloneManual(true);
        StructuredExtractionService service = service(template);

        service.runExtraction("/repo");

        verify(repository).findBySourceIn("/repo", List.of("user_statement", "manual"), true, 100);
    }

    private final ObservationRepository repository = mock(ObservationRepository.class);

    private TemplateConfig template() {
        TemplateConfig template = new TemplateConfig();
        template.setName("user_preference");
        return template;
    }

    private StructuredExtractionService service(TemplateConfig template) {
        ExtractionConfig config = new ExtractionConfig();
        config.setEnabled(true);
        config.setTemplates(List.of(template));
        LlmService llmService = mock(LlmService.class);
        when(llmService.isAvailable()).thenReturn(true);
        return new StructuredExtractionService(config, repository, mock(SessionRepository.class),
            llmService, mock(ExtractionStorageService.class));
    }
}
