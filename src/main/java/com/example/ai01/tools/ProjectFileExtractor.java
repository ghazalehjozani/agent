package com.example.ai01.tools;


import com.example.ai01.agent.model.ExtractedJavaFile;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ProjectFileExtractor {

    @Tool("Reads all Java files from a local directory and returns their name, absolute path, and content.")
    public List<ExtractedJavaFile> extract(
            @P("The absolute path of the root directory of the Java project") String path
    ) {
        List<ExtractedJavaFile> extractedFiles = new ArrayList<>();
        Path rootPath = Paths.get(path);

        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("The provided path is invalid or is not a directory: " + path);
        }

        try (Stream<Path> paths = Files.walk(rootPath)) {
            extractedFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map((p) -> {
                        long[] data = new long[0];
                        try {
                            data = getLineCountAndSize(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        var name = p.getFileName().toString();
                                return new ExtractedJavaFile(
                                        name,
                                        p.toAbsolutePath().toString(),
                                        getExtensions(name),
                                        data[1],
                                        data[0]
                                );
                            }
                    ).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Error traversing directory structure", e);
        }

        return extractedFiles;
    }

    private String getExtensions(String fileName) {
        return fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.'))
                : "";
    }

    /**
     * result[0] = lineCount
     * result[1] = sizeBytes
     */
    public static long[] getLineCountAndSize(Path path) throws IOException {
        long lineCount = 0;
        long size = 0;

        boolean hasAnyByte = false;
        boolean lastByteWasNewLine = false;

        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[8192];
            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                size += read;
                hasAnyByte = true;

                for (int i = 0; i < read; i++) {
                    if (buffer[i] == '\n') {
                        lineCount++;
                        lastByteWasNewLine = true;
                    } else {
                        lastByteWasNewLine = false;
                    }
                }
            }
        }

        if (hasAnyByte && !lastByteWasNewLine) {
            lineCount++;
        }

        return new long[]{lineCount, size};
    }
}
