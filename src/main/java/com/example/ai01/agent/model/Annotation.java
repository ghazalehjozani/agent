package com.example.ai01.agent.model;

import java.util.Map;

public record Annotation(String name,
                         Map<String, String> attributes) {
}