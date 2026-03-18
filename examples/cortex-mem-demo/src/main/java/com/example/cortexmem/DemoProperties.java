package com.example.cortexmem;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo configuration: multiple project paths for showcasing project isolation.
 *
 * <p>Configure in application.yml:
 * <pre>
 * demo:
 *   projects:
 *     project-a: /path/to/project-a
 *     project-b: /path/to/project-b
 *     default: ${user.dir}
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    /**
     * Named projects: key = logical name, value = absolute path.
     */
    private Map<String, String> projects = new LinkedHashMap<>();

    public Map<String, String> getProjects() {
        return projects;
    }

    public void setProjects(Map<String, String> projects) {
        this.projects = projects;
    }

    /**
     * Resolve project path by key. Falls back to key if not in map (treat as path).
     */
    public String resolveProjectPath(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return null;
        }
        String resolved = projects.get(projectKey);
        return resolved != null ? resolved : projectKey;
    }
}
