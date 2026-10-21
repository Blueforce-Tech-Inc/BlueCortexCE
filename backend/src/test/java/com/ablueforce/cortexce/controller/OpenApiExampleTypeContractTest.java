package com.ablueforce.cortexce.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins one invariant of the machine-readable contract: <em>the JSON kind of a schema's
 * {@code example} must equal the type that schema declares</em>.
 *
 * <p>Background: an {@code @Schema} annotation that carries only {@code example} and no
 * {@code type} (and no {@code implementation}) is resolved by springdoc into a schema whose
 * type is {@code string}. The example itself is still emitted verbatim, so the published
 * document ended up asserting two contradictory things at once — {@code "type": "string"}
 * next to an object or array example. A client generated from such a document sends a JSON
 * string where the endpoint expects a JSON object, and the endpoint answers 400.
 *
 * <p>The sharpest instance was {@code PATCH /api/memory/observations/{id}}: its request body
 * was published as {@code type: string} while the example was a JSON object, and the
 * handler binds a {@code Map<String, Object>}. Sending the string the document describes
 * is answered with 400, while sending the object the example shows is answered with 200.
 *
 * <p>This test reads the annotations directly — no Spring context, no database. It walks
 * every {@code @RestController} in the controller package, collects every request-body and
 * response schema that carries an example, and asserts the declared type matches the
 * example's JSON kind.
 *
 * <p>Scope note: schemas that declare an {@code implementation} are skipped. springdoc
 * resolves those against the named type, so their published type is derived from the class
 * rather than from the annotation's own {@code type} attribute. The invariant this test
 * pins is the one that a bare {@code example} annotation can violate.
 */
class OpenApiExampleTypeContractTest {

    private static final String CONTROLLER_PACKAGE = "com.ablueforce.cortexce.controller";

    /** All schemas inspected during the last sweep — used to prove the sweep is not vacuous. */
    private static final List<String> INSPECTED = new ArrayList<>();

    @Test
    @DisplayName("every example's JSON kind matches the type its schema declares")
    void exampleKindMatchesDeclaredType() {
        List<String> violations = new ArrayList<>();
        INSPECTED.clear();

        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Schema schema : requestBodySchemas(method)) {
                    check(schema, controller, method, violations);
                }
                for (Schema schema : responseSchemas(method)) {
                    check(schema, controller, method, violations);
                }
            }
        }

        // Positive control: the sweep must actually have reached the schemas. Without this
        // a broken scanner would report "no violations" and the test would pass for the
        // wrong reason — the same failure mode as an empty diff proving nothing.
        assertTrue(INSPECTED.size() >= 40,
                "the sweep must reach the documented schemas, found " + INSPECTED.size());
        assertTrue(violations.isEmpty(),
                "schema examples contradict their declared type:\n  " + String.join("\n  ", violations));
    }

    private void check(Schema schema, Class<?> controller, Method method, List<String> violations) {
        String example = schema.example();
        if (example == null || example.isBlank()) {
            return;
        }
        if (schema.implementation() != Void.class) {
            // springdoc resolves the named type; the published type comes from that class.
            return;
        }
        String kind = jsonKind(example);
        String declared = schema.type() == null ? "" : schema.type();
        String effective = declared.isBlank() ? "string" : declared;

        INSPECTED.add(controller.getSimpleName() + "#" + method.getName());

        if (!kind.equals(effective)) {
            violations.add(controller.getSimpleName() + "#" + method.getName()
                    + " — example is a JSON " + kind + " but the schema declares type \"" + effective
                    + "\" (example: " + example + ")");
        }
    }

    private static String jsonKind(String example) {
        String trimmed = example.trim();
        if (trimmed.startsWith("{")) {
            return "object";
        }
        if (trimmed.startsWith("[")) {
            return "array";
        }
        return "string";
    }

    private static List<Schema> requestBodySchemas(Method method) {
        List<Schema> schemas = new ArrayList<>();
        io.swagger.v3.oas.annotations.parameters.RequestBody onMethod =
                method.getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);
        if (onMethod != null) {
            schemas.addAll(schemasOf(onMethod.content()));
        }
        for (java.lang.reflect.Parameter parameter : method.getParameters()) {
            io.swagger.v3.oas.annotations.parameters.RequestBody onParameter =
                    parameter.getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);
            if (onParameter != null) {
                schemas.addAll(schemasOf(onParameter.content()));
            }
        }
        return schemas;
    }

    private static List<Schema> responseSchemas(Method method) {
        List<Schema> schemas = new ArrayList<>();
        ApiResponses container = method.getAnnotation(ApiResponses.class);
        if (container != null) {
            for (ApiResponse response : container.value()) {
                schemas.addAll(schemasOf(response.content()));
            }
        }
        ApiResponse single = method.getAnnotation(ApiResponse.class);
        if (single != null) {
            schemas.addAll(schemasOf(single.content()));
        }
        return schemas;
    }

    private static List<Schema> schemasOf(Content[] contents) {
        List<Schema> schemas = new ArrayList<>();
        for (Content content : contents) {
            Schema schema = content.schema();
            if (schema != null) {
                schemas.add(schema);
            }
            // An array response carries its shape in Content.array() instead of
            // Content.schema(): @ArraySchema's own arraySchema() describes the array and
            // its schema() describes the items. Reading only Content.schema() would miss
            // every array-typed response, so both sides are collected here.
            io.swagger.v3.oas.annotations.media.ArraySchema array = content.array();
            if (array != null) {
                if (array.arraySchema() != null) {
                    schemas.add(array.arraySchema());
                }
                if (array.schema() != null) {
                    schemas.add(array.schema());
                }
            }
        }
        return schemas;
    }

    private static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            try {
                classes.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("cannot load " + definition.getBeanClassName(), e);
            }
        }
        assertTrue(classes.size() >= 10,
                "the controller package must be scannable, found " + classes.size() + " classes");
        return classes;
    }
}
