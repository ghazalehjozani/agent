package com.example.ai01.agent.model;

import java.util.List;

public record Constructor(AccessModifier accessModifier,
                          List<Annotation> annotations,
                          List<Parameter> parameters) {
}