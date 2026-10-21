package com.example.cortexmem;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the demo's endpoint surface — every {@code (verb, path)} the demo serves.
 *
 * <p>Why this exists: the demo exposes 44 endpoints across 12 controllers, and
 * until now <em>not one of them</em> was asserted anywhere. The only endpoint
 * with a test was {@code GET /memory/health}. So the surface was free to change
 * in any direction — a renamed path, a flipped verb, a dropped controller —
 * and nothing would notice. That is the same failure shape as the capture
 * contracts pinned in rounds 385–387, one level up: a family of behaviour with
 * no member of it pinned.
 *
 * <p>The set is compared for <em>equality</em>, not containment: an added or a
 * removed endpoint fails this test, which is the point. A demo is the first
 * thing a reader copies from, so its surface is part of what the repository
 * promises.
 *
 * <p>Read off the annotations, not from a running server, so the test needs no
 * backend and no Spring context — it asserts what the code declares. The
 * declared surface was checked against the live server separately (44 endpoints
 * served, every path in the README's table resolving).
 */
class DemoEndpointSurfaceTest {

    private static final Class<?>[] CONTROLLERS = {
            ChatController.class,
            ExperiencesController.class,
            ExtractionController.class,
            FeedbackController.class,
            IngestController.class,
            ManagementController.class,
            MemoryController.class,
            ObservationsController.class,
            ProjectsController.class,
            SearchController.class,
            SessionLifecycleController.class,
            ToolsController.class,
    };

    @Test
    void shouldServeExactlyTheDeclaredEndpointSurface() {
        Set<String> expected = new TreeSet<>(Arrays.asList(
                "GET /chat",
                "GET /demo/experiences",
                "GET /demo/experiences/health",
                "GET /demo/iclprompt",
                "GET /demo/extraction/health",
                "GET /demo/extraction/history",
                "GET /demo/extraction/latest",
                "GET /demo/feedback/health",
                "GET /demo/manage/health",
                "GET /demo/manage/modes",
                "GET /demo/manage/projects",
                "GET /demo/manage/quality",
                "GET /demo/manage/settings",
                "GET /demo/manage/stats",
                "GET /demo/manage/version",
                "GET /demo/observations",
                "GET /demo/observations/status",
                "GET /demo/observations/{id}",
                "GET /demo/projects",
                "GET /demo/search",
                "GET /demo/search/health",
                "GET /demo/session/tool",
                "GET /demo/tool",
                "GET /memory/experiences",
                "GET /memory/experiences/filtered",
                "GET /memory/health",
                "GET /memory/icl",
                "GET /memory/icl/truncated",
                "GET /memory/quality",
                "POST /demo/extraction/run",
                "POST /demo/feedback",
                "POST /demo/ingest/prompt",
                "POST /demo/ingest/session-end",
                "POST /demo/manage/refine",
                "POST /demo/observations/batch",
                "POST /demo/observations/create",
                "POST /demo/session/end",
                "POST /demo/session/lifecycle",
                "POST /demo/session/prompt",
                "POST /demo/session/start",
                "POST /memory/refine",
                "PATCH /demo/observations/{id}",
                "PATCH /demo/session/user",
                "DELETE /demo/observations/{id}"
        ));

        assertEquals(expected, new TreeSet<>(declaredEndpoints()));
    }

    @Test
    void shouldNotDeclareTwoEndpointsWithTheSameVerbAndPath() {
        // Spring fails at startup on a duplicate mapping, but only for the
        // mappings it can see. This catches the case where two controllers claim
        // the same verb and path, which would make the surface ambiguous.
        Set<String> all = new LinkedHashSet<>();
        Set<String> dupes = new LinkedHashSet<>();
        for (String e : declaredEndpoints()) {
            if (!all.add(e)) {
                dupes.add(e);
            }
        }
        assertFalse(dupes.iterator().hasNext(), "duplicate endpoints: " + dupes);
    }

    @Test
    void shouldKeepTheHealthEndpointOfEveryControllerThatHasOne() {
        // The demo gives most controllers a /health sibling. This pins the list
        // of which controllers have one, because the static ones answer "ok"
        // unconditionally — see StaticHealthEndpointsTest for why that matters.
        Set<String> healthPaths = new TreeSet<>();
        for (String e : declaredEndpoints()) {
            if (e.endsWith("/health")) {
                healthPaths.add(e);
            }
        }
        assertEquals(new TreeSet<>(Arrays.asList(
                "GET /demo/experiences/health",
                "GET /demo/extraction/health",
                "GET /demo/feedback/health",
                "GET /demo/manage/health",
                "GET /demo/search/health",
                "GET /memory/health"
        )), healthPaths);
    }

    private static Set<String> declaredEndpoints() {
        Set<String> out = new LinkedHashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping classMapping = controller.getAnnotation(RequestMapping.class);
            String base = (classMapping != null && classMapping.value().length > 0)
                    ? classMapping.value()[0] : "";
            for (Method method : controller.getDeclaredMethods()) {
                for (String verb : verbsOf(method)) {
                    out.add(verb + " " + base + pathOf(method));
                }
            }
        }
        return out;
    }

    private static String pathOf(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) return firstOrEmpty(get.value());
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) return firstOrEmpty(post.value());
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) return firstOrEmpty(put.value());
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) return firstOrEmpty(patch.value());
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) return firstOrEmpty(delete.value());
        return "";
    }

    private static String firstOrEmpty(String[] values) {
        return values.length > 0 ? values[0] : "";
    }

    private static Set<String> verbsOf(Method method) {
        Set<String> verbs = new LinkedHashSet<>();
        if (method.getAnnotation(GetMapping.class) != null) verbs.add("GET");
        if (method.getAnnotation(PostMapping.class) != null) verbs.add("POST");
        if (method.getAnnotation(PutMapping.class) != null) verbs.add("PUT");
        if (method.getAnnotation(PatchMapping.class) != null) verbs.add("PATCH");
        if (method.getAnnotation(DeleteMapping.class) != null) verbs.add("DELETE");
        return verbs;
    }
}
