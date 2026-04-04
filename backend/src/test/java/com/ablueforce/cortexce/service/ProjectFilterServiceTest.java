package com.ablueforce.cortexce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProjectFilterService.
 * Tests: shouldInclude, isUnsafeDirectory, loadPatterns, expandHomeDirectory edge cases.
 * Note: DEFAULT_EXCLUDES patterns use double-asterisk glob (e.g. patterns like dot-git paths) designed for relative paths.
 */
class ProjectFilterServiceTest {

    private ProjectFilterService filterService;

    @BeforeEach
    void setUp() {
        filterService = new ProjectFilterService();
    }

    // ===== shouldInclude: null / blank =====

    @Test
    void shouldInclude_nullPath_returnsFalse() {
        assertThat(filterService.shouldInclude(null)).isFalse();
    }

    @Test
    void shouldInclude_blankPath_isIncluded() {
        // Blank path: normalizedPath is blank string; exclude patterns won't match "" so returns true
        assertThat(filterService.shouldInclude("   ")).isTrue();
    }

    // ===== shouldInclude: relative paths with default excludes =====

    @Test
    void shouldInclude_relativeProjectFile_included() {
        // No custom patterns; defaults only exclude .git, node_modules, build etc.
        assertThat(filterService.shouldInclude("project/src/main.java")).isTrue();
        assertThat(filterService.shouldInclude("my-project/src/App.ts")).isTrue();
    }

    @Test
    void shouldInclude_relativeGitDir_excluded() {
        assertThat(filterService.shouldInclude("project/.git/HEAD")).isFalse();
        assertThat(filterService.shouldInclude(".git/config")).isFalse();
    }

    @Test
    void shouldInclude_relativeNodeModules_excluded() {
        assertThat(filterService.shouldInclude("project/node_modules/package/index.js")).isFalse();
        assertThat(filterService.shouldInclude("node_modules/lodash/index.js")).isFalse();
    }

    @Test
    void shouldInclude_relativeBuildDir_excluded() {
        assertThat(filterService.shouldInclude("project/build/classes/Main.class")).isFalse();
    }

    @Test
    void shouldInclude_relativeDistDir_excluded() {
        assertThat(filterService.shouldInclude("project/dist/app.js")).isFalse();
    }

    @Test
    void shouldInclude_relativePycache_excluded() {
        assertThat(filterService.shouldInclude("project/__pycache__/main.pyc")).isFalse();
    }

    @Test
    void shouldInclude_relativeIdeaDir_excluded() {
        assertThat(filterService.shouldInclude("project/.idea/workspace.xml")).isFalse();
    }

    @Test
    void shouldInclude_relativeVscodeDir_excluded() {
        assertThat(filterService.shouldInclude("project/.vscode/settings.json")).isFalse();
    }

    @Test
    void shouldInclude_relativeTargetDir_excluded() {
        assertThat(filterService.shouldInclude("project/target/classes/App.class")).isFalse();
    }

    // ===== shouldInclude: custom patterns =====

    @Test
    void shouldInclude_customExclude_respectsPattern() {
        filterService.loadPatterns(List.of(), List.of("**/build/**"));
        assertThat(filterService.shouldInclude("project/build/output.jar")).isFalse();
        assertThat(filterService.shouldInclude("project/src/main.java")).isTrue();
    }

    @Test
    void shouldInclude_customInclude_includesMatching() {
        filterService.loadPatterns(List.of("**/src/**"), List.of());
        assertThat(filterService.shouldInclude("project/src/main.java")).isTrue();
        assertThat(filterService.shouldInclude("project/docs/readme.md")).isFalse();
    }

    @Test
    void shouldInclude_includeAndExclude_excludeWins() {
        filterService.loadPatterns(List.of("**/src/**"), List.of("**/test/**"));
        assertThat(filterService.shouldInclude("project/src/Main.java")).isTrue();
        assertThat(filterService.shouldInclude("project/src/test/Main.java")).isFalse();
        assertThat(filterService.shouldInclude("project/src/test/util/Main.java")).isFalse();
    }

    @Test
    void shouldInclude_noMatchingInclude_returnsFalse() {
        filterService.loadPatterns(List.of("**/src/**/*.java"), List.of());
        assertThat(filterService.shouldInclude("project/docs/readme.md")).isFalse();
        assertThat(filterService.shouldInclude("project/src/Main.java")).isTrue();
    }

    // ===== shouldInclude: glob patterns =====

    @Test
    void shouldInclude_singleCharGlob_matches() {
        filterService.loadPatterns(List.of("**/te?.java"), List.of());
        // ? matches exactly one char; pattern = "te" + (1 char) + ".java"
        // project/tes.java: ** matches "project", "te?.java" matches "tes.java" (t-e + s = 1 char) -> TRUE
        assertThat(filterService.shouldInclude("project/tes.java")).isTrue();
        // project/test.java: "te?.java" needs exactly 1 char between "te" and ".java"; "test" has 4 -> FALSE
        assertThat(filterService.shouldInclude("project/test.java")).isFalse();
        // project/t1.java: no "te" prefix; pattern requires filename starting with "te" -> FALSE
        assertThat(filterService.shouldInclude("project/t1.java")).isFalse();
        // project/tea.java: ** matches "project", "te?.java" matches "tea.java" (t-e + a = 1 char) -> TRUE
        assertThat(filterService.shouldInclude("project/tea.java")).isTrue();
        // project/a.java: no "te" prefix -> FALSE
        assertThat(filterService.shouldInclude("project/a.java")).isFalse();
    }

    @Test
    void shouldInclude_wildcardGlob_matches() {
        filterService.loadPatterns(List.of("**/*.md"), List.of());
        assertThat(filterService.shouldInclude("project/README.md")).isTrue();
        assertThat(filterService.shouldInclude("docs/guide.md")).isTrue();
        assertThat(filterService.shouldInclude("project/src/Main.java")).isFalse();
    }

    // ===== isUnsafeDirectory: null / blank =====

    @Test
    void isUnsafeDirectory_nullPath_returnsFalse() {
        assertThat(filterService.isUnsafeDirectory(null)).isFalse();
    }

    @Test
    void isUnsafeDirectory_blankPath_returnsFalse() {
        // Blank path: exclude patterns won't match "" so returns false
        assertThat(filterService.isUnsafeDirectory("   ")).isFalse();
    }

    // ===== isUnsafeDirectory: relative paths =====

    @Test
    void isUnsafeDirectory_relativeGitDir_returnsTrue() {
        assertThat(filterService.isUnsafeDirectory(".git")).isTrue();
        assertThat(filterService.isUnsafeDirectory("project/.git")).isTrue();
        assertThat(filterService.isUnsafeDirectory("project/.git/objects")).isTrue();
    }

    @Test
    void isUnsafeDirectory_relativeNodeModules_returnsTrue() {
        assertThat(filterService.isUnsafeDirectory("project/node_modules")).isTrue();
    }

    @Test
    void isUnsafeDirectory_relativeBuildDir_returnsTrue() {
        assertThat(filterService.isUnsafeDirectory("project/build")).isTrue();
    }

    @Test
    void isUnsafeDirectory_relativeDistDir_returnsTrue() {
        assertThat(filterService.isUnsafeDirectory("project/dist")).isTrue();
    }

    @Test
    void isUnsafeDirectory_relativePycache_returnsTrue() {
        assertThat(filterService.isUnsafeDirectory("project/__pycache__")).isTrue();
    }

    @Test
    void isUnsafeDirectory_relativeIdeaDir_returnsTrue() {
        assertThat(filterService.isUnsafeDirectory("project/.idea")).isTrue();
    }

    @Test
    void isUnsafeDirectory_relativeVscodeDir_returnsTrue() {
        assertThat(filterService.isUnsafeDirectory("project/.vscode")).isTrue();
    }

    @Test
    void isUnsafeDirectory_relativeTargetDir_returnsTrue() {
        assertThat(filterService.isUnsafeDirectory("project/target")).isTrue();
    }

    @Test
    void isUnsafeDirectory_regularSrcDir_returnsFalse() {
        assertThat(filterService.isUnsafeDirectory("project/src")).isFalse();
        assertThat(filterService.isUnsafeDirectory("src")).isFalse();
    }

    @Test
    void isUnsafeDirectory_docsDir_returnsFalse() {
        assertThat(filterService.isUnsafeDirectory("project/docs")).isFalse();
    }

    // ===== loadPatterns tests =====

    @Test
    void loadPatterns_nullIncludesAndExcludes_usesDefaults() {
        filterService.loadPatterns(null, null);
        // Defaults should still be active
        assertThat(filterService.isUnsafeDirectory(".git")).isTrue();
        assertThat(filterService.shouldInclude("project/src/main.java")).isTrue();
    }

    @Test
    void loadPatterns_customIncludesAndExcludes() {
        filterService.loadPatterns(List.of("**/myapp/**"), List.of("**/logs/**"));
        assertThat(filterService.shouldInclude("user/myapp/main.java")).isTrue();
        assertThat(filterService.shouldInclude("user/myapp/logs/app.log")).isFalse();
    }

    @Test
    void loadPatterns_clearsPreviousPatterns() {
        filterService.loadPatterns(List.of("**/keep/**"), List.of("**/skip/**"));
        assertThat(filterService.shouldInclude("project/keep/file.java")).isTrue();
        assertThat(filterService.shouldInclude("project/skip/file.java")).isFalse();

        // Reload with different patterns - old patterns are cleared
        filterService.loadPatterns(List.of("**/new/**"), List.of("**/old/**"));
        // project/keep no longer matches the new include pattern
        assertThat(filterService.shouldInclude("project/keep/file.java")).isFalse();
        // project/new matches new include pattern
        assertThat(filterService.shouldInclude("project/new/file.java")).isTrue();
        // project/skip is no longer excluded (old exclude cleared), but also not included
        // (since no include pattern matches it) -> falls back to false (includePatterns is non-empty)
        assertThat(filterService.shouldInclude("project/skip/file.java")).isFalse();
    }
}
