package com.example.ai01.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProjectAnalysisTools {

    @Tool(value = {"", "", ""}, name = "listAllJavaFiles")
    public List<String> listJavaFiles(String projectPath) throws IOException {
        Path path = Paths.get(projectPath);
        try (var stream = Files.walk(path)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    @Tool(value = {"", "", ""}, name = "readAllFileContents")
    public String readFileContent(String filePath) throws IOException {
        return Files.readString(Paths.get(filePath));
    }
}
