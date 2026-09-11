package com.example.ai01.agent.model.ruleextraction;

import com.example.ai01.agent.model.RuleType;
import dev.langchain4j.model.output.structured.Description;

@Description("""
        One atomic architecture rule extracted from architecture documents.

        The rule contains:
        - the original architectural constraint,
        - its evaluation type,
        - application-owned local execution metadata,
        - and semantic metadata used for applicability detection,
          semantic retrieval and project-context selection.
        """)
public record ArchitectureRule(

        @Description("""
                Stable unique rule identifier.

                Examples:
                LAYER-001
                DEP-002
                SECURITY-003
                REST-004
                """)
        String id,

        @Description("""
                Short, clear, atomic and actionable architecture rule.

                The description must represent exactly one architectural
                constraint and must not combine independent requirements.
                """)
        String description,

        @Description("""
                Defines how the rule is evaluated.

                Use only one of the supported RuleType enum values.
                """)
        RuleType ruleType,

        @Description("""
                Application-owned metadata used for local deterministic execution.

                This information is NOT semantic information extracted from
                architecture documents.

                The extraction LLM must not invent strategy keys.

                During raw rule extraction this value may be null.
                The application may enrich the rule with local metadata later.
                """)
        RuleLocalMetadata localMetadata,

        @Description("""
                Semantic metadata derived from the meaning of the architecture rule.

                It describes applicability, affected project elements,
                dependencies, technologies, protocols, required context and
                semantic concepts used for retrieval.
                """)
        RuleSemanticMetadata semanticMetadata

) {

        public ArchitectureRule withLocalMetadata(RuleLocalMetadata metadata) {
                return new ArchitectureRule(
                        id,
                        description,
                        ruleType,
                        metadata,
                        semanticMetadata
                );
        }
}