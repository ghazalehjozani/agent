package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FileSystemSourceTextProvider implements SourceTextProvider {

    @Override
    public String read(JavaFile javaFile) {
        if (javaFile == null || javaFile.path() == null || javaFile.path().isBlank()) {
            return "";
        }
        try {
            return Files.readString(Path.of(javaFile.path()));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Java source: " + javaFile.path(), exception);
        }
    }

    @Override
    public Map<String, String> readAll(PackageNode project) {
        Map<String, String> result = new LinkedHashMap<>();
        collect(project, result);
        return Map.copyOf(result);
    }

    private void collect(PackageNode node, Map<String, String> target) {
        if (node == null) {
            return;
        }
        for (JavaFile javaFile : node.javaFiles()) {
            target.put(javaFile.path(), read(javaFile));
        }
        node.packages().forEach(child -> collect(child, target));
    }
}
