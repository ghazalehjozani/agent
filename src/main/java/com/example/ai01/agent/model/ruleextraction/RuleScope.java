package com.example.ai01.agent.model.ruleextraction;

public enum RuleScope {

    // Whole project structure
    PROJECT,
    MODULE,
    PACKAGE,

    // Java types
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,

    // Architectural components
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    COMPONENT,
    ENTITY,

    // Java members
    METHOD,
    CONSTRUCTOR,
    FIELD,
    ANNOTATION,

    // Dependencies
    DEPENDENCY,

    // API
    API,
    ENDPOINT,
    REQUEST,
    RESPONSE,

    // HTTP parts
    HEADER,
    BODY,
    QUERY_PARAMETER,

    // Messaging
    MESSAGE,
    CHANNEL,
    TOPIC,
    QUEUE,

    // Configuration / specification
    CONFIGURATION,
    DOCUMENTATION,
    SCHEMA,
    OPENAPI_SPEC,

    // Fallback
    UNKNOWN
}