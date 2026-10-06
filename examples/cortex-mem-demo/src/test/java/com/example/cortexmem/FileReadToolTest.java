package com.example.cortexmem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the file read confinement added for P1-2.
 *
 * <p>Before it, {@code FileReadTool.readFile} called {@code Files.readString(Path.of(path))}
 * with no root confinement, so every caller — the two controllers taking a
 * {@code ?path=} parameter and the model itself through {@code @Tool} — could read
 * any file the JVM could reach, and the endpoint returned the contents verbatim.
 * The tool is now confined to {@code demo.file-read-root}.
 *
 * <p>What is pinned here is that the refusal happens <em>before</em> the read, and
 * that a lexical-only check is not enough: {@code ../} and an absolute path are
 * stripped by {@code normalize()}, but a symlink placed inside the root is not,
 * which is why {@code toRealPath()} is consulted as well.
 */
class FileReadToolTest {

    @TempDir
    Path root;

    private Path secretDir;

    @BeforeEach
    void setUp() throws IOException {
        secretDir = Files.createTempDirectory("outside-the-root");
        Files.writeString(secretDir.resolve("id_rsa"), "PRIVATE KEY");
        Files.writeString(root.resolve("hello.txt"), "hello from inside");
        Files.createDirectory(root.resolve("sub"));
        Files.writeString(root.resolve("sub").resolve("nested.txt"), "nested");
    }

    private FileReadTool tool() {
        DemoProperties props = new DemoProperties();
        props.setFileReadRoot(root.toString());
        return new FileReadTool(props);
    }

    @Test
    void readsFileInsideRoot() {
        assertEquals("hello from inside", tool().readFile("hello.txt"));
    }

    @Test
    void readsFileNamedByAbsolutePathWhenItIsInsideRoot() {
        assertEquals("nested", tool().readFile(root.resolve("sub/nested.txt").toString()));
    }

    @Test
    void refusesParentTraversal() {
        String out = tool().readFile("../" + secretDir.getFileName() + "/id_rsa");
        assertTrue(out.startsWith("Error: path is outside the allowed root"), out);
        assertTrue(!out.contains("PRIVATE KEY"), "拒绝时不得回显内容");
    }

    @Test
    void refusesAbsolutePathOutsideRoot() {
        String out = tool().readFile(secretDir.resolve("id_rsa").toString());
        assertTrue(out.startsWith("Error: path is outside the allowed root"), out);
    }

    @Test
    void refusesSecretFileOutsideRootThatTheUserOwns() {
        // The exact case P1-2 was filed for: readable by the JVM, not by the sandbox.
        String out = tool().readFile(secretDir.toString());
        assertTrue(out.startsWith("Error: path is outside the allowed root"), out);
    }

    @Test
    void refusesSymlinkPointingOutsideRoot() throws IOException {
        Path link = root.resolve("escape");
        try {
            Files.createSymbolicLink(link, secretDir.resolve("id_rsa"));
        } catch (UnsupportedOperationException | IOException e) {
            return; // 平台不支持符号链接，跳过而不是误报通过
        }
        String out = tool().readFile("escape");
        assertTrue(out.startsWith("Error: path is outside the allowed root"), out);
        assertTrue(!out.contains("PRIVATE KEY"), "符号链接逃逸必须被拦下");
    }

    @Test
    void refusesBlankPath() {
        assertTrue(tool().readFile("").startsWith("Error: path is outside the allowed root"));
        assertTrue(tool().readFile("   ").startsWith("Error: path is outside the allowed root"));
        assertTrue(tool().readFile(null).startsWith("Error: path is outside the allowed root"));
    }

    @Test
    void reportsMissingFileInsideRootAsAReadError() {
        // Inside the root but absent: still a read error, not a confinement error.
        String out = tool().readFile("nope.txt");
        assertTrue(!out.startsWith("Error: path is outside the allowed root"), out);
    }
}
