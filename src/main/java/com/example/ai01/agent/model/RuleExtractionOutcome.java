package com.example.ai01.agent.model;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;

import java.util.List;

public record RuleExtractionOutcome(Boolean isValid, Long version, List<ArchitectureRule> rules) {
}
