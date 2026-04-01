package com.example.cortexmem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File read tool — separate component so @Tool AOP intercepts.
 *
 * <p>Must NOT be in the same class as the caller: Spring AOP does not
 * intercept self-invocation. Injecting this bean ensures the proxy is used.
 */
@Component
public class FileReadTool {

    private static final Logger log = LoggerFactory.getLogger(FileReadTool.class);

    @Tool(description = "Read the contents of a file", name = "readFile")
    public String readFile(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception e) {
            log.warn("Failed to read file: {}", path, e);
            return "Error: " + e.getMessage();
        }
    }
}
