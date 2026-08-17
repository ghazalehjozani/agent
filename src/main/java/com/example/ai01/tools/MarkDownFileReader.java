package com.example.ai01.tools;

import com.example.ai01.configuration.PathProperties;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class MarkDownFileReader {
    private final PathProperties pathProperties;

    public MarkDownFileReader(PathProperties pathProperties) {
        this.pathProperties = pathProperties;
    }

    @Tool("Reads the content of a local Markdown (.md) file containing architectural rules.")
    public String readMarkdownFile(
            @P("Markdown file path. Use the exact argument name filePath.") String filePath
    ) {
        Path basePath = Paths.get(pathProperties.getRootPath()).toAbsolutePath().normalize();
        Path targetPath = Paths.get(filePath).toAbsolutePath().normalize();

        if (!targetPath.startsWith(basePath)) {
            targetPath = basePath.resolve(filePath).toAbsolutePath().normalize();
        }

        if (!Files.exists(targetPath)) {
            throw new IllegalArgumentException("The markdown file does not exist: " + filePath);
        }

        if (!Files.isRegularFile(targetPath)) {
            throw new IllegalArgumentException("The path does not point to a regular file: " + filePath);
        }

        String fileName = targetPath.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".md") && !fileName.endsWith(".markdown")) {
            throw new IllegalArgumentException("Invalid file format. Only Markdown (.md or .markdown) files are allowed.");
        }

        try {

            byte[] bytes = Files.readAllBytes(targetPath);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the markdown file: " + e.getMessage(), e);
        }
    }

}