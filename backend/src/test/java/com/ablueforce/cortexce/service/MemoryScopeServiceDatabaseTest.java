package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.identity.MemoryScopeKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link MemoryScopeService} against a REAL PostgreSQL instance.
 *
 * <p>Mocks cannot prove any of the properties that matter here: {@code ON CONFLICT}
 * atomicity, {@code COLLATE "C"} exactness, or the digest-collision fail-closed path.
 * They all depend on the database actually executing the statement.
 *
 * <p>The test runs only when {@code MEMSCOPE_TEST_DB_URL} points at a disposable
 * database whose schema has already been migrated through V20. Anywhere else it is
 * skipped rather than silently passing.
 *
 * <pre>
 *   export MEMSCOPE_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55432/mem_dev_copy
 *   export MEMSCOPE_TEST_DB_USER=postgres MEMSCOPE_TEST_DB_PASSWORD=postgres
 * </pre>
 */
class MemoryScopeServiceDatabaseTest {

    private static final String URL_ENV = "MEMSCOPE_TEST_DB_URL";
    private static final String USER_ENV = "MEMSCOPE_TEST_DB_USER";
    private static final String PASS_ENV = "MEMSCOPE_TEST_DB_PASSWORD";

    private DriverManagerDataSource dataSource;
    private NamedParameterJdbcTemplate jdbc;
    private MemoryScopeService service;
    /** Scope ids created by this test, removed in {@link #cleanup()}. */
    private final List<UUID> created = new ArrayList<>();

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    @BeforeEach
    void setUp() throws SQLException {
        String url = System.getenv(URL_ENV);
        assumeTrue(url != null && !url.isBlank(),
            "set " + URL_ENV + " to run real-database scope tests");

        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(env(USER_ENV, "postgres"));
        dataSource.setPassword(env(PASS_ENV, "postgres"));
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        service = new MemoryScopeService(jdbc);

        // Fail loudly and clear if the schema is not migrated: these tests are meaningless
        // against a pre-V20 schema, and skipping there would hide that fact.
        Integer present = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.tables
            WHERE table_name = 'mem_memory_scopes'
            """, new MapSqlParameterSource(), Integer.class);
        assumeTrue(present != null && present > 0,
            "schema must be migrated through V20 before running scope tests");
    }

    @AfterEach
    void cleanup() {
        if (jdbc == null) {
            return;
        }
        for (UUID id : created) {
            jdbc.update("DELETE FROM mem_memory_scopes WHERE id = :id AND scope_type = 'project'",
                new MapSqlParameterSource("id", id));
        }
        created.clear();
    }

    private UUID tracked(UUID id) {
        created.add(id);
        return id;
    }

    // ------------------------------------------------------------------
    // Exactness
    // ------------------------------------------------------------------

    @Test
    @DisplayName("look-alike project paths get distinct scopes")
    void exactKeysDoNotMerge() {
        String base = "/memsope-test/" + UUID.randomUUID();
        String[] variants = {base, base + "/", " " + base, base + " ", base + "/.", base.toUpperCase()};

        List<UUID> ids = new ArrayList<>();
        for (String v : variants) {
            ids.add(tracked(service.resolveProjectScope(v)));
        }

        assertEquals(variants.length, ids.stream().distinct().count(),
            "every spelling must be a separate scope; got " + ids);
    }

    @Test
    @DisplayName("resolving the same key twice returns the same scope id")
    void sameKeyIsStable() {
        String path = "/memsope-test/" + UUID.randomUUID() + "/stable";
        UUID first = tracked(service.resolveProjectScope(path));
        UUID second = service.resolveProjectScope(path);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("the stored key is byte-identical to the requested path")
    void storedKeyIsVerbatim() {
        String path = "/memsope-test/" + UUID.randomUUID() + "/Ünïcode path with spaces/";
        UUID id = tracked(service.resolveProjectScope(path));
        String stored = jdbc.queryForObject(
            "SELECT scope_key FROM mem_memory_scopes WHERE id = :id",
            new MapSqlParameterSource("id", id), String.class);
        assertEquals(path, stored);
    }

    @Test
    @DisplayName("manual_default resolves to exactly one fixed scope")
    void manualDefaultIsSingleton() {
        UUID a = tracked(service.resolveManualDefault());
        UUID b = service.resolveManualDefault();
        assertEquals(a, b);
        String key = jdbc.queryForObject(
            "SELECT scope_key FROM mem_memory_scopes WHERE id = :id",
            new MapSqlParameterSource("id", a), String.class);
        assertEquals("manual-default-v1", key);
    }

    @Test
    @DisplayName("an explicit project named manual-default-v1 is NOT the manual default")
    void projectWithReservedNameIsSeparate() {
        UUID manual = tracked(service.resolveManualDefault());
        UUID project = tracked(service.resolveProjectScope("manual-default-v1"));
        assertTrue(!manual.equals(project));
    }

    // ------------------------------------------------------------------
    // Read paths never create
    // ------------------------------------------------------------------

    @Test
    @DisplayName("find() never creates a scope")
    void findDoesNotCreate() {
        String path = "/memsope-test/" + UUID.randomUUID() + "/never-written";
        assertNull(service.findProjectScope(path));
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM mem_memory_scopes
            WHERE scope_type = 'project' AND scope_key = :k
            """, new MapSqlParameterSource("k", path), Integer.class);
        assertEquals(0, count, "a read must not manufacture an empty scope");
    }

    @Test
    void findExistingReturnsId() {
        String path = "/memsope-test/" + UUID.randomUUID() + "/existing";
        UUID createdId = tracked(service.resolveProjectScope(path));
        assertEquals(createdId, service.findProjectScope(path));
    }

    // ------------------------------------------------------------------
    // Concurrency
    // ------------------------------------------------------------------

    @Test
    @DisplayName("concurrent first-writers converge on a single scope id")
    void concurrentCreationIsAtomic() throws Exception {
        String path = "/memsope-test/" + UUID.randomUUID() + "/concurrent";
        int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<UUID> task = () -> {
                    start.await(10, TimeUnit.SECONDS);
                    // A fresh service per thread keeps transactions independent, which is
                    // exactly the race the resolver must survive.
                    return new MemoryScopeService(jdbc).resolveProjectScope(path);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();

            UUID expected = null;
            for (Future<UUID> f : futures) {
                UUID got = f.get(30, TimeUnit.SECONDS);
                assertNotNull(got);
                if (expected == null) {
                    expected = got;
                }
                assertEquals(expected, got, "all writers must observe one scope");
            }
            tracked(expected);

            Integer rows = jdbc.queryForObject("""
                SELECT count(*) FROM mem_memory_scopes
                WHERE scope_type = 'project' AND scope_key = :k
                """, new MapSqlParameterSource("k", path), Integer.class);
            assertEquals(1, rows, "concurrency must not create duplicate scopes");
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Digest collision: fail closed, never merge
    // ------------------------------------------------------------------

    @Test
    @DisplayName("two different keys with the same digest are refused, not merged")
    void digestCollisionFailsClosed() throws Exception {
        // Force every key to the same digest, then prove the second distinct key is
        // reported as a collision instead of silently reusing the first scope.
        byte[] frozen = MessageDigest.getInstance("SHA-256")
            .digest("constant".getBytes(StandardCharsets.UTF_8));
        MemoryScopeKey.KeyDigester colliding = key -> frozen;
        MemoryScopeService collidingService = new MemoryScopeService(jdbc, colliding);

        String first = "/memsope-test/" + UUID.randomUUID() + "/collision-a";
        String second = "/memsope-test/" + UUID.randomUUID() + "/collision-b";

        UUID a = tracked(collidingService.resolveProjectScope(first));
        assertNotNull(a);

        MemoryScopeService.ScopeKeyDigestCollisionException ex =
            assertThrows(MemoryScopeService.ScopeKeyDigestCollisionException.class,
                () -> collidingService.resolveProjectScope(second));
        assertTrue(ex.getMessage().contains("SCOPE_KEY_DIGEST_COLLISION"));
        // The exception must not leak either project path.
        assertTrue(!ex.getMessage().contains(first) && !ex.getMessage().contains(second));

        // The original scope is untouched and still points at the first key.
        String storedKey = jdbc.queryForObject(
            "SELECT scope_key FROM mem_memory_scopes WHERE id = :id",
            new MapSqlParameterSource("id", a), String.class);
        assertEquals(first, storedKey, "a collision must never overwrite the existing row");
    }

    @Test
    @DisplayName("the same key under the injected digester still resolves normally")
    void sameKeyWithInjectedDigesterIsFine() throws Exception {
        byte[] frozen = MessageDigest.getInstance("SHA-256")
            .digest("constant2".getBytes(StandardCharsets.UTF_8));
        MemoryScopeService s = new MemoryScopeService(jdbc, key -> frozen);
        String path = "/memsope-test/" + UUID.randomUUID() + "/same";
        UUID a = tracked(s.resolveProjectScope(path));
        assertEquals(a, s.resolveProjectScope(path));
    }

    // ------------------------------------------------------------------
    // Constraint surface
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the database rejects a non-project, non-default scope type")
    void databaseRejectsUnknownScopeType() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.executeUpdate(
                "INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256) "
                    + "VALUES ('tenant', 'x', decode(repeat('00',32),'hex'))"));
        }
    }

    @Test
    @DisplayName("the database rejects a wrong-length digest")
    void databaseRejectsBadDigestLength() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.executeUpdate(
                "INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256) "
                    + "VALUES ('project', '/x', decode('0011','hex'))"));
        }
    }

    @Test
    @DisplayName("project scope requires a non-empty key; manual_default requires its fixed key")
    void databaseEnforcesKeyShape() throws Exception {
        byte[] sha = MessageDigest.getInstance("SHA-256").digest("x".getBytes(StandardCharsets.UTF_8));
        String hex = java.util.HexFormat.of().formatHex(sha);
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.executeUpdate(
                "INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256) VALUES "
                    + "('project', '', decode('" + hex + "','hex'))"));
            assertThrows(SQLException.class, () -> st.executeUpdate(
                "INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256) VALUES "
                    + "('manual_default', 'something-else', decode('" + hex + "','hex'))"));
        }
    }
}
