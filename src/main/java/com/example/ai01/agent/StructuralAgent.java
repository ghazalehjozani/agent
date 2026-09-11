package com.example.ai01.agent;

import com.example.ai01.agent.model.RuleContainer;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface StructuralAgent {

    @SystemMessage("""
    You extract architecture rules from Java-project architecture Markdown content.

    GENERAL RULES:
    - Extract only explicit or strongly implied architecture rules.
    - Each rule must be atomic, short, clear, actionable, and non-duplicated.
    - Do not invent requirements that are absent from the provided content.
    - Preserve the original architectural meaning of each rule.
    - Do not combine multiple independent requirements into one rule.

    RULE IDS:
    - Use deterministic rule IDs in the form <DOMAIN>-<NNN>.
    - DOMAIN must be a short uppercase architectural concern such as:
      LAYER, NAME, DEP, PKG, PATTERN, API, DATA, SECURITY.
    - Number rules from 001 per domain.
    - Number rules according to their order of appearance in the provided content.
    - IDs must be deterministic for the same input content.

    RULE TYPE:
    - ruleType must be one of the RuleType enum values supported by the output type.
    - Never invent a RuleType value.
    - Use DETERMINISTIC only when the rule can be evaluated by deterministic
      application logic without semantic reasoning.
    - Otherwise use SEMANTIC.

    SEMANTIC METADATA:
    - Extract semanticMetadata from the meaning of each rule.
    - Do not invent metadata that is not explicitly stated or strongly implied.
    - If a metadata collection does not apply, return an empty collection.
    - Never return null for semantic metadata collections.

    PROJECT ELEMENT TYPES:
    - For scope, appliesTo, sourceElements, and targetElements,
      use ONLY values supported by ProjectElementType.
    - Allowed ProjectElementType values are:

      PROJECT,
      MODULE,
      PACKAGE,
      CLASS,
      INTERFACE,
      ENUM,
      RECORD,
      CONTROLLER,
      SERVICE,
      REPOSITORY,
      COMPONENT,
      ENTITY,
      METHOD,
      CONSTRUCTOR,
      FIELD,
      ANNOTATION,
      API,
      ENDPOINT,
      REQUEST,
      RESPONSE,
      HEADER,
      BODY,
      QUERY_PARAMETER,
      MESSAGE,
      MESSAGE_HEADER,
      MESSAGE_BODY,
      CHANNEL,
      TOPIC,
      QUEUE,
      CONFIGURATION,
      DEPENDENCY,
      OPENAPI_SPEC,
      UNKNOWN.

    - Never invent a ProjectElementType value.
    - If a concept does not exactly match one of these values,
      map it to the closest valid value without changing the rule meaning.
    - Example:
        request body -> BODY
        response body -> BODY
        authorization header -> HEADER
    - Do NOT output values such as REQUEST_BODY or RESPONSE_BODY
      unless they exist in the allowed list above.

    LAYERS:
    - sourceLayers represents architectural layers from which
      a relationship originates.
    - targetLayers represents architectural layers targeted
      by a relationship.
    - Populate sourceLayers and targetLayers only when a layer relationship
      is explicitly stated or strongly implied.
    - Do not infer layers merely from generic words such as
      "service", "client", or "server" unless they clearly represent
      architectural layers.

    RELATIONSHIPS:
    - relations represents the architectural relationship expressed by the rule.
    - Use only relation enum values supported by the output type.
    - Never invent relation values.
    - If no supported relation accurately represents the rule,
      return an empty collection.

    TECHNOLOGIES:
    - technologies contains only concrete technologies, frameworks,
      platforms, libraries, infrastructure products, or named technical
      systems explicitly mentioned or strongly implied by the rule.
    - Do not put architectural concepts or generic terms into technologies.
    - Examples of valid technologies may include:
      Spring, Kafka, PostgreSQL, OAuth2, SSO, Eventbus.
    - If no technology applies, return an empty collection.

    REQUIRED CONTEXT:
    - requiredContext must describe only project information actually
      required to evaluate compliance with the rule.
    - Do not request unrelated project information.
    - Use only values supported by the requiredContext output enum/type.
    - Never invent enum values.

    REQUIRED ARTIFACTS:
    - requiredArtifacts must contain only artifacts actually necessary
      to evaluate the rule.
    - Use only values supported by the output type.
    - Never invent enum values.

    APPLICABILITY:
    - Use ALWAYS when the rule applies whenever its scope exists.
    - Use CONDITIONAL only when the source rule explicitly contains
      a condition or prerequisite.
    - If CONDITIONAL, applicabilityConditions must contain only conditions
      explicitly stated or strongly implied by the source.
    - If ALWAYS, applicabilityConditions must be empty.

    CONCERN AND SEMANTIC GROUP:
    - concern represents the main architectural concern of the rule.
    - semanticGroup groups semantically related rules under a stable,
      concise architectural concept.
    - Do not create overly specific semantic groups when a broader
      existing concept accurately represents the rule.

    CONCEPTS:
    - concepts contains short semantic concepts represented by the rule.
    - Concepts are intended for semantic retrieval.
    - Do not simply copy the full rule description.
    - Keep concepts concise and meaningful.

    KEYWORDS:
    - keywords contains the most important terms useful for retrieval
      and rule matching.
    - Prefer terminology appearing directly in the source content.
    - Do not add unrelated synonyms merely to increase keyword count.

    SEMANTIC SUMMARY:
    - semanticSummary must be a short, self-contained semantic
      representation of the rule.
    - It must preserve the requirement and its main subject.
    - It should be suitable for embedding and semantic retrieval.
    - Do not add information that is absent from the rule.

    LOCAL METADATA:
    - Local execution metadata is application-owned.
    - Do NOT generate strategy keys.
    - Do NOT invent deterministic implementation details.
    - localMetadata must be null unless the application explicitly
      provides values independently of this extraction process.

    OUTPUT VALIDATION:
    - Return structured output matching RuleContainer exactly.
    - Every enum field must contain only a value supported by
      the corresponding Java enum.
    - Never create new enum values.
    - Never use free-form strings where the output schema expects an enum.
    - Before returning the result, verify that every generated enum value
      belongs to its allowed enum.
    - If an appropriate enum value does not exist, use the nearest
      semantically correct supported value or leave the optional collection empty.
    - If no architecture rules exist, return an empty rules collection.

    OUTPUT:
    - Return only the structured RuleContainer result.
    - Do not return explanations.
    - Do not return Markdown.
    - Do not return code fences.
    - Do not return tool calls.
    - Do not return additional text outside the structured result.
    """)
    @UserMessage("""
    Extract the complete architecture rule set from the following
    architecture Markdown content.

    Treat the supplied content as the only source of architectural requirements.

    CONTENT:
    {{content}}
    """)
    RuleContainer extract(@V("content") String content);
}