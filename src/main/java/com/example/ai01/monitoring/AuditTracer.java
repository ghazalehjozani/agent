package com.example.ai01.monitoring;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

@Component
public class AuditTracer {
    private final Tracer tracer;
    public AuditTracer( OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry
                .getTracer("com.example.ai01.architecture-audit");
    }
    public Tracer getTracer() {
        return tracer;
    }
}
