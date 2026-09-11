package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.monitoring.TraceOperation;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service("deterministicRuleStrategyRegistry")
public class RuleStrategyRegistry {
    private final Map<String, DeterministicRuleRepository> repositories;

    public RuleStrategyRegistry(List<DeterministicRuleRepository> repositories) {
        Map<String, DeterministicRuleRepository> indexed = new LinkedHashMap<>();
        for (DeterministicRuleRepository repository : repositories) {
            DeterministicRuleRepository previous = indexed.put(repository.strategyKey(), repository);
            if (previous != null) {
                throw new IllegalStateException("Duplicate strategy key: " + repository.strategyKey());
            }
        }
        this.repositories = Map.copyOf(indexed);
    }

    @TraceOperation(serviceName = "rule-strategy-registry", spanName = "rules.deterministic", newSpan = true)
    public List<ViolationFinding> evaluate(ArchitectureRule rule, PackageNode project) {
        if (!supports(rule)) {
            throw new IllegalArgumentException(
                    "Rule is not registered for deterministic evaluation: "
                            + (rule == null ? "null" : rule.id())
            );
        }

        String strategyKey = rule.localMetadata().strategyKey();
        return repositories.get(strategyKey).evaluate(rule, project);
    }

    public boolean supports(ArchitectureRule rule) {
        if (rule == null || rule.ruleType() != RuleType.DETERMINISTIC) {
            return false;
        }
        if (rule.localMetadata() == null) {
            return false;
        }

        String strategyKey = rule.localMetadata().strategyKey();
        if (strategyKey == null || strategyKey.isBlank()) {
            return false;
        }

        DeterministicRuleRepository repository = repositories.get(strategyKey);
        return repository != null && repository.supports(rule.id());
    }
}
