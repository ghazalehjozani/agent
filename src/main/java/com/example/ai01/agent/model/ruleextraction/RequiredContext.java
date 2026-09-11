package com.example.ai01.agent.model.ruleextraction;

public enum RequiredContext {

    PROJECT,

    PACKAGES,

    CLASSES,

    CLASS_TYPE,

    IMPORTS,

    DEPENDENCIES,

    INTERFACES,

    SUPER_CLASS,

    ANNOTATIONS,

    FIELDS,

    CONSTRUCTORS,

    METHODS,

    METHOD_PARAMETERS,

    METHOD_RETURN_TYPES,

    METHOD_CALLS,

    // Build / dependencies
    BUILD_DEPENDENCIES,

    // Configuration
    APPLICATION_CONFIGURATION,

    // API
    API_ENDPOINTS,

    HTTP_METHODS,

    REQUEST_HEADERS,

    RESPONSE_HEADERS,

    REQUEST_BODIES,

    RESPONSE_BODIES,

    QUERY_PARAMETERS,

    // Messaging
    MESSAGE_CHANNELS,

    MESSAGE_HEADERS,

    MESSAGE_BODIES,

    // Infrastructure concerns
    LOGGING_CONFIGURATION,

    SECURITY_CONFIGURATION,

    // Documentation
    DOCUMENTATION
}