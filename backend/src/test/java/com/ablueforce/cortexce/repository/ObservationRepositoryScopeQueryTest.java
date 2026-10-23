package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.service.MemoryScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ObservationRepositoryScopeQueryTest {

    @Test
    void multiProjectObservationQueriesFilterByExactProjectScopes() throws Exception {
        assertScopeQuery("findByProjectsLimited", List.class, int.class);
        assertScopeQuery("findByProjectsTypesAndConcepts", List.class, List.class, List.class,
            boolean.class, int.class);
        assertScopeQuery("findBySourceIn", String.class, List.class, boolean.class, int.class);
        assertScopeQuery("findByProjectLimited", String.class, int.class);
        assertScopeQuery("findByTypeAndConcepts", String.class, List.class, List.class, boolean.class, int.class);
        assertScopeQuery("countByProjectPath", String.class);
        assertScopeQuery("findByProjectPathOrderByCreatedAtEpochDesc", String.class);
        assertScopeQuery("findByProjectPathOrderByCreatedAtEpochDesc", String.class, Pageable.class);
    }

    @Test
    void databaseQueriesFollowScopeOwnershipInsteadOfDisplayProjectPath() throws Exception {
        String url = System.getenv("MEMSCOPE_TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(),
            "set MEMSCOPE_TEST_DB_URL to a disposable V20 database");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(env("MEMSCOPE_TEST_DB_USER", "postgres"));
        dataSource.setPassword(env("MEMSCOPE_TEST_DB_PASSWORD", "postgres"));
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        MemoryScopeService scopes = new MemoryScopeService(jdbc);

        String ownerPath = "/scope-query-test/owner/" + UUID.randomUUID();
        String outsidePath = "/scope-query-test/outside/" + UUID.randomUUID();
        String displayPath = "/scope-query-test/display/" + UUID.randomUUID();
        UUID ownerScope = scopes.resolveProjectScope(ownerPath);
        UUID outsideScope = scopes.resolveProjectScope(outsidePath);
        UUID manualProjectScope = scopes.resolveProjectScope("manual-memories");
        UUID manualDefaultScope = scopes.resolveManualDefault();
        UUID ownerObservation = null;
        UUID outsideObservation = null;
        UUID manualProjectObservation = null;
        UUID manualDefaultObservation = null;

        try {
            ownerObservation = insertManualObservation(jdbc, displayPath, ownerScope);
            outsideObservation = insertManualObservation(jdbc, ownerPath, outsideScope);
            manualProjectObservation = insertManualObservation(jdbc, displayPath, manualProjectScope);
            manualDefaultObservation = insertManualObservation(jdbc, "manual-memories", manualDefaultScope);

            MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projects", List.of(ownerPath))
                .addValue("types", List.of("discovery"))
                .addValue("concepts", List.of("unused"))
                .addValue("conceptsEmpty", true)
                .addValue("limit", 100);

            assertThat(runIds(jdbc, query("findByProjectsLimited", List.class, int.class), params))
                .contains(ownerObservation).doesNotContain(outsideObservation);
            assertThat(runIds(jdbc, query("findByProjectsTypesAndConcepts", List.class, List.class, List.class,
                boolean.class, int.class), params))
                .contains(ownerObservation).doesNotContain(outsideObservation);

            MapSqlParameterSource projectParams = new MapSqlParameterSource("project", ownerPath);
            assertThat(runIds(jdbc, query("findByProjectPathOrderByCreatedAtEpochDesc", String.class), projectParams))
                .contains(ownerObservation).doesNotContain(outsideObservation);
            assertThat(runIds(jdbc, query("findByProjectPathOrderByCreatedAtEpochDesc", String.class, Pageable.class)
                    + " LIMIT 100 OFFSET 0", projectParams))
                .contains(ownerObservation).doesNotContain(outsideObservation);
            assertThat(jdbc.queryForObject(query("countByProjectPath", String.class), projectParams, Long.class))
                .isEqualTo(1L);

            MapSqlParameterSource searchParams = new MapSqlParameterSource()
                .addValue("project", "manual-memories")
                .addValue("types", List.of("discovery"))
                .addValue("concepts", List.of("unused"))
                .addValue("conceptsEmpty", true)
                .addValue("limit", 100);
            Set<UUID> manualSearchIds = runIds(jdbc,
                query("findByTypeAndConcepts", String.class, List.class, List.class, boolean.class, int.class),
                searchParams);
            assertThat(manualSearchIds)
                .contains(manualProjectObservation, manualDefaultObservation)
                .doesNotContain(ownerObservation, outsideObservation);
            assertThat(jdbc.queryForObject(query("countByProjectPath", String.class), searchParams, Long.class))
                .isEqualTo(2L);

            MapSqlParameterSource extractionParams = new MapSqlParameterSource()
                .addValue("project", "manual-memories")
                .addValue("sources", List.of("manual"))
                .addValue("includeStandaloneManual", false)
                .addValue("limit", 100);
            String extractionQuery = query("findBySourceIn", String.class, List.class, boolean.class, int.class);
            assertThat(runIds(jdbc, extractionQuery, extractionParams)).isEmpty();
            extractionParams.addValue("includeStandaloneManual", true);
            assertThat(runIds(jdbc, extractionQuery, extractionParams))
                .contains(manualProjectObservation, manualDefaultObservation);

            MapSqlParameterSource multiProjectParams = new MapSqlParameterSource()
                .addValue("projects", List.of(ownerPath, "manual-memories"))
                .addValue("limit", 100)
                .addValue("types", List.of("discovery"))
                .addValue("concepts", List.of("unused"))
                .addValue("conceptsEmpty", true);
            assertThat(runIds(jdbc, query("findByProjectsLimited", List.class, int.class), multiProjectParams))
                .contains(ownerObservation, manualProjectObservation, manualDefaultObservation)
                .doesNotContain(outsideObservation);
            assertThat(runIds(jdbc, query("findByProjectsTypesAndConcepts", List.class, List.class, List.class,
                boolean.class, int.class), multiProjectParams))
                .contains(ownerObservation, manualProjectObservation, manualDefaultObservation)
                .doesNotContain(outsideObservation);

            Set<String> scheduledProjects = jdbc.query(query("findDistinctProjects"),
                (ResultSet rs) -> {
                    Set<String> projects = new java.util.HashSet<>();
                    while (rs.next()) projects.add(rs.getString(1));
                    return projects;
                });
            assertThat(scheduledProjects)
                .contains(ownerPath, outsidePath, "manual-memories")
                .doesNotContain(displayPath);
        } finally {
            List<UUID> observationIds = new java.util.ArrayList<>();
            if (ownerObservation != null) observationIds.add(ownerObservation);
            if (outsideObservation != null) observationIds.add(outsideObservation);
            if (manualProjectObservation != null) observationIds.add(manualProjectObservation);
            if (manualDefaultObservation != null) observationIds.add(manualDefaultObservation);
            if (!observationIds.isEmpty()) {
                jdbc.update("DELETE FROM mem_observations WHERE id IN (:ids)",
                    new MapSqlParameterSource("ids", observationIds));
            }
            jdbc.update("DELETE FROM mem_memory_scopes WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", List.of(ownerScope, outsideScope, manualProjectScope)));
        }
    }

    private static void assertScopeQuery(String methodName, Class<?>... parameterTypes) throws Exception {
        Query query = queryAnnotation(methodName, parameterTypes);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).contains("memory_scope_id", "mem_memory_scopes", "scope_type = 'project'")
            .contains("scope_type = 'manual_default'", "COLLATE \"C\"")
            .doesNotContain("WHERE project_path IN");
    }

    private static String query(String methodName, Class<?>... parameterTypes) throws Exception {
        return queryAnnotation(methodName, parameterTypes).value();
    }

    private static Query queryAnnotation(String methodName, Class<?>... parameterTypes) throws Exception {
        return ObservationRepository.class.getMethod(methodName, parameterTypes).getAnnotation(Query.class);
    }

    private static UUID insertManualObservation(NamedParameterJdbcTemplate jdbc, String displayPath,
                                                UUID memoryScopeId) {
        return jdbc.queryForObject("""
            INSERT INTO mem_observations (
                content_session_id, platform_source, project_path, type, title, subtitle, source,
                created_at_epoch, memory_scope_id, conversation_id, standalone_kind
            ) VALUES (
                'manual-memories', 'claude', :projectPath, 'discovery', 'scope query fixture',
                'Manual memory', 'manual', 1, :scopeId, NULL, 'manual'
            ) RETURNING id
            """, new MapSqlParameterSource()
                .addValue("projectPath", displayPath)
                .addValue("scopeId", memoryScopeId), UUID.class);
    }

    private static Set<UUID> runIds(NamedParameterJdbcTemplate jdbc, String query,
                                   MapSqlParameterSource params) {
        return jdbc.query(query, params, (ResultSet rs) -> {
            Set<UUID> ids = new java.util.HashSet<>();
            while (rs.next()) ids.add(rs.getObject("id", UUID.class));
            return ids;
        });
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
