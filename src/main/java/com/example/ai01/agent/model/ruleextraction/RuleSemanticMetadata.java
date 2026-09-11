package com.example.ai01.agent.model.extraction;

import java.util.Set;

public record RuleSemanticMetadata(

        RuleScope scope,

        Set<ProjectElementType> appliesTo,

        Set<RelationType> relations,

        Set<RequiredContext> requiredContext,

        Set<String> concepts,

        String semanticSummary

) {
}
