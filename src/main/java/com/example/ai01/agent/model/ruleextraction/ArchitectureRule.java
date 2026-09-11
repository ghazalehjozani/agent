package com.example.ai01.agent.model;

import dev.langchain4j.model.output.structured.Description;

@Description("One atomic architecture rule extracted from the architecture Markdown files")
public record ArchitectureRule(
        @Description("Stable rule code, for example LAYER-001 or DEP-002")
        String id,
        @Description("Short, clear and actionable architecture rule description")
        String description,
        @Description("Architecture rule type; use one of the RuleCategory enum values")
        RuleType ruleType,
        String strategyName

) {
}