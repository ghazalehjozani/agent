package com.example.ai01.agent.model.ruleextraction;

import dev.langchain4j.model.output.structured.Description;

import java.util.Set;

@Description("""
        Semantic metadata derived only from the meaning of one architecture rule.

        This metadata is used for:
        - semantic retrieval,
        - applicability detection,
        - rule grouping,
        - selecting relevant project context,
        - and reducing irrelevant context sent to rule-checking agents.

        General rules:
        - Never invent information absent from the original architecture rule.
        - Enum-backed fields MUST contain only values supported by their Java enum.
        - Never invent new enum values.
        - Return an empty collection when a collection field is not applicable.
        - Keep project elements, artifacts, context and technologies conceptually separate.
        """)
public record RuleSemanticMetadata(

        // =========================================================
        // Scope
        // =========================================================

        @Description("""
                Smallest meaningful architectural scope at which
                the rule can be evaluated.

                MUST be one of the RuleScope enum values.
                Never invent a new scope.

                Examples may include:
                PROJECT,
                PACKAGE,
                CLASS,
                METHOD,
                DEPENDENCY,
                API,
                ENDPOINT,
                MESSAGE,
                CHANNEL.
                """)
        RuleScope scope,


        // =========================================================
        // Elements
        // =========================================================

        @Description("""
                Project element types to which this rule applies.

                IMPORTANT:
                Every value MUST be a valid ProjectElementType enum value.
                Never invent a ProjectElementType.

                Examples may include:
                PROJECT,
                API,
                ENDPOINT,
                CONTROLLER,
                SERVICE,
                REPOSITORY,
                CLASS,
                METHOD,
                REQUEST,
                RESPONSE,
                HEADER,
                BODY,
                MESSAGE,
                QUEUE,
                TOPIC.

                This field describes project/software elements only.

                DO NOT place artifact or context concepts here.

                In particular:
                - DOCUMENTATION is NOT a ProjectElementType.
                - BUILD_FILE is NOT a ProjectElementType.
                - SECURITY_CONFIG is NOT a ProjectElementType.
                - LOGGING_CONFIG is NOT a ProjectElementType.

                If a rule requires documentation:
                - use the actual affected element such as API, PROJECT,
                  ENDPOINT, REQUEST or RESPONSE in appliesTo;
                - put DOCUMENTATION in requiredArtifacts when appropriate.

                Return an empty set if no affected project element
                can be determined from the rule.
                """)
        Set<ProjectElementType> appliesTo,


        @Description("""
                Source-side project elements of a directional relationship.

                Every value MUST be a valid ProjectElementType enum value.
                Never invent values.

                Example:
                "controller may only call service"

                sourceElements = [CONTROLLER]

                Use only for directional rules.
                Return an empty set for non-directional rules.
                """)
        Set<ProjectElementType> sourceElements,


        @Description("""
                Target-side project elements of a directional relationship.

                Every value MUST be a valid ProjectElementType enum value.
                Never invent values.

                Example:
                "controller may only call service"

                targetElements = [SERVICE]

                Use only for directional rules.
                Return an empty set for non-directional rules.
                """)
        Set<ProjectElementType> targetElements,


        // =========================================================
        // Layers
        // =========================================================

        @Description("""
                Architectural layers from which a dependency,
                access, invocation or relationship originates.

                Use short canonical uppercase values.

                Examples:
                CONTROLLER,
                PRESENTATION,
                APPLICATION,
                SERVICE,
                DOMAIN,
                INFRASTRUCTURE,
                PERSISTENCE.

                Include a layer only when explicitly stated or
                strongly implied by the original rule.

                Return an empty set when no source layer is defined.
                """)
        Set<String> sourceLayers,


        @Description("""
                Architectural layers targeted by a dependency,
                access, invocation or relationship.

                Use short canonical uppercase values.

                Examples:
                SERVICE,
                APPLICATION,
                DOMAIN,
                INFRASTRUCTURE,
                PERSISTENCE.

                Include a layer only when explicitly stated or
                strongly implied by the original rule.

                Return an empty set when no target layer is defined.
                """)
        Set<String> targetLayers,


        // =========================================================
        // Relations
        // =========================================================

        @Description("""
                Relationships needed to evaluate the rule.

                Every value MUST be a valid RelationType enum value.
                Never invent relationship values.

                Examples may include:
                IMPORTS,
                DEPENDS_ON,
                CALLS,
                EXTENDS,
                IMPLEMENTS,
                INJECTS,
                ANNOTATED_WITH,
                PUBLISHES,
                CONSUMES,
                PROPAGATES,
                VALIDATES.

                Return an empty set if no relationship is relevant.
                """)
        Set<RelationType> relations,


        // =========================================================
        // Required context
        // =========================================================

        @Description("""
                Logical project information required to evaluate
                the architecture rule.

                Every value MUST be a valid RequiredContext enum value.
                Never invent context values.

                Include only context actually required for evaluation.

                This field is used to determine which parts of the
                extracted project structure should be provided to
                the rule-checking agent.

                Return an empty set when no specific extracted
                project context is required.
                """)
        Set<RequiredContext> requiredContext,


        @Description("""
                Physical artifacts that may need to be inspected
                to evaluate this rule.

                Artifacts are NOT ProjectElementType values.

                Use short canonical UPPER_SNAKE_CASE names.

                Preferred values:
                SOURCE_CODE,
                BUILD_FILE,
                APPLICATION_CONFIG,
                OPENAPI_SPEC,
                SECURITY_CONFIG,
                LOGGING_CONFIG,
                MESSAGE_BROKER_CONFIG,
                DOCUMENTATION.

                Example:
                A rule requiring API documentation should normally use:
                appliesTo = [API]
                requiredArtifacts = [DOCUMENTATION]

                Return an empty set when no external artifact
                needs to be inspected.
                """)
        Set<String> requiredArtifacts,


        // =========================================================
        // Language / technology
        // =========================================================

        @Description("""
                Programming languages to which the rule applies.

                Use canonical uppercase names.

                Examples:
                JAVA,
                KOTLIN,
                CSHARP,
                PYTHON,
                JAVASCRIPT,
                TYPESCRIPT,
                ANY.

                Use ANY only when the rule is truly
                language-independent.

                Do not infer a language merely from the current
                application's implementation language.
                """)
        Set<String> languages,


        @Description("""
                Technologies, frameworks, libraries or platforms
                explicitly mentioned or strongly implied by the rule.

                Examples:
                Spring,
                Spring Boot,
                Spring MVC,
                Spring Security,
                JPA,
                Hibernate,
                OpenTelemetry,
                Kafka,
                OAuth2,
                OpenAPI.

                Do not infer Spring merely because the rule mentions
                controllers, services or repositories.

                Return an empty set when technology-independent.
                """)
        Set<String> technologies,


        // =========================================================
        // Protocols and standards
        // =========================================================

        @Description("""
                Communication protocols or interaction mechanisms
                relevant to the rule.

                Use canonical uppercase values.

                Examples:
                HTTP,
                REST,
                GRPC,
                SOAP,
                ASYNC_MESSAGE,
                PUB_SUB,
                POINT_TO_POINT.

                Do not invent protocols.

                Return an empty set when not applicable.
                """)
        Set<String> protocols,


        @Description("""
                External technical standards or specifications
                explicitly relevant to the rule.

                Use canonical identifiers.

                Examples:
                W3C_TRACE_CONTEXT,
                ISO_8601,
                RFC_3339,
                RFC_5545,
                BCP_47,
                OAUTH_2,
                OPENAPI.

                Do not infer standards without evidence from the rule.

                Return an empty set when none applies.
                """)
        Set<String> standards,


        // =========================================================
        // Communication
        // =========================================================

        @Description("""
                Communication context in which the rule applies.

                Use short canonical UPPER_SNAKE_CASE values.

                Examples:
                INBOUND,
                OUTBOUND,
                INTER_SERVICE,
                INTRA_SERVICE,
                INTRA_DOMAIN,
                CROSS_DOMAIN,
                SYNCHRONOUS,
                ASYNCHRONOUS.

                Return an empty set when communication context
                is irrelevant.
                """)
        Set<String> communicationContexts,


        // =========================================================
        // HTTP
        // =========================================================

        @Description("""
                Specific HTTP methods to which the rule applies.

                Allowed canonical values:
                GET,
                POST,
                PUT,
                PATCH,
                DELETE.

                Return an empty set when:
                - the rule is not HTTP-related, or
                - the rule applies to all HTTP methods.
                """)
        Set<String> httpMethods,


        @Description("""
                HTTP components directly involved in the rule.

                Preferred canonical values:
                URL,
                PATH,
                QUERY_PARAMETER,
                REQUEST_HEADER,
                RESPONSE_HEADER,
                REQUEST_BODY,
                RESPONSE_BODY,
                STATUS_CODE.

                Do not put generic project elements here.

                Return an empty set for non-HTTP rules.
                """)
        Set<String> httpComponents,


        // =========================================================
        // Messaging
        // =========================================================

        @Description("""
                Asynchronous messaging elements involved in the rule.

                Preferred canonical values:
                MESSAGE,
                MESSAGE_HEADER,
                MESSAGE_BODY,
                EVENT,
                COMMAND,
                TOPIC,
                QUEUE,
                DLT.

                Return an empty set when asynchronous messaging
                is irrelevant.
                """)
        Set<String> messagingElements,


        // =========================================================
        // Environment
        // =========================================================

        @Description("""
                Runtime environments specifically relevant to the rule.

                Preferred canonical values:
                DEVELOPMENT,
                TEST,
                STAGING,
                PRODUCTION,
                ALL.

                Do not add ALL automatically.
                Use ALL only when the rule explicitly applies
                across all runtime environments.

                Return an empty set when environment-specific
                information is irrelevant.
                """)
        Set<String> environments,


        // =========================================================
        // Applicability
        // =========================================================

        @Description("""
                Indicates whether the rule is always applicable
                within its scope or requires additional conditions.

                MUST be one of the RuleApplicability enum values.

                ALWAYS:
                no extra applicability condition is required.

                CONDITIONAL:
                applicabilityConditions must contain the conditions.

                Never invent another applicability value.
                """)
        RuleApplicability applicability,


        @Description("""
                Conditions that determine whether this rule
                is applicable.

                Use concise UPPER_SNAKE_CASE expressions.

                Examples:
                SPRING_MVC_IS_USED,
                REST_IS_USED,
                GRPC_IS_USED,
                ASYNC_COMMUNICATION_IS_USED,
                ENDPOINT_HAS_BODY,
                PAGINATION_IS_USED,
                IDEMPOTENCY_IS_ENABLED,
                PROJECTION_IS_SUPPORTED.

                These values describe applicability only.

                They MUST NOT introduce additional architecture
                requirements.

                Return an empty set when applicability is ALWAYS.
                """)
        Set<String> applicabilityConditions,


        // =========================================================
        // Semantic grouping
        // =========================================================

        @Description("""
                Broad architectural concern represented by the rule.

                Use one short, stable UPPER_SNAKE_CASE value.

                Preferred values:
                LAYERING,
                NAMING,
                DEPENDENCY_INJECTION,
                PERSISTENCE,
                SECURITY,
                OBSERVABILITY,
                ERROR_HANDLING,
                REST_DESIGN,
                IDEMPOTENCY,
                DATA_FORMATTING,
                ASYNC_MESSAGING,
                RESILIENCE,
                BACKWARD_COMPATIBILITY,
                GOVERNANCE.

                Choose the closest semantic concern.
                Do not create unnecessarily specific values.
                """)
        String concern,


        @Description("""
                More specific semantic group for strongly related rules.

                Use a stable UPPER_SNAKE_CASE value.

                Examples:
                DEPENDENCY_DIRECTION,
                NAMING_CONVENTION,
                GLOBAL_EXCEPTION_HANDLING,
                DISTRIBUTED_TRACING,
                AUTHENTICATION,
                AUTHORIZATION,
                TOKEN_VALIDATION,
                IDEMPOTENCY_PROCESSING,
                URL_DESIGN,
                REQUEST_RESPONSE_SCHEMA,
                HTTP_STATUS_CODES,
                CHANNEL_NAMING,
                DATE_TIME_FORMATTING.

                Rules sharing a semanticGroup should normally
                be meaningful when retrieved or evaluated together.
                """)
        String semanticGroup,


        // =========================================================
        // Semantic retrieval
        // =========================================================

        @Description("""
                Short semantic concepts describing the meaning
                of the rule.

                Examples:
                layer isolation,
                dependency direction,
                constructor injection,
                trace propagation,
                token validation,
                idempotent request,
                naming convention.

                Keep each concept short.
                Do not copy the whole rule description.
                """)
        Set<String> concepts,


        @Description("""
                Concrete technical terms useful for lexical and
                semantic retrieval.

                Prefer terms directly appearing in the rule.

                Examples:
                ControllerAdvice,
                Autowired,
                Entity,
                Table,
                traceparent,
                Authorization,
                Bearer,
                Idempotency-Key,
                OpenTelemetry.

                Avoid generic words such as:
                system,
                code,
                application,
                implementation
                unless they are genuinely meaningful for retrieval.
                """)
        Set<String> keywords,


        @Description("""
                Concise, self-contained summary optimized for
                embedding-based semantic retrieval.

                It should state:
                - what condition must be checked,
                - which elements are involved,
                - what restriction or relationship exists,
                - and relevant technology/protocol context when needed.

                Do not introduce requirements absent from
                the original architecture rule.

                Prefer one concise sentence.
                """)
        String semanticSummary

) {

        public RuleSemanticMetadata {

                appliesTo = safe(appliesTo);

                sourceElements = safe(sourceElements);
                targetElements = safe(targetElements);

                sourceLayers = safe(sourceLayers);
                targetLayers = safe(targetLayers);

                relations = safe(relations);

                requiredContext = safe(requiredContext);
                requiredArtifacts = safe(requiredArtifacts);

                languages = safe(languages);
                technologies = safe(technologies);

                protocols = safe(protocols);
                standards = safe(standards);

                communicationContexts = safe(communicationContexts);

                httpMethods = safe(httpMethods);
                httpComponents = safe(httpComponents);

                messagingElements = safe(messagingElements);

                environments = safe(environments);

                applicabilityConditions = safe(applicabilityConditions);

                concepts = safe(concepts);
                keywords = safe(keywords);
        }

        private static <T> Set<T> safe(Set<T> values) {

                return values == null
                        ? Set.of()
                        : Set.copyOf(values);
        }
}