package com.example.ai01.agent.model.ruleextraction;

public enum ProjectElementType {

    // Project structure
    PROJECT,
    MODULE,
    PACKAGE,

    // Architectural components
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    REQUEST_BODY,
    ENTITY,
    CONFIGURATION,
    COMPONENT,

    // Java types
    INTERFACE,
    CLASS,
    ENUM,
    RECORD,
    ANNOTATION,

    // Java members
    CONSTRUCTOR,
    METHOD,
    FIELD,

    // Architecture
    DEPENDENCY,

    // API
    API,
    ENDPOINT,

    REQUEST,
    RESPONSE,

    HEADER,
    BODY,

    QUERY_PARAMETER,

    // Messaging
    MESSAGE,
    MESSAGE_HEADER,
    MESSAGE_BODY,

    CHANNEL,
    TOPIC,
    QUEUE,

    // Specifications
    OPENAPI_SPEC,

    UNKNOWN
}