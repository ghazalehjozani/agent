package com.example.ai01.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ReadFileTool {

    //TODO get root dir for invalid access errors
    public ReadFileTool() {
    }
    @Tool("""
            Reads and returns the full text content of a single file given its path.
            Use this when you need to inspect the actual source code of a file to
            verify content-based architectural rules (dependencies, injection style,
            forbidden imports, mandatory patterns, etc.).
            Call this one file at a time. The path must be one of the paths provided
            in the extracted file list.
            """)
    public String readFile(@P("The relative path of the file to read") String path) {
        try {
            var noramlizedPath = Path.of(path);
            if (!Files.exists(noramlizedPath) || !Files.isRegularFile(noramlizedPath)) {
                return "ERROR: file not found: " + path;
            }
            return Files.readString(noramlizedPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "ERROR: could not read file '" + path + "': " + e.getMessage();
        }
    }

}