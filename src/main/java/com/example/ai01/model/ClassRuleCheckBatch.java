package com.example.ai01.model;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;

import java.util.List;

public record ClassRuleCheckBatch(
        List<ArchitectureRule> rules,
        List<ProjectClassContext> classContexts) {

    public ClassRuleCheckBatch {
        rules = rules == null ? List.of() : List.copyOf(rules);
        classContexts = classContexts == null
                ? List.of()
                : List.copyOf(classContexts);
    }
}
