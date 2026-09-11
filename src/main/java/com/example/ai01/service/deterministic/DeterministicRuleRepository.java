package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;

import java.util.List;

/** Executes one family of application-owned deterministic rules. */
public interface DeterministicRuleRepository {

    String strategyKey();

    boolean supports(String ruleCode);

    List<ViolationFinding> evaluate(ArchitectureRule rule, PackageNode project);
}
