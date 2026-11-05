package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.repository.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only project catalogue shared by the viewer and the SSE bootstrap.
 *
 * <p>The viewer ({@code GET /api/projects}) and the stream ({@code initial_load})
 * publish the same three lists; they used to build them independently from
 * {@link SessionRepository} in two controllers. Keeping the assembly here means a
 * change to "what counts as a project" lands in one place, and neither controller
 * needs a repository dependency.
 */
@Service
public class ProjectCatalogService {

    private final SessionRepository sessionRepository;

    public ProjectCatalogService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** Immutable view of the catalogue; field names match the wire contract. */
    public record ProjectCatalog(
        List<String> projects,
        List<String> sources,
        Map<String, List<String>> projectsBySource) {
    }

    @Transactional(readOnly = true)
    public ProjectCatalog getCatalog() {
        List<String> projects = sessionRepository.findAllProjects();
        List<String> sources = sessionRepository.findAllPlatformSources();

        Map<String, List<String>> projectsBySource = new HashMap<>();
        for (Object[] row : sessionRepository.findProjectsByPlatformSource()) {
            String source = (String) row[0];
            String project = (String) row[1];
            projectsBySource.computeIfAbsent(source, key -> new ArrayList<>()).add(project);
        }

        return new ProjectCatalog(projects, sources, projectsBySource);
    }

    @Transactional(readOnly = true)
    public List<String> getProjects() {
        return sessionRepository.findAllProjects();
    }

    @Transactional(readOnly = true)
    public List<String> getSources() {
        return sessionRepository.findAllPlatformSources();
    }
}
