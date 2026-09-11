package com.example.ai01.tools;

import com.example.ai01.monitoring.TraceOperation;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ReadFileTool {
    private final Path workspaceRoot;

    public ReadFileTool(
            @Value("${app.workspace.root-path}") String workspaceRoot
    ) {
        this.workspaceRoot = Path.of(workspaceRoot)
                .toAbsolutePath()
                .normalize();
    }

    @Tool("""
            Reads the source code of exactly one Java file.

            The argument must be the exact absolute path taken from the `path`
            field of a JavaFile in PROJECT STRUCTURE.

            Never invent, shorten, modify, or convert the path.
            """)
    public String readFile(
            @P("""
                    Exact absolute path from the JavaFile.path field
                    in PROJECT STRUCTURE.
                    Example:
                    /home/temp_files/ai-agent/src/main/java/com/example/Test.java
                    """)
            String path
    ) {

        try {

            Path requestedPath = Path.of(path)
                    .toAbsolutePath()
                    .normalize();

            /*if (!requestedPath.startsWith(workspaceRoot)) {
                return "ERROR: access outside workspace is not allowed: " + path;
            }*/

            if (!Files.exists(requestedPath)) {
                return "ERROR: file not found: " + path;
            }

            if (!Files.isRegularFile(requestedPath)) {
                return "ERROR: path is not a regular file: " + path;
            }

            if (!requestedPath.toString().endsWith(".java")) {
                return "ERROR: only Java source files can be read: " + path;
            }

            return Files.readString(
                    requestedPath,
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {

            return "ERROR: could not read file '"
                    + path
                    + "': "
                    + e.getMessage();

        } catch (RuntimeException e) {

            return "ERROR: invalid file path '"
                    + path
                    + "': "
                    + e.getMessage();
        }
    }
}