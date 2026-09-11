package com.example.ai01.agent.model.ruleextraction;

public enum RelationType {

    // Structural
    IMPORTS,
    DEPENDS_ON,
    CONTAINS,
    REFERENCES,

    // Invocation
    CALLS,
    USES,

    // Type relationships
    EXTENDS,
    IMPLEMENTS,

    // Dependency injection
    INJECTS,

    // Annotation
    ANNOTATED_WITH,

    // Method contract
    RETURNS,
    ACCEPTS_PARAMETER,

    // API
    EXPOSES,

    // Messaging
    PUBLISHES,
    CONSUMES,

    // Distributed communication
    PROPAGATES,

    // Validation / security
    VALIDATES,

    // Data access
    READS,
    WRITES,

    SENDS
}