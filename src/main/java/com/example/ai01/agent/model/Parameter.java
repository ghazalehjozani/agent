package com.example.ai01.agent.model;

import java.util.List;

public record Parameter(
        String type,
        String name,
        List<Annotation> annotations
) {
    public Parameter {
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
    }

    public Parameter(String type, String name) {
        this(type, name, List.of());
    }
}
