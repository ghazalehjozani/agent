package com.example.ai01.agent.model.ruleextraction;

import dev.langchain4j.model.output.structured.Description;

@Description("""
        Application-owned metadata used for local execution of architecture rules.

        This metadata is not extracted semantically from architecture documents.
        It is assigned by the application after rule extraction.
        """)
public record RuleLocalMetadata(
        @Description("""
                Key used by the application to resolve a deterministic
                validation strategy from its strategy registry.

                Example:
                LayerDependencyStrategy
                NamingConventionStrategy
                SecurityStandard

                The extraction LLM must never invent or infer this value.

                It may be null when:
                - the rule is semantic,
                - no deterministic implementation exists,
                - or the strategy has not yet been assigned.
                """)
        String strategyKey
) {

    public static RuleLocalMetadata empty() {
        return new RuleLocalMetadata(null);
    }

    public boolean hasStrategy() {
        return strategyKey != null && !strategyKey.isBlank();
    }
}