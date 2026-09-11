package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.RuleLocalMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleStrategyRegistryTest {
    @Test
    void dispatchesByConfiguredStrategyKey() {
        DeterministicRuleRepository repository = new StubRepository();
        RuleStrategyRegistry registry = new RuleStrategyRegistry(List.of(repository));
        ArchitectureRule rule = new ArchitectureRule(
                "12001LAYER001", "layer rule", RuleType.DETERMINISTIC,
                new RuleLocalMetadata("LAYER"), null);

        assertThat(registry.evaluate(rule, new PackageNode("", "", List.of(), List.of())))
                .hasSize(1);
    }

    private static final class StubRepository implements DeterministicRuleRepository {
        public String strategyKey() { return "LAYER"; }
        public boolean supports(String ruleCode) { return RuleCode.belongsTo(ruleCode, "LAYER"); }
        public List<ViolationFinding> evaluate(ArchitectureRule rule, PackageNode project) {
            return List.of(new ViolationFinding(rule.id(), "layer", rule.id(), "MAJOR",
                    null, null, "evidence", "recommendation", 1.0));
        }
    }
}
