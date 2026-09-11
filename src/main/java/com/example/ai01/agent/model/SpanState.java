package com.example.ai01.agent.model;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

public record SpanState(
        Span span,
        Scope scope) {
}