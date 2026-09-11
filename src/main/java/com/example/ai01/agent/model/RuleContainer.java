package com.example.ai01.agent.model;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;
@Description("Complete set of architecture rules extracted from all supplied Markdown files")
public record RuleContainer(
        @Description("All extracted architecture rules; empty when no rules are found")
        List<ArchitectureRule> rules
) {
    public RuleContainer {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}