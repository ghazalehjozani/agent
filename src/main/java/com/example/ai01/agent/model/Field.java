package com.example.ai01.agent.model;

import java.util.List;

public record Field(List<Annotation> annotations, AccessModifier accessModifier, String type, String name) {
}