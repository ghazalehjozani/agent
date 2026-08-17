package com.example.ai01.agent.model;

import java.util.List;
import java.util.Map;

public record ExtractedResult(List<ExtractedJavaFile> extractedJavaFiles, Map<String, String> rules) {
}