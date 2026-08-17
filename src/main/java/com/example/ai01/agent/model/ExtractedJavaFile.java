package com.example.ai01.agent.model;

public record ExtractedJavaFile(
        String fileName,
        String path,
        String extension,
        long size,
        long lineCount
) {
}
