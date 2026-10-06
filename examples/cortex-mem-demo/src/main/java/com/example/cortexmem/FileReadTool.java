package com.example.cortexmem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * File read tool — separate component so @Tool AOP intercepts.
 *
 * <p>Must NOT be in the same class as the caller: Spring AOP does not
 * intercept self-invocation. Injecting this bean ensures the proxy is used.
 *
 * <p><b>Reads are confined to {@link DemoProperties#getFileReadRoot()}</b>
 * (default: the process working directory). Without that confinement this
 * tool is an arbitrary-file read: every caller — the two controllers that
 * take a {@code ?path=} parameter and the model itself, via {@code @Tool} —
 * could read {@code ~/.ssh/id_rsa}, {@code ~/.aws/credentials} or any
 * {@code .env} the JVM can reach. Recorded as P1-2.
 */
@Component
public class FileReadTool {

    private static final Logger log = LoggerFactory.getLogger(FileReadTool.class);

    private final Path root;

    public FileReadTool(DemoProperties demoProperties) {
        this.root = Path.of(demoProperties.getFileReadRoot()).toAbsolutePath().normalize();
        log.info("FileReadTool confined to {}", root);
    }

    @Tool(description = "Read the contents of a file inside the project root", name = "readFile")
    public String readFile(String path) {
        Path resolved = resolveWithinRoot(path);
        if (resolved == null) {
            return "Error: path is outside the allowed root " + root;
        }
        try {
            return Files.readString(resolved);
        } catch (Exception e) {
            log.warn("Failed to read file: {}", resolved, e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Resolve {@code path} to an absolute, normalized location inside the root,
     * or return null when it escapes. A relative path is taken against the root;
     * an absolute path is accepted only if it is already inside the root.
     *
     * <p>Two checks, and neither replaces the other: {@code normalize()} strips
     * {@code ../} lexically but does not follow symlinks, so a link placed
     * inside the root could still point out of it; {@code toRealPath()} resolves
     * links but throws when the target does not exist, which is the ordinary
     * case for a path the caller got wrong.
     */
    private Path resolveWithinRoot(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path candidate;
        try {
            candidate = root.resolve(path).normalize();
        } catch (InvalidPathException e) {
            return null;
        }
        if (!candidate.startsWith(root)) {
            log.warn("Blocked read outside root {}: {}", root, path);
            return null;
        }
        if (Files.exists(candidate)) {
            try {
                Path real = candidate.toRealPath();
                if (!real.startsWith(root.toRealPath())) {
                    log.warn("Blocked symlink escape {} -> {}", candidate, real);
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
        }
        return candidate;
    }
}
